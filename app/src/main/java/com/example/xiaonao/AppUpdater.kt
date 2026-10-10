package com.example.xiaonao

import android.app.Activity
import com.example.xiaonao.BuildConfig
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内「点一下就能更新」——免费方案：拉 GitHub 仓库里的 latest.json，
 * 发现更高 versionCode 就下载 APK 并调起系统安装器。
 *
 * 零成本、不依赖任何第三方账号：latest.json 用 raw.githubusercontent 直链（无限流），
 * APK 作为 GitHub Release 附件，地址写进 latest.json 的 apkUrl。
 *
 * 你要把下面两个常量改成自己的仓库：
 *   UPDATE_JSON_URL = https://raw.githubusercontent.com/<你的用户名>/<仓库名>/main/update/latest.json
 */
object AppUpdater {

    // TODO: 改成你自己的仓库路径（main 可换成分支名）
    private const val UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/OWNER/REPO/main/update/latest.json"

    private const val PREFS = "xiaonao_update"
    private const val KEY_LAST_CHECK = "last_check_ms"

    /** 版本清单内容 */
    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val note: String
    )

    /** 当前已安装的 versionCode（用 BuildConfig，避免老 API 兼容麻烦） */
    fun currentVersionCode(): Int = BuildConfig.VERSION_CODE

    /** 是否允许安装未知来源 APK（Android 8+ 需要用户在设置里开启） */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    /** 跳转到「允许安装未知应用」设置页，让用户手动开启 */
    fun openInstallSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * 拉取 latest.json 并解析。失败返回 null（网络异常 / 仓库未配置都算失败，静默忽略）。
     */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(UPDATE_JSON_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val obj = JSONObject(text)
            UpdateInfo(
                versionCode = obj.getInt("versionCode"),
                versionName = obj.getString("versionName"),
                apkUrl = obj.getString("apkUrl"),
                note = obj.optString("note", "")
            )
        } catch (e: Exception) {
            Log.w("AppUpdater", "拉取更新信息失败：${e.message}")
            null
        }
    }

    /** 是否有可用更新（远端 versionCode 更大） */
    fun hasUpdate(info: UpdateInfo?): Boolean =
        info != null && info.versionCode > currentVersionCode()

    /**
     * 下载 APK 到应用私有目录（无需存储权限），onProgress 回调 0~100 的进度。
     * 返回下载好的文件，失败抛异常。
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val conn = URL(apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        val total = conn.contentLength.takeIf { it > 0 } ?: -1
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "update.apk")
        conn.inputStream.use { input ->
            FileOutputStream(file).use { out ->
                val buf = ByteArray(8 * 1024)
                var read: Int
                var downloaded = 0L
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    if (total > 0) onProgress((downloaded * 100 / total).toInt())
                }
            }
        }
        conn.disconnect()
        file
    }

    /** 用系统安装器安装下载好的 APK（FileProvider 共享，兼容 Android 7+） */
    fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 今天是否已检查过（节流：默认每天最多自动查一次） */
    fun shouldAutoCheck(context: Context, intervalMs: Long = 24 * 60 * 60 * 1000L): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK, 0)
        return System.currentTimeMillis() - last >= intervalMs
    }

    fun markChecked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
    }
}
