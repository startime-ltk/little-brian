package com.example.xiaonao.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.xiaonao.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 接收小组件里「勾选/取消勾选」的点击。
 * 在后台线程改数据库，然后让小组件刷新。
 */
class WidgetActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE = "com.example.xiaonao.ACTION_TOGGLE_TODO"
        const val EXTRA_TODO_ID = "todo_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val id = intent.getLongExtra(EXTRA_TODO_ID, -1L)
            if (id != -1L) {
                CoroutineScope(Dispatchers.IO).launch {
                    TodoRepository.of(context).toggle(id)
                    TodoWidgetProvider.notifyUpdate(context)
                }
            }
        }
    }
}
