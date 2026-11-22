package com.nox.feature.agent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nox.feature.agent.data.local.entity.KnowledgeEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeEntryDao {

    /** 同 hash 已存在时静默跳过：去重的第一道闸在 SQL 唯一索引上。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entry: KnowledgeEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: KnowledgeEntryEntity): Long

    @Query("SELECT * FROM knowledge_entries ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<KnowledgeEntryEntity>>

    /** 工作区过滤：全局条目（projectRoot IS NULL）始终可见。 */
    @Query(
        """
        SELECT * FROM knowledge_entries
        WHERE (:projectRoot IS NULL OR projectRoot IS NULL OR projectRoot = :projectRoot)
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun observeForProject(projectRoot: String?): Flow<List<KnowledgeEntryEntity>>

    @Query(
        """
        SELECT * FROM knowledge_entries
        WHERE (:projectRoot IS NULL OR projectRoot IS NULL OR projectRoot = :projectRoot)
          AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%')
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun search(query: String, projectRoot: String?): Flow<List<KnowledgeEntryEntity>>

    /** 召回候选集：全局 + 当前工作区，按最近更新取一批，打分在内存里做。 */
    @Query(
        """
        SELECT * FROM knowledge_entries
        WHERE (:projectRoot IS NULL OR projectRoot IS NULL OR projectRoot = :projectRoot)
        ORDER BY pinned DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun candidates(projectRoot: String?, limit: Int): List<KnowledgeEntryEntity>

    @Query("SELECT * FROM knowledge_entries WHERE contentHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): KnowledgeEntryEntity?

    @Query("SELECT * FROM knowledge_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): KnowledgeEntryEntity?

    @Query("DELETE FROM knowledge_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM knowledge_entries")
    suspend fun deleteAll()

    @Query("UPDATE knowledge_entries SET hitCount = hitCount + 1, lastRecalledAt = :now WHERE id IN (:ids)")
    suspend fun markRecalled(ids: List<Long>, now: Long)

    @Query("UPDATE knowledge_entries SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long)

    @Query("SELECT COUNT(*) FROM knowledge_entries")
    suspend fun count(): Int
}
