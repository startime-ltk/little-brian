package com.example.xiaonao

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.xiaonao.data.Todo
import com.example.xiaonao.widget.AlarmReceiver

/**
 * 封装到期提醒的闹钟安排与取消。
 * 用精确闹钟（setExactAndAllowWhileIdle），即使熄屏也会在到期时刻唤醒并弹通知。
 */
object AlarmScheduler {

    /** 为某条待办安排提醒（仅在到期时间仍晚于当前时生效） */
    fun schedule(context: Context, todo: Todo) {
        val due = todo.dueDate ?: return
        if (due <= System.currentTimeMillis()) return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // Android 12+ 需要用户授予「精确闹钟」权限，否则排不上；这里优雅降级不崩溃
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return
        am.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            due,
            pendingIntent(context, todo.id)
        )
    }

    /** 取消某条待办的提醒 */
    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(context, id))
    }

    private fun pendingIntent(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_TODO_ID, id)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE
            else 0
        // todo.id 自增，转 Int 作为 requestCode 足够区分
        return PendingIntent.getBroadcast(context, id.toInt(), intent, flags)
    }
}
