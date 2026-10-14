package com.example.xiaonao.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条待办事项。
 * id 自增；done 表示是否已完成；createdAt 用于排序；
 * dueDate 到期时间（可空，无则不过期提醒）；category 分类（存 Category 的 name）；
 * pinned 置顶（true 排在最前）；repeat 重复周期（存 RepeatRule 的 name，NONE 表示不重复）；
 * note 备注（可空的补充说明，空串表示没写）；
 * deletedAt 回收站：null 表示正常待办；非 null 表示已删除及删除时间（软删除，可恢复）。
 */
@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val category: String = Category.NONE.name,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "'NONE'") val repeat: String = RepeatRule.NONE.name,
    @ColumnInfo(defaultValue = "''") val note: String = "",
    @ColumnInfo(defaultValue = "NULL") val deletedAt: Long? = null
)
