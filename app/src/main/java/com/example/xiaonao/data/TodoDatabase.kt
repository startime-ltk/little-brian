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
 *  - v1.21 加了 deletedAt（回收站：软删除，非 null 即已进回收站）
 *  - v1.24 修复迁移：v1.21 曾短暂上线的版本漏写 DEFAULT NULL，Room 2.6+ 校验 defaultValue
 *    会导致「打开即崩」；v6→v7 重建 todos 表补齐该默认值（老数据无损）
 * 为避免老库与新 schema 对不上导致一打开就崩溃，这里把迁移做「健壮补全」：
 * 用 PRAGMA 检测列是否存在，缺哪列补哪列；并加 fallback 兜底（极端情况清数据也不闪退）。
 */
@Database(entities = [Todo::class], version = 7, exportSchema = false)
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

        /** v5 -> v6：新增「回收站」列（软删除时间戳，NULL 表示未删除）。
         *  注意必须带 DEFAULT NULL：Room 2.6+ 会校验列的 defaultValue，
         *  与实体 @ColumnInfo(defaultValue = "NULL") 对不上就会判迁移失败、打开即崩。 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfNotExists(db, "todos", "deletedAt", "INTEGER DEFAULT NULL")
            }
        }

        /**
         * v6 -> v7：修复迁移。
         * v1.21 短暂上线的版本里 MIGRATION_5_6 漏写了 DEFAULT NULL，导致迁移后的库
         * 与实体 schema 对不上（Room 2.6+ 校验列的 defaultValue），表现为「打开即崩」。
         * 由于库版本号已经是 6，后续版本不会再跑 5→6 迁移，只能重建表补齐默认值。
         * 做法：建新表（DDL 与 Room 生成的一致）→ 拷数据 → 删旧表 → 改名 → 修自增序列。
         * 已用 sqlite3 完整演练：数据无损、deletedAt 默认值修复、自增 id 不撞号。
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `todos_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`done` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`dueDate` INTEGER, " +
                        "`category` TEXT NOT NULL, " +
                        "`pinned` INTEGER NOT NULL DEFAULT 0, " +
                        "`repeat` TEXT NOT NULL DEFAULT 'NONE', " +
                        "`note` TEXT NOT NULL DEFAULT '', " +
                        "`deletedAt` INTEGER DEFAULT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `todos_new` " +
                        "(`id`, `title`, `done`, `createdAt`, `dueDate`, `category`, `pinned`, `repeat`, `note`, `deletedAt`) " +
                        "SELECT `id`, `title`, `done`, `createdAt`, `dueDate`, `category`, `pinned`, `repeat`, `note`, `deletedAt` FROM `todos`"
                )
                db.execSQL("DROP TABLE `todos`")
                db.execSQL("ALTER TABLE `todos_new` RENAME TO `todos`")
                // 重建后自增序列条目会丢，按当前最大 id 重设，防止新行 id 撞号
                db.execSQL("DELETE FROM sqlite_sequence WHERE name = 'todos'")
                db.execSQL("INSERT INTO sqlite_sequence (name, seq) SELECT 'todos', IFNULL(MAX(id), 0) FROM todos")
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

        /** 暴露全部迁移给测试使用（MigrationTest 在 JVM 上验证迁移正确性） */
        internal fun migrations(): Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        @Volatile
        private var INSTANCE: TodoDatabase? = null

        fun get(context: Context): TodoDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TodoDatabase::class.java,
                    "xiaonao_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration() // 任何迁移异常都清数据继续，绝不闪退
                    .build().also { INSTANCE = it }
            }
    }
}
