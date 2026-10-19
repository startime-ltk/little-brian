package com.example.xiaonao

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.xiaonao.R
import com.example.xiaonao.data.Category
import com.example.xiaonao.data.RepeatRule
import com.example.xiaonao.data.Todo
import com.example.xiaonao.data.TodoDao
import com.example.xiaonao.data.TodoDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 数据仓库：界面向它要数据，它背后用 Room。
 * 纯本地，不联网、不依赖任何后端。
 * 同时负责在添加 / 勾选 / 删除时安排或取消到期提醒闹钟。
 */
class TodoRepository(private val context: Context, private val dao: TodoDao) {

    val todos: Flow<List<Todo>> = dao.observeAll()

    /** 回收站内容（软删除的待办，按删除时间倒序） */
    val trash: Flow<List<Todo>> = dao.observeTrash()

    suspend fun add(
        title: String,
        dueDate: Long? = null,
        category: String = Category.NONE.name,
        repeat: String = RepeatRule.NONE.name,
        note: String = ""
    ) {
        if (title.isBlank()) return
        val todo = Todo(
            title = title.trim(),
            dueDate = dueDate,
            category = category,
            repeat = repeat,
            note = note
        )
        dao.insert(todo)
        AlarmScheduler.schedule(context, todo)
    }

    /**
     * 勾选 / 取消勾选一条待办。
     *
     * @return 若这是一条周期性待办且刚被勾「完成」，返回顺延后的新到期时间（界面可据此提示）；
     *         其余情况返回 null。
     *
     * 周期性待办的特殊处理：勾「完成」时不标记 done，而是把到期时间推进到下一个周期、
     * 保持未完成并重排提醒——这样列表里始终只有一条，不会每天堆出历史已完成项。
     */
    suspend fun toggle(id: Long): Long? {
        val todo = dao.getById(id) ?: return null
        val newDone = !todo.done
        val rule = RepeatRule.from(todo.repeat)

        if (newDone && rule != RepeatRule.NONE) {
            val next = RepeatRule.nextDue(todo.dueDate ?: System.currentTimeMillis(), rule)
            dao.update(todo.copy(done = false, dueDate = next))
            AlarmScheduler.cancel(context, id)
            AlarmScheduler.schedule(context, todo.copy(done = false, dueDate = next))
            return next
        }

        dao.setDone(id, newDone)
        // 勾选完成 -> 取消提醒；重新打开 -> 若仍未来到期则再排
        if (newDone) AlarmScheduler.cancel(context, id)
        else AlarmScheduler.schedule(context, todo.copy(done = false))
        return null
    }

    /**
     * 删除一条待办 —— 软删除：打上删除时间戳移入回收站（不物理删行），
     * 之后可在回收站里恢复；同时取消它的到期提醒。
     */
    suspend fun delete(id: Long) {
        val todo = dao.getById(id) ?: return
        AlarmScheduler.cancel(context, id)
        dao.moveToTrash(id, System.currentTimeMillis())
    }

    suspend fun getById(id: Long): Todo? = dao.getById(id)

    /** 取未完成列表（给小组件用） */
    suspend fun getActive(): List<Todo> = dao.getActive()

    /** 设备重启 / App 启动后，重新安排所有「未完成且尚未到期」的提醒 */
    suspend fun rescheduleAll() {
        val now = System.currentTimeMillis()
        dao.getAll()
            .filter { !it.done && it.dueDate != null && it.dueDate > now }
            .forEach { AlarmScheduler.schedule(context, it) }
    }

    /** 清空所有已完成的事项 */
    suspend fun clearDone() {
        dao.deleteDone()
    }

    /** 修改一条已存在的待办（标题 / 分类 / 到期都可改） */
    suspend fun update(todo: Todo) {
        dao.update(todo)
        // 到期/完成状态可能变了，先取消旧闹钟再按需重排
        AlarmScheduler.cancel(context, todo.id)
        if (!todo.done) AlarmScheduler.schedule(context, todo.copy(done = false))
    }

    /** 撤销删除（底部提示的「撤销」）：清掉删除时间戳回到主列表，并重新排提醒 */
    suspend fun restore(todo: Todo) {
        dao.insert(todo.copy(deletedAt = null))
        if (!todo.done) AlarmScheduler.schedule(context, todo.copy(done = false))
    }

    // ---------- 回收站 ----------

    /** 从回收站恢复一条待办（回到主列表，重新排提醒） */
    suspend fun restoreFromTrash(id: Long) {
        val todo = dao.getById(id) ?: return
        dao.restoreFromTrash(id)
        if (!todo.done) AlarmScheduler.schedule(context, todo.copy(done = false))
    }

    /** 彻底删除一条（物理删除，不可恢复） */
    suspend fun deleteForever(id: Long) {
        AlarmScheduler.cancel(context, id)
        dao.hardDelete(id)
    }

    /** 清空回收站 */
    suspend fun clearTrash() {
        dao.clearTrash()
    }

    /** 自动清理：回收站里超过 days 天的内容物理删除（默认 30 天），返回清理条数 */
    suspend fun cleanOldTrash(days: Int = 30): Int {
        val before = System.currentTimeMillis() - days * 24L * 3600 * 1000
        return dao.cleanTrashBefore(before)
    }

    /** 置顶 / 取消置顶 */
    suspend fun setPinned(id: Long, pinned: Boolean) {
        dao.setPinned(id, pinned)
    }

    /**
     * 批量导入：每行一条，空行跳过；可按统一分类 / 统一周期入库。
     * 用于「从备忘录 / 微信 / 别处复制一堆文字，粘贴进来批量建待办」。
     */
    suspend fun batchAdd(
        lines: List<String>,
        category: String = Category.NONE.name,
        repeat: String = RepeatRule.NONE.name
    ) {
        lines.map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { title ->
                val todo = Todo(title = title, category = category, repeat = repeat)
                dao.insert(todo)
                AlarmScheduler.schedule(context, todo)
            }
    }

    /**
     * 导出可读文本清单（区别于 JSON 备份）：便于直接查看 / 分享到微信 / 迁移到别的 App。
     * 返回生成的 .txt 文件；失败返回 null。
     */
    suspend fun exportToText(ctx: Context): File? {
        return try {
            val all = dao.getAll()
            val active = all.filter { !it.done }
            val done = all.filter { it.done }
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(System.currentTimeMillis())
            val sb = StringBuilder()
            sb.appendLine(ctx.getString(R.string.export_title))
            sb.appendLine(ctx.getString(R.string.export_time, ts))
            sb.appendLine(ctx.getString(R.string.export_summary, all.size, active.size, done.size))
            sb.appendLine()
            sb.appendLine(ctx.getString(R.string.export_active_header))
            if (active.isEmpty()) sb.appendLine(ctx.getString(R.string.export_none)) else active.forEach { sb.appendLine(formatLine(it)) }
            sb.appendLine()
            sb.appendLine(ctx.getString(R.string.export_done_header))
            if (done.isEmpty()) sb.appendLine(ctx.getString(R.string.export_none)) else done.forEach { sb.appendLine(formatLine(it)) }

            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: ctx.getExternalFilesDir(null) ?: return null
            val safe = ts.replace(" ", "_").replace(":", "")
            val file = File(dir, "xiaonao_list_$safe.txt")
            file.writeText(sb.toString())
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** 把一条待办格式化成清单里的一行 */
    private fun formatLine(t: Todo): String {
        val pin = if (t.pinned) "📌" else "  "
        val cat = if (t.category == Category.NONE.name) "" else " (${context.getString(Category.from(t.category).labelRes)})"
        val rep = if (t.repeat == RepeatRule.NONE.name) ""
                  else " 🔁${context.getString(RepeatRule.from(t.repeat).labelRes)}"
        val due = if (t.dueDate != null)
            context.getString(R.string.export_due, SimpleDateFormat("MM-dd", Locale.getDefault()).format(t.dueDate)) else ""
        val note = if (t.note.isBlank()) "" else context.getString(R.string.export_note, t.note)
        return "$pin ${t.title}$cat$due$rep$note"
    }

    /**
     * 导出全部待办为 JSON 文件，写到应用外置私有目录（无需存储权限，用户可经分享取出）。
     * 返回生成的文件；失败返回 null。
     */
    suspend fun exportToJson(ctx: Context): File? {
        return try {
            val all = dao.getAll()
            val arr = JSONArray()
            all.forEach { t ->
                arr.put(
                    JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("done", t.done)
                        put("createdAt", t.createdAt)
                        put("dueDate", t.dueDate ?: JSONObject.NULL)
                        put("category", t.category)
                        put("pinned", t.pinned)
                        put("repeat", t.repeat)
                        put("note", t.note)
                    }
                )
            }
            val root = JSONObject().apply {
                put("app", "小脑")
                put("version", 1)
                put("todos", arr)
            }
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(System.currentTimeMillis())
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: ctx.getExternalFilesDir(null)
                ?: return null
            val file = File(dir, "xiaonao_backup_$ts.json")
            file.writeText(root.toString(2))
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 从 JSON 文件（Uri，通常来自系统文件选择器）导入待办。
     * 按 id 合并（已存在则覆盖）；导入后重新排未来到期提醒。返回成功导入条数。
     */
    suspend fun importFromJson(ctx: Context, uri: Uri): Int {
        return try {
            val text = ctx.contentResolver.openInputStream(uri)
                ?.bufferedReader().use { it?.readText() } ?: return 0
            val arr = JSONObject(text).getJSONArray("todos")
            var n = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val due = if (o.isNull("dueDate")) null else o.optLong("dueDate")
                val t = Todo(
                    id = o.optLong("id", 0),
                    title = o.getString("title"),
                    done = o.optBoolean("done", false),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    dueDate = due,
                    category = o.optString("category", Category.NONE.name),
                    pinned = o.optBoolean("pinned", false),
                    repeat = o.optString("repeat", RepeatRule.NONE.name),
                    note = o.optString("note", "")
                )
                dao.insert(t) // @Insert(REPLACE)：同 id 覆盖，不同 id 新增
                if (due != null && !t.done) AlarmScheduler.schedule(ctx, t)
                n++
            }
            n
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    companion object {
        /** 便捷构造：从任意 Context 拿到仓库（统一用 applicationContext） */
        fun of(context: Context): TodoRepository {
            val ctx = context.applicationContext
            return TodoRepository(ctx, TodoDatabase.get(ctx).todoDao())
        }
    }
}
