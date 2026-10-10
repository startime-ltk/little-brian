package com.example.xiaonao

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.xiaonao.data.Category
import com.example.xiaonao.data.RepeatRule
import com.example.xiaonao.data.Todo
import com.example.xiaonao.widget.TodoWidgetProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * 界面用的 ViewModel。
 * 每次数据变动后顺手通知桌面小组件刷新（notifyUpdate）。
 */
class TodoViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TodoRepository.of(app)

    val todos: StateFlow<List<Todo>> = repo.todos.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun add(
        title: String,
        dueDate: Long? = null,
        category: String = Category.NONE.name,
        repeat: String = RepeatRule.NONE.name,
        note: String = ""
    ) = viewModelScope.launch {
        repo.add(title, dueDate, category, repeat, note)
        TodoWidgetProvider.notifyUpdate(getApplication())
    }

    /**
     * 勾选 / 取消勾选。
     * @param onRepeated 若这是一条周期性待办且刚被勾「完成」，回调会收到顺延后的新到期时间
     *                   （界面据此提示「已顺延至 X 月 X 日」）；普通待办不回调。
     */
    fun toggle(id: Long, onRepeated: ((Long) -> Unit)? = null) = viewModelScope.launch {
        val nextDue = repo.toggle(id)
        TodoWidgetProvider.notifyUpdate(getApplication())
        if (nextDue != null) onRepeated?.invoke(nextDue)
    }

    fun delete(id: Long) = viewModelScope.launch {
        repo.delete(id)
        TodoWidgetProvider.notifyUpdate(getApplication())
    }

    fun clearDone() = viewModelScope.launch {
        repo.clearDone()
        TodoWidgetProvider.notifyUpdate(getApplication())
    }

    fun update(todo: Todo) = viewModelScope.launch {
        repo.update(todo)
        TodoWidgetProvider.notifyUpdate(getApplication())
    }

    fun restore(todo: Todo) = viewModelScope.launch {
        repo.restore(todo)
        TodoWidgetProvider.notifyUpdate(getApplication())
    }

    fun togglePin(id: Long) = viewModelScope.launch {
        val t = repo.getById(id) ?: return@launch
        repo.setPinned(id, !t.pinned)
        TodoWidgetProvider.notifyUpdate(getApplication())
    }

    /** 批量导入：每行一条，统一归类到指定分类、指定周期 */
    fun batchAdd(
        titles: List<String>,
        category: String,
        repeat: String = RepeatRule.NONE.name
    ) = viewModelScope.launch {
        repo.batchAdd(titles, category, repeat)
        TodoWidgetProvider.notifyUpdate(getApplication())
    }

    /** 导出可读文本清单（区别于 JSON 备份，便于分享/迁移） */
    fun exportText(context: Context, onDone: (File?) -> Unit) = viewModelScope.launch {
        val file = repo.exportToText(context.applicationContext)
        onDone(file)
    }

    /** 导出为 JSON 文件，结果通过回调返回（在调用方线程外完成） */
    fun export(context: Context, onDone: (File?) -> Unit) = viewModelScope.launch {
        val file = repo.exportToJson(context.applicationContext)
        TodoWidgetProvider.notifyUpdate(getApplication())
        onDone(file)
    }

    /** 从 JSON 文件导入，结果返回导入条数 */
    fun import(context: Context, uri: Uri, onDone: (Int) -> Unit) = viewModelScope.launch {
        val n = repo.importFromJson(context.applicationContext, uri)
        TodoWidgetProvider.notifyUpdate(getApplication())
        onDone(n)
    }
}
