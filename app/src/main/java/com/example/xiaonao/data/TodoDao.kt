package com.example.xiaonao.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 数据访问接口（Room 自动生成实现）。
 * observeAll 返回 Flow，数据变化会自动通知界面/小组件刷新。
 *
 * 回收站（软删除）：deletedAt 非 NULL 即已进回收站。
 * 主列表 / 小组件 / 导出一律过滤掉回收站内容；回收站内容只能「恢复」或「彻底删除」。
 */
@Dao
interface TodoDao {

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL ORDER BY pinned DESC, done ASC, createdAt DESC")
    fun observeAll(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL ORDER BY pinned DESC, done ASC, createdAt DESC")
    suspend fun getAll(): List<Todo>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: Long): Todo?

    /** 只返回未完成的事项，用于桌面小组件（让桌面始终聚焦「待办」） */
    @Query("SELECT * FROM todos WHERE done = 0 AND deletedAt IS NULL ORDER BY pinned DESC, createdAt DESC")
    fun observeActive(): Flow<List<Todo>>

    /** 一次性取出未完成列表（给小组件工厂用） */
    @Query("SELECT * FROM todos WHERE done = 0 AND deletedAt IS NULL ORDER BY pinned DESC, createdAt DESC")
    suspend fun getActive(): List<Todo>

    /** 删除所有已完成的事项（不影响回收站） */
    @Query("DELETE FROM todos WHERE done = 1 AND deletedAt IS NULL")
    suspend fun deleteDone(): Int

    // ---------- 回收站 ----------

    /** 回收站列表（按删除时间倒序，最近删的在前） */
    @Query("SELECT * FROM todos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    suspend fun getTrash(): List<Todo>

    /** 软删除：打上删除时间戳进回收站（不物理删行，可恢复） */
    @Query("UPDATE todos SET deletedAt = :ts WHERE id = :id")
    suspend fun moveToTrash(id: Long, ts: Long)

    /** 从回收站恢复：清掉删除时间戳 */
    @Query("UPDATE todos SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    /** 彻底删除一条（物理删除） */
    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun hardDelete(id: Long)

    /** 清空回收站 */
    @Query("DELETE FROM todos WHERE deletedAt IS NOT NULL")
    suspend fun clearTrash()

    /** 自动清理：删掉删除时间早于 before 的回收站内容，返回清理条数 */
    @Query("DELETE FROM todos WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun cleanTrashBefore(before: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: Todo)

    @Update
    suspend fun update(todo: Todo)

    @Delete
    suspend fun delete(todo: Todo)

    @Query("UPDATE todos SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("UPDATE todos SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)
}
