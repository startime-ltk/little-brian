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
import kotlinx.coroutines.ensureActive
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

    // 小脑 Little Brain 的更新清单地址（little-brian 仓库 main 分支）
    private const val UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/startime-ltk/little-brian/main/update/latest.json"

    // 国内直连 GitHub 经常失败：这些是常用的加速镜像前缀，
    // 直连失败时会依次拼接「镜像 + 原地址」重试（镜像只做透明代理，不改文件内容）
    private val MIRROR_PREFIXES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/"
    )

    /** 生成候选地址列表：原始地址放最前，其后是各镜像拼接版 */
    private fun candidates(url: String): List<String> =
        listOf(url) + MIRROR_PREFIXES.map { it + url }

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

    /** 当前已安装的 versionName，例如 "1.14" */
    fun currentVersionName(): String = BuildConfig.VERSION_NAME

    /** 作者 GitHub 主页（关于页跳转用） */
    const val GITHUB_HOME = "https://github.com/startime-ltk"

    /** 项目仓库地址 */
    const val GITHUB_REPO = "https://github.com/startime-ltk/little-brian"

    /** 仓库路径（用户名/仓库名），用于从版本号拼接 Release 直链做下载兜底 */
    private const val GITHUB_REPO_PATH = "startime-ltk/little-brian"

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
     * 拉取 latest.json 并解析。直连失败自动切换镜像重试；
     * 全部失败返回 null（网络异常 / 仓库未配置都算失败，静默忽略）。
     */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (url in candidates(UPDATE_JSON_URL)) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val obj = JSONObject(text)
                return@withContext UpdateInfo(
                    versionCode = obj.getInt("versionCode"),
                    versionName = obj.getString("versionName"),
                    apkUrl = obj.getString("apkUrl"),
                    note = obj.optString("note", "")
                )
            } catch (e: Exception) {
                lastError = e
                Log.w("AppUpdater", "拉取更新信息失败（$url）：${e.message}")
            }
        }
        Log.w("AppUpdater", "所有地址均拉取失败：${lastError?.message}")
        null
    }

    /** 是否有可用更新（远端 versionCode 更大） */
    fun hasUpdate(info: UpdateInfo?): Boolean =
        info != null && info.versionCode > currentVersionCode()

    /**
     * APK 下载候选地址（按优先级）：
     *  1. apkUrl —— latest.json 里配的主源，国内优先用 jsDelivr CDN（cdn.jsdelivr.net/gh/…）；
     *  2. GitHub Release 附件直链（由版本号拼，作兜底）；
     *  3. 各国内加速镜像代理上面的 Release 直链。
     * 去重后依次尝试，某个源失败自动换下一个。
     */
    private fun downloadCandidates(apkUrl: String, versionName: String): List<String> {
        val releaseUrl =
            "https://github.com/$GITHUB_REPO_PATH/releases/download/v$versionName/little-brain-v$versionName.apk"
        return (listOf(apkUrl, releaseUrl) + MIRROR_PREFIXES.map { it + releaseUrl }).distinct()
    }

    /**
     * 下载 APK 到应用私有目录（无需存储权限）。
     * onProgress 回调 (百分比 0~100, 瞬时网速 KB/s)。
     * 主源失败自动切换国内镜像重试；支持协程取消（取消后循环内立即退出）。
     * 返回下载好的文件，全部失败抛异常。
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        versionName: String,
        onProgress: (percent: Int, speedKBps: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (url in downloadCandidates(apkUrl, versionName)) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                conn.instanceFollowRedirects = true
                val total = conn.contentLength.takeIf { it > 0 } ?: -1
                val dir = context.getExternalFilesDir(null) ?: context.filesDir
                val file = File(dir, "update.apk")
                var lastPercent = -1
                val startTime = System.currentTimeMillis()
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { out ->
                        // 64KB 缓冲区：比默认 8KB 少一半系统调用，下载更稳更快
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var downloaded = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            downloaded += read
                            // 平均网速（KB/s）
                            val elapsedSec =
                                (System.currentTimeMillis() - startTime) / 1000.0
                            val speedKBps =
                                if (elapsedSec > 0.1) (downloaded / 1024.0 / elapsedSec).toInt() else 0
                            if (total > 0) {
                                val pct = (downloaded * 100 / total).toInt()
                                if (pct != lastPercent) {
                                    lastPercent = pct
                                    onProgress(pct, speedKBps)
                                }
                            }
                            // 支持取消：协程被 cancel 时这里立即抛出退出
                            coroutineContext.ensureActive()
                        }
                    }
                }
                conn.disconnect()
                return@withContext file
            } catch (e: Exception) {
                lastError = e
                Log.w("AppUpdater", "下载失败（$url）：${e.message}")
                // 用户主动取消时不再尝试其它地址
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
        throw lastError ?: IllegalStateException("所有下载地址均失败")
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
