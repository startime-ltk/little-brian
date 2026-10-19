package com.example.xiaonao.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.example.xiaonao.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xiaonao.data.Category

/**
 * 小组件「显示哪一类待办」的偏好存取。
 *
 * 这里只做存储与默认值逻辑，不再承担系统配置页的角色：
 * 国产桌面（小米/华为/OPPO 等）对 android:configure 配置页支持很差，
 * 经常不启动或直接报「无法添加小部件」，所以 v1.3.3 起已移除配置页，
 * 改为在 App 内的「小组件设置」里配置（稳定可靠，不依赖桌面实现）。
 *
 * 两级偏好：
 *  - 单组件偏好 filter_<appWidgetId>：只对某一个已添加的组件生效；
 *  - 全局默认 default_filter：新添加的组件、以及没单独设过的组件都用它。
 */
class WidgetConfigActivity : ComponentActivity() {

    companion object {
        const val PREFS = "widget_filters"
        const val FILTER_ALL = "ALL"
        private const val KEY_DEFAULT = "default_filter"

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** 全局默认分类（新组件 / 未单独设置的组件都用它） */
        fun defaultFilterOf(context: Context): String =
            prefs(context).getString(KEY_DEFAULT, FILTER_ALL) ?: FILTER_ALL

        fun saveDefaultFilter(context: Context, filter: String) {
            prefs(context).edit().putString(KEY_DEFAULT, filter).apply()
        }

        /** 某个组件显示哪一类；没单独设过则回退到全局默认 */
        fun filterOf(context: Context, appWidgetId: Int): String =
            prefs(context).getString("filter_$appWidgetId", null) ?: defaultFilterOf(context)

        fun saveFilter(context: Context, appWidgetId: Int, filter: String) {
            prefs(context).edit().putString("filter_$appWidgetId", filter).apply()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 先按「取消」兜底，若用户没有正常完成配置，系统不会把 widget 加进来
        setResult(RESULT_CANCELED)

        setContent {
            var selected by remember { mutableStateOf(FILTER_ALL) }

            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.widget_config_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        val options = listOf(FILTER_ALL to stringResource(R.string.chip_all)) +
                                Category.all.filter { it != Category.NONE }.map { it.name to getString(it.labelRes) }

                        options.forEach { (key, label) ->
                            FilterChip(
                                selected = selected == key,
                                onClick = { selected = key },
                                label = { Text(label) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                saveFilter(this@WidgetConfigActivity, appWidgetId, selected)
                                // 主动把这一块小组件刷新成所选分类
                                TodoWidgetProvider.updateWidget(
                                    this@WidgetConfigActivity,
                                    AppWidgetManager.getInstance(this@WidgetConfigActivity),
                                    appWidgetId
                                )
                                val result = Intent().apply {
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                                }
                                setResult(RESULT_OK, result)
                                finish()
                            }
                        ) { Text(stringResource(R.string.btn_done)) }
                    }
                }
            }
        }
    }
}
