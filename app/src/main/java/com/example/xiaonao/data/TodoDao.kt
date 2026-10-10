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
 */
@Dao
interface TodoDao {

    @Query("SELECT * FROM todos ORDER BY pinned DESC, done ASC, createdAt DESC")
    fun observeAll(): Flow<List<Todo>>

    @Query("SELECT * FROM todos ORDER BY pinned DESC, done ASC, createdAt DESC")
    suspend fun getAll(): List<Todo>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun getById(id: Long): Todo?

    /** 只返回未完成的事项，用于桌面小组件（让桌面始终聚焦「待办」） */
    @Query("SELECT * FROM todos WHERE done = 0 ORDER BY pinned DESC, createdAt DESC")
    fun observeActive(): Flow<List<Todo>>

    /** 一次性取出未完成列表（给小组件工厂用） */
    @Query("SELECT * FROM todos WHERE done = 0 ORDER BY pinned DESC, createdAt DESC")
    suspend fun getActive(): List<Todo>

    /** 删除所有已完成的事项 */
    @Query("DELETE FROM todos WHERE done = 1")
    suspend fun deleteDone(): Int

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
