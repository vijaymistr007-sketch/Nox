package com.nox.feature.agent.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 记忆知识库条目：从会话里自动抽取的可复用事实。
 *
 * 与 `domain/memory` 的 Markdown 记忆是两层东西——那边是用户/模型显式维护的长期指令，
 * 这边是会话过程中沉淀下来的零散结论，靠 [contentHash] 与相似度去重，靠关键词打分召回。
 *
 * @param contentHash 归一化正文的 SHA-256，唯一索引，同一事实重复抽取只留一条。
 * @param projectRoot 归属工作区；null 表示全局（跨工作区可见）。
 * @param sourceMessageIds 来源消息 id，逗号分隔，用于回溯「这条结论是哪轮聊出来的」。
 * @param hitCount 被召回次数，用于排序与淘汰。
 */
@Entity(
    tableName = "knowledge_entries",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["projectRoot"]),
        Index(value = ["updatedAt"])
    ]
)
data class KnowledgeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    /** 逗号分隔的关键词，召回时参与打分。 */
    val tags: String = "",
    val projectRoot: String? = null,
    val sessionId: String? = null,
    val sourceMessageIds: String = "",
    val contentHash: String,
    val createdAt: Long,
    val updatedAt: Long,
    val hitCount: Int = 0,
    val lastRecalledAt: Long? = null,
    /** 置顶条目在召回时优先，且不参与自动淘汰。 */
    val pinned: Boolean = false
)
