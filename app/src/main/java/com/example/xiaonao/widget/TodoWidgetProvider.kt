package com.example.xiaonao.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.example.xiaonao.MainActivity
import com.example.xiaonao.QuickAddActivity
import com.example.xiaonao.R

/**
 * 桌面小组件的「控制器」。
 * 系统会在合适的时候回调 onUpdate，我们在里面把列表、点击意图绑好。
 */
class TodoWidgetProvider : android.appwidget.AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    companion object {
        /** 把单个小组件刷新一遍（外层 try-catch 兜底，绝不让 onUpdate 抛异常导致整块变「无法添加」） */
        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_list)

                // 标题：默认「小脑」，若该组件设了分类筛选则显示「小脑 · 学习」这类
                val filter = WidgetConfigActivity.filterOf(context, appWidgetId)
                val titleText =
                    if (filter == WidgetConfigActivity.FILTER_ALL) context.getString(R.string.app_name)
                    else "${context.getString(R.string.app_name)} · ${com.example.xiaonao.data.Category.from(filter).label}"
                views.setTextViewText(R.id.widget_title, titleText)

                // 1) 列表数据交给 RemoteViewsService（支持滚动）
                val serviceIntent = Intent(context, TodoWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    // 显式指定 Component / package，防止高版本/OEM 桌面解析不到服务
                    component = ComponentName(context, TodoWidgetService::class.java)
                    setPackage(context.packageName)
                }
                // 两参数 setRemoteAdapter(viewId, intent) 从 API 11 起就存在，所有 Android 版本都兼容；
                // 带 appWidgetId 的三参数版本是 API 31 才新增，老系统调用会 NoSuchMethodError，绝不能用。
                views.setRemoteAdapter(R.id.widget_listview, serviceIntent)
                views.setEmptyView(R.id.widget_listview, R.id.widget_empty)

                // 2) 点击条目 -> 发广播（具体 todo id 由 Service 的 fillInIntent 补全）
                val toggleIntent = Intent(context, WidgetActionReceiver::class.java).apply {
                    action = WidgetActionReceiver.ACTION_TOGGLE
                }
                val togglePending = android.app.PendingIntent.getBroadcast(
                    context, 0, toggleIntent, pendingFlags()
                )
                views.setPendingIntentTemplate(R.id.widget_listview, togglePending)

                // 3) 点标题 -> 打开 App
                val launch = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_title,
                    android.app.PendingIntent.getActivity(context, 0, launch, pendingFlags())
                )

                // 4) 点「+ 添加」条 -> 唤起透明弹窗快速添加（不离开桌面观感）
                val addIntent = Intent(context, QuickAddActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                views.setOnClickPendingIntent(
                    R.id.widget_add_bar,
                    android.app.PendingIntent.getActivity(context, 1, addIntent, pendingFlags())
                )

                manager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                // 兜底：至少显示一个带标题的空框架，避免系统判定「无法添加小部件」
                Log.e("TodoWidget", "updateWidget failed", e)
                try {
                    val fallback = RemoteViews(context.packageName, R.layout.widget_list)
                    fallback.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                    manager.updateAppWidget(appWidgetId, fallback)
                } catch (_: Exception) {
                }
            }
        }

        /** 通知所有已添加的小组件重新拉数据 */
        fun notifyUpdate(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, TodoWidgetProvider::class.java)
            )
            for (id in ids) {
                mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_listview)
            }
        }

        /**
         * 完整重绘所有已添加的小组件：连标题一起刷新（改完分类筛选设置后调用）。
         * notifyUpdate 只刷列表数据，标题不会变，所以要走一遍 updateWidget。
         */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, TodoWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, mgr, id)
                mgr.notifyAppWidgetViewDataChanged(id, R.id.widget_listview)
            }
        }

        /** 兼容 Android 12 前后的 PendingIntent 可变性要求 */
        private fun pendingFlags(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_MUTABLE
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
    }
}
