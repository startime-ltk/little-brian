package com.example.xiaonao.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.xiaonao.R
import com.example.xiaonao.TodoRepository
import com.example.xiaonao.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟到点后由系统广播唤起，负责弹出「待办到期」通知。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_TODO_ID = "todo_id"
        const val CHANNEL_ID = "xiaonao_reminder"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TODO_ID, -1L)
        if (id == -1L) return
        CoroutineScope(Dispatchers.IO).launch {
            val todo = TodoRepository.of(context).getById(id) ?: return@launch
            if (todo.done) return@launch // 已经完成了就不打扰
            showNotification(context, todo.title)
        }
    }

    private fun showNotification(context: Context, title: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // Android 8+ 必须有通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notif_channel_desc) }
            nm.createNotificationChannel(channel)
        }

        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val contentIntent = PendingIntent.getActivity(context, 0, launch, piFlags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        nm.notify(title.hashCode(), notification)
    }
}
