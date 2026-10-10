package com.example.xiaonao.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.xiaonao.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 设备重启后，系统清掉了所有闹钟。
 * 这里在开机完成后把所有「未完成且未到期」的提醒重新排上。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CoroutineScope(Dispatchers.IO).launch {
                TodoRepository.of(context).rescheduleAll()
            }
        }
    }
}
