package com.example.xiaonao.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

/**
 * Room 数据库（单表 todos）。
 * 用伴生对象做单例，避免重复创建。
 *
 * 版本演进：
 *  - v1：id / title / done / createdAt
 *  - v1.1 起实体加了 dueDate / category，但当时漏改数据库版本号（历史坑）
 *  - v1.3 加了 pinned
 *  - v1.6 加了 repeat（周期性待办）
 *  - v1.7 加了 note（备注）
 * 为避免老库与新 schema 对不上导致一打开就崩溃，这里把迁移做「健壮补全」：
 * 用 PRAGMA 检测列是否存在，缺哪列补哪列；并加 fallback 兜底（极端情况清数据也不闪退）。
 */
@Database(entities = [Todo::class], version = 5, exportSchema = false)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        /** v1 -> v2：补齐 v1.1 起新增的「到期 / 分类」两列（兼容老 v1.0 库） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "todos", "dueDate", "INTEGER")
                addColumnIfNotExists(db, "todos", "category", "TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        /** v2 -> v3：新增「置顶」列 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "todos", "pinned", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 -> v4：新增「重复周期」列（每天 / 每周 / 每月） */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "todos", "repeat", "TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        /** v4 -> v5：新增「备注」列 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "todos", "note", "TEXT NOT NULL DEFAULT ''")
            }
        }

        /** 列不存在才 ALTER 添加，避免「column already exists」把迁移搞崩 */
        private fun addColumnIfNotExists(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            definition: String
        ) {
            db.query("PRAGMA table_info($table)").use { cursor ->
                val nameIdx = cursor.getColumnIndex("name")
                var exists = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIdx) == column) { exists = true; break }
                }
                if (!exists) {
                    db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
                }
            }
        }

        @Volatile
        private var INSTANCE: TodoDatabase? = null

        fun get(context: Context): TodoDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TodoDatabase::class.java,
                    "xiaonao_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration() // 任何迁移异常都清数据继续，绝不闪退
                    .build().also { INSTANCE = it }
            }
    }
}
