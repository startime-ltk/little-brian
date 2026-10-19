package com.example.xiaonao.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Room 迁移测试（Robolectric 在 JVM 上跑，CI 无需模拟器）。
 *
 * 背景：v1.21 曾因 MIGRATION_5_6 漏写 DEFAULT NULL，导致老用户升级后
 * Room 2.6+ 校验列 defaultValue 失败、打开即崩。本测试把这类事故固化成断言：
 *
 *  1. v5→v6：迁移后 deletedAt 列必须存在且默认值为 NULL，老数据无损
 *  2. v6 毒库→v7：模拟 v1.21 毒库（deletedAt 无 DEFAULT NULL），
 *     用当前代码打开必须不崩（v6→v7 修复迁移兜底），数据无损、id 不撞号
 *  3. v1→v7 全链路：最老版本的用户一路升级，数据必须无损
 *
 * 注意：测试里打开数据库时【故意不加】 fallbackToDestructiveMigration——
 * 迁移失败必须抛异常让测试变红，而不是悄悄清数据蒙混过关。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], manifest = Config.NONE)
class MigrationTest {

    private lateinit var context: Context

    private val helper = MigrationTestHelper(
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        TodoDatabase::class.java
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    /** 用当前代码打开测试库：触发迁移 + Room schema 校验（打开即崩事故的第一现场） */
    private fun openMigratedDb(): TodoDatabase =
        Room.databaseBuilder(context, TodoDatabase::class.java, TEST_DB)
            .addMigrations(*TodoDatabase.migrations())
            .build()

    /** v1 远古 schema：只有 4 列 */
    private fun createV1Db() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `todos` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`done` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL)"
            )
            db.execSQL("INSERT INTO todos (title, done, createdAt) VALUES ('v1远古数据', 1, 1000)")
        }
    }

    /** v5 schema：无 deletedAt 列 */
    private fun createV5Db() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `todos` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`done` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`dueDate` INTEGER, " +
                    "`category` TEXT NOT NULL, " +
                    "`pinned` INTEGER NOT NULL DEFAULT 0, " +
                    "`repeat` TEXT NOT NULL DEFAULT 'NONE', " +
                    "`note` TEXT NOT NULL DEFAULT '')"
            )
            db.execSQL("INSERT INTO todos (title, done, createdAt) VALUES ('v5时代数据', 0, 1000)")
        }
    }

    /** v6 毒库 schema：deletedAt 列存在但【没有 DEFAULT NULL】——v1.21 事故库 */
    private fun createV6PoisonDb() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `todos` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`done` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`dueDate` INTEGER, " +
                    "`category` TEXT NOT NULL, " +
                    "`pinned` INTEGER NOT NULL DEFAULT 0, " +
                    "`repeat` TEXT NOT NULL DEFAULT 'NONE', " +
                    "`note` TEXT NOT NULL DEFAULT '', " +
                    "`deletedAt` INTEGER)"
            )
            db.execSQL("INSERT INTO todos (title, done, createdAt) VALUES ('毒库老数据1', 0, 1000)")
            db.execSQL("INSERT INTO todos (title, done, createdAt) VALUES ('毒库老数据2', 1, 2000)")
        }
    }

    @Test
    fun v5到v6_补齐deletedAt列_默认NULL_数据无损() {
        createV5Db()
        val db = openMigratedDb()

        // 老数据无损
        db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM todos")).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        // deletedAt 列存在，且默认值必须是 'NULL'（v1.21 漏的就是这个）
        db.query(SimpleSQLiteQuery("PRAGMA table_info(todos)")).use { c ->
            val nameIdx = c.getColumnIndex("name")
            val dfltIdx = c.getColumnIndex("dflt_value")
            var found = false
            while (c.moveToNext()) {
                if (c.getString(nameIdx) == "deletedAt") {
                    found = true
                    assertEquals("deletedAt 默认值必须是 NULL", "NULL", c.getString(dfltIdx))
                }
            }
            assertTrue("deletedAt 列应存在", found)
        }
        db.close()
    }

    @Test
    fun v6毒库_当前代码打开不崩_数据无损_序列不撞号() {
        createV6PoisonDb()
        // 能打开且查询不抛异常 = 不崩（v6→v7 修复迁移生效）
        val db = openMigratedDb()

        // 毒库老数据无损
        db.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM todos")).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        // 修复迁移重建了 sqlite_sequence：seq 必须等于当前最大 id（2），新行才不会撞号
        db.query(SimpleSQLiteQuery("SELECT seq FROM sqlite_sequence WHERE name = 'todos'")).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun v1到v7全链路迁移_老数据无损() {
        createV1Db()
        val db = openMigratedDb()

        db.query(SimpleSQLiteQuery("SELECT title, done FROM todos")).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("v1远古数据", c.getString(0))
            assertEquals(1, c.getInt(1))
        }
        db.close()
    }

    companion object {
        private const val TEST_DB = "migration_test.db"
    }
}
