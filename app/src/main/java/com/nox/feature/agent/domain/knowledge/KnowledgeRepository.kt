package com.nox.feature.agent.domain.knowledge

import com.nox.feature.agent.data.local.dao.KnowledgeEntryDao
import com.nox.feature.agent.data.local.entity.KnowledgeEntryEntity
import com.nox.feature.agent.domain.model.AgentMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** 知识库条目（领域模型，与 Room 实体解耦，UI 只认这个）。 */
data class KnowledgeEntry(
    val id: Long,
    val title: String,
    val content: String,
    val tags: List<String>,
    val projectRoot: String?,
    val sessionId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val hitCount: Int,
    val pinned: Boolean
)

/**
 * 记忆知识库门面：抽取、去重、召回、增删改查。
 *
 * 去重两道闸：SQL 唯一索引挡完全相同的正文；相似度合并挡「换个说法讲同一件事」。
 * 召回是关键词加权打分，不做向量——本地跑 embedding 成本与复杂度都不划算，
 * 而知识条目普遍短、术语密集，词面匹配的召回率已经够用。
 */
@Singleton
class KnowledgeRepository @Inject constructor(
    private val dao: KnowledgeEntryDao
) {

    companion object {
        /** 相似度合并阈值：低于它视为不同事实。 */
        private const val SIMILARITY_MERGE = 0.72

        /** 召回候选池上限：按最近更新取这么多条再打分，避免全表扫描。 */
        private const val CANDIDATE_LIMIT = 300

        /** 召回条数上限。 */
        private const val RECALL_LIMIT = 5

        /** 召回块总字数上限，防止把上下文吃满。 */
        private const val RECALL_BLOCK_MAX_CHARS = 1200

        /** 单条召回最低分，低于它视为不相关。 */
        private const val RECALL_MIN_SCORE = 1.0
    }

    fun observeAll(): Flow<List<KnowledgeEntry>> = dao.observeAll().mapToDomain()

    fun observeForProject(projectRoot: String?): Flow<List<KnowledgeEntry>> =
        dao.observeForProject(projectRoot).mapToDomain()

    fun search(query: String, projectRoot: String?): Flow<List<KnowledgeEntry>> =
        dao.search(query, projectRoot).mapToDomain()

    suspend fun count(): Int = dao.count()

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun setPinned(id: Long, pinned: Boolean) =
        dao.setPinned(id, pinned, System.currentTimeMillis())

    /** 手动新增/编辑：编辑时重算指纹，撞唯一索引则覆盖原条目。 */
    suspend fun save(
        id: Long?,
        title: String,
        content: String,
        tags: String,
        projectRoot: String?,
        sessionId: String? = null
    ): Boolean {
        val now = System.currentTimeMillis()
        val hash = KnowledgeText.hash(content)
        val existing = dao.findByHash(hash)
        val entity = KnowledgeEntryEntity(
            id = id ?: existing?.id ?: 0,
            title = title.ifBlank { KnowledgeText.titleOf(content) },
            content = content,
            tags = tags,
            projectRoot = projectRoot,
            sessionId = sessionId,
            sourceMessageIds = existing?.sourceMessageIds.orEmpty(),
            contentHash = hash,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            hitCount = existing?.hitCount ?: 0,
            lastRecalledAt = existing?.lastRecalledAt,
            pinned = existing?.pinned ?: false
        )
        return runCatching { dao.upsert(entity) }.isSuccess
    }

    /**
     * 从会话消息里抽取条目并落库，返回新增条数。
     *
     * 相似度合并只在同一 projectRoot 的候选里做：跨工作区的「同一句话」很可能是不同语境，
     * 合并会丢信息。
     */
    suspend fun extractAndStore(
        sessionId: String?,
        projectRoot: String?,
        messages: List<AgentMessage>
    ): Int {
        val candidates = KnowledgeExtractor.extract(messages)
        if (candidates.isEmpty()) return 0

        val existing = dao.candidates(projectRoot, CANDIDATE_LIMIT)
        val existingTokens = existing.map { it to KnowledgeText.tokenize(it.title + " " + it.content) }
        val now = System.currentTimeMillis()
        var inserted = 0

        for (candidate in candidates) {
            val hash = KnowledgeText.hash(candidate.content)
            if (dao.findByHash(hash) != null) continue

            val tokens = KnowledgeText.tokenize(candidate.title + " " + candidate.content)
            val similar = existingTokens.firstOrNull { (_, other) ->
                KnowledgeText.jaccard(tokens, other) >= SIMILARITY_MERGE
            }
            if (similar != null) {
                // 同一事实换个说法：保留信息量更大的那份，刷新时间戳，不新增条目。
                val (hit, _) = similar
                if (candidate.content.length > hit.content.length) {
                    dao.upsert(
                        hit.copy(
                            title = candidate.title,
                            content = candidate.content,
                            tags = mergeTags(hit.tags, candidate.tags),
                            contentHash = hash,
                            updatedAt = now
                        )
                    )
                } else {
                    dao.upsert(hit.copy(updatedAt = now))
                }
                continue
            }

            val rowId = dao.insertIgnore(
                KnowledgeEntryEntity(
                    title = candidate.title,
                    content = candidate.content,
                    tags = candidate.tags,
                    projectRoot = projectRoot,
                    sessionId = sessionId,
                    sourceMessageIds = candidate.sourceMessageId,
                    contentHash = hash,
                    createdAt = now,
                    updatedAt = now
                )
            )
            if (rowId > 0) inserted++
        }
        return inserted
    }

    /** 按关键词加权打分召回；返回按分数降序的条目。 */
    suspend fun recall(query: String, projectRoot: String?, limit: Int = RECALL_LIMIT): List<KnowledgeEntry> {
        val queryTokens = KnowledgeText.tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val candidates = dao.candidates(projectRoot, CANDIDATE_LIMIT)
        if (candidates.isEmpty()) return emptyList()

        val scored = candidates.mapNotNull { entity ->
            val titleTokens = KnowledgeText.tokenize(entity.title)
            val tagTokens = KnowledgeText.tokenize(entity.tags)
            val contentTokens = KnowledgeText.tokenize(entity.content)
            var score = 0.0
            for (token in queryTokens) {
                score += when {
                    token in titleTokens -> 2.0
                    token in tagTokens -> 1.5
                    token in contentTokens -> 1.0
                    else -> 0.0
                }
            }
            if (score <= 0.0) return@mapNotNull null
            // 长条目天然命中更多词，用词数开方做长度惩罚，避免长文霸榜。
            val normalized = score / kotlin.math.sqrt((contentTokens.size + 1).toDouble())
            if (normalized < RECALL_MIN_SCORE) null else entity to normalized
        }.sortedByDescending { it.second }
            .take(limit)

        if (scored.isEmpty()) return emptyList()
        dao.markRecalled(scored.map { it.first.id }, System.currentTimeMillis())
        return scored.map { it.first.toDomain() }
    }

    /**
     * 组装注入到本轮请求的召回块；无命中返回 null。
     *
     * 输出的是「参考材料」而不是指令，措辞上明确让模型自行判断是否适用，
     * 避免历史结论覆盖当前用户的新要求。
     */
    suspend fun buildRecallBlock(query: String, projectRoot: String?): String? {
        val hits = recall(query, projectRoot)
        if (hits.isEmpty()) return null
        val builder = StringBuilder()
        builder.append("【记忆知识库·自动召回】以下是本工作区历史会话中沉淀的相关结论，仅供参考；")
        builder.append("与当前要求冲突时以当前要求为准。\n")
        for (hit in hits) {
            val line = "- ${hit.title}：${hit.content.replace("\n", " ").take(200)}\n"
            if (builder.length + line.length > RECALL_BLOCK_MAX_CHARS) break
            builder.append(line)
        }
        return builder.toString().trimEnd()
    }

    private fun mergeTags(a: String, b: String): String =
        (a.split(',') + b.split(','))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")

    private fun KnowledgeEntryEntity.toDomain() = KnowledgeEntry(
        id = id,
        title = title,
        content = content,
        tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        projectRoot = projectRoot,
        sessionId = sessionId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        hitCount = hitCount,
        pinned = pinned
    )

    private fun Flow<List<KnowledgeEntryEntity>>.mapToDomain(): Flow<List<KnowledgeEntry>> =
        map { list -> list.map { it.toDomain() } }
}
