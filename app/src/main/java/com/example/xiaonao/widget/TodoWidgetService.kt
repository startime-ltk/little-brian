package com.example.xiaonao.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.xiaonao.R
import com.example.xiaonao.TodoRepository
import com.example.xiaonao.data.Todo
import androidx.core.content.ContextCompat
import kotlinx.coroutines.runBlocking

/**
 * 给小组件提供「列表里的每一行」的工厂。
 * 因为 RemoteViews 不支持 RecyclerView，列表型 widget 必须用 RemoteViewsService。
 */
class TodoWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        return TodoWidgetFactory(applicationContext, appWidgetId)
    }
}

class TodoWidgetFactory(
    private val context: android.content.Context,
    private val appWidgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<Todo> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items = try {
            // 仅取未完成的事项，桌面始终聚焦「待办」
            val active = runBlocking { TodoRepository.of(context).getActive() }
            // 按该小组件配置的分类过滤（"ALL" 表示不过滤）
            val filter = WidgetConfigActivity.filterOf(context, appWidgetId)
            if (filter == WidgetConfigActivity.FILTER_ALL) active
            else active.filter { it.category == filter }
        } catch (e: Exception) {
            Log.e("TodoWidget", "onDataSetChanged failed", e)
            emptyList()
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val todo = items[position]
        val rv = RemoteViews(context.packageName, R.layout.widget_list_item)
        // 周期性待办加个 🔁 前缀，桌面上也能一眼看出是每天/每周要做的事
        val rule = com.example.xiaonao.data.RepeatRule.from(todo.repeat)
        val titleText =
            if (rule == com.example.xiaonao.data.RepeatRule.NONE) todo.title
            else "🔁 ${todo.title}"
        rv.setTextViewText(R.id.item_title, titleText)
        rv.setTextViewText(R.id.item_check, if (todo.done) "✓" else "○")

        // 备注：没写就整行隐藏，写了才显示（RemoteViews 只能靠 setViewVisibility 控制）
        if (todo.note.isBlank()) {
            rv.setViewVisibility(R.id.item_note, android.view.View.GONE)
        } else {
            rv.setViewVisibility(R.id.item_note, android.view.View.VISIBLE)
            rv.setTextViewText(R.id.item_note, todo.note)
        }

        // 按分类给左侧色条上色（RemoteViews 只能 set 资源 id 或颜色值）
        try {
            val cat = com.example.xiaonao.data.Category.from(todo.category)
            val color = ContextCompat.getColor(context, cat.colorRes)
            rv.setInt(R.id.item_color, "setBackgroundColor", color)
        } catch (e: Exception) {
            Log.e("TodoWidget", "set color failed", e)
        }

        // 点击这一行 -> 把 todo id 补进广播意图，交给 WidgetActionReceiver 处理
        val fill = Intent().apply {
            putExtra(WidgetActionReceiver.EXTRA_TODO_ID, todo.id)
        }
        rv.setOnClickFillInIntent(R.id.item_check, fill)
        rv.setOnClickFillInIntent(R.id.item_title, fill)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items[position].id
    override fun hasStableIds(): Boolean = true
}
