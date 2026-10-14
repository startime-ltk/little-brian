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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
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
    // 直连失败时会依次拼接「镜像 + 原地址」重试（镜像只做透明代理，不改文件内容）。
    // 注：ghproxy.net 已失效（实测 22KB/s 且常 503），v1.23 起移除。
    private val MIRROR_PREFIXES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/"
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

    /**
     * 分块并行下载的线程数。实测：jsDelivr 单连接约 1MB/s，4 线程约 2.7MB/s（≈2.7 倍），
     * 但 8 线程会被 CDN 限速惩罚（暴跌），所以固定 4，不贪多。
     */
    private const val DOWNLOAD_THREADS = 4

    /** 测速探测：每个源采样的字节数（小请求，只为测连接+吞吐） */
    private const val PROBE_BYTES = 96 * 1024

    /** 测速探测：单源总时长硬上限（含建连），超时视为不可用 */
    private const val PROBE_TIMEOUT_MS = 2500

    /** 测速探测：低于此速度的源不配参与正式下载（6MB 要下 96 秒以上，没意义） */
    private const val PROBE_MIN_SPEED = 64 * 1024L

    /** 一个测速结果：源地址 + 实测字节/秒 */
    private data class ProbedSource(val url: String, val speedBps: Long)

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
        val releaseUrl = releaseUrl(versionName)
        return (listOf(apkUrl, releaseUrl) + MIRROR_PREFIXES.map { it + releaseUrl }).distinct()
    }

    /** 由版本号拼 Release 附件直链 */
    private fun releaseUrl(versionName: String): String =
        "https://github.com/$GITHUB_REPO_PATH/releases/download/v$versionName/little-brain-v$versionName.apk"

    /**
     * 下载 APK 到应用私有目录（无需存储权限）。
     * onProgress 回调 (百分比 0~100, 平均网速 KB/s)。
     *
     * v1.23 起改为「先测速、再下载」：
     *  1. 对国内友好源（jsDelivr 主源 + 各镜像）**并行**发起小尺寸 Range 请求计时，
     *     按实测速度降序得到可用源列表——手机当前网络下谁快用谁，慢源/死源直接出局；
     *  2. 按排名逐个尝试 4 线程 Range 分块并行下载（CDN 常限单个连接速度，
     *     多段并发实测约 2.7 倍提速）；某个源不可用就顺延到下一个排名；
     *  3. 全部测速失败（极端网络）时走传统兜底：逐源单线程下载。
     * 支持协程取消。返回下载好的文件，全部失败抛异常。
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        versionName: String,
        onProgress: (percent: Int, speedKBps: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, "update.apk")
        var lastError: Exception? = null

        // 1) 并行测速：主源 + 国内镜像（GitHub 直链不测——国内基本连不上，只留作兜底）
        val releaseUrl = releaseUrl(versionName)
        val probeList = (listOf(apkUrl) + MIRROR_PREFIXES.map { it + releaseUrl }).distinct()
        val ranked = probeSources(probeList)
        Log.i("AppUpdater", "测速完成：${ranked.size}/${probeList.size} 源可用 → ${ranked.map { "${it.speedBps / 1024}KB/s" }}")

        // 2) 按实测速度排名，依次尝试分块/单线程下载
        for (p in ranked) {
            try {
                var done = false
                try {
                    done = tryMultithreaded(p.url, file, DOWNLOAD_THREADS, onProgress)
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce   // 用户取消：直接向上传，不回退、不换源
                } catch (e: Exception) {
                    Log.w("AppUpdater", "分块下载失败，回退单线程（${p.url}）：${e.message}")
                    done = false
                }
                if (!done) downloadSingle(p.url, file, onProgress)
                return@withContext file
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                Log.w("AppUpdater", "下载失败（${p.url}）：${e.message}")
            }
        }

        // 3) 传统兜底：测速全挂时，按原始优先级逐源单线程硬试
        for (url in downloadCandidates(apkUrl, versionName)) {
            try {
                downloadSingle(url, file, onProgress)
                return@withContext file
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                Log.w("AppUpdater", "兜底下载失败（$url）：${e.message}")
            }
        }
        throw lastError ?: IllegalStateException("所有下载地址均失败")
    }

    /**
     * 并行测速：对所有候选源同时发起小尺寸 Range 请求，按实测速度降序返回可用源。
     * 连不上 / 龟速（< PROBE_MIN_SPEED）的源被过滤，正式下载时不再浪费时间。
     */
    private suspend fun probeSources(urls: List<String>): List<ProbedSource> = coroutineScope {
        urls.map { url -> async(Dispatchers.IO) { probeOne(url) } }
            .awaitAll()
            .filterNotNull()
            .sortedByDescending { it.speedBps }
    }

    /**
     * 探测单个源：GET 前 PROBE_BYTES 字节，实测速度。
     * 采样不足一半 / 总时长超 PROBE_TIMEOUT_MS / 速度低于门槛 → 返回 null。
     */
    private fun probeOne(url: String): ProbedSource? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = PROBE_TIMEOUT_MS
            conn.readTimeout = PROBE_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Range", "bytes=0-${PROBE_BYTES - 1}")
            val startMs = System.currentTimeMillis()
            var got = 0L
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    got += read
                    if (got >= PROBE_BYTES) break
                    if (System.currentTimeMillis() - startMs > PROBE_TIMEOUT_MS) break
                }
            }
            conn.disconnect()
            val elapsedMs = System.currentTimeMillis() - startMs
            if (got < PROBE_BYTES / 2 || elapsedMs <= 0) return null
            val speed = got * 1000 / elapsedMs
            if (speed < PROBE_MIN_SPEED) return null
            ProbedSource(url, speed)
        } catch (e: Exception) {
            null
        }
    }

    /** 单线程下载单个 url 到 file，带进度/网速回调（分块不可用时的兜底） */
    private suspend fun downloadSingle(
        url: String,
        file: File,
        onProgress: (percent: Int, speedKBps: Int) -> Unit
    ) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = true
        val total = conn.contentLength.takeIf { it > 0 } ?: -1
        val startTime = System.currentTimeMillis()
        var lastPercent = -1
        var downloaded = 0L
        conn.inputStream.use { input ->
            FileOutputStream(file).use { out ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    downloaded += read
                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000.0
                    val speed = if (elapsedSec > 0.1) (downloaded / 1024.0 / elapsedSec).toInt() else 0
                    if (total > 0) {
                        val pct = (downloaded * 100 / total).toInt()
                        if (pct != lastPercent) { lastPercent = pct; onProgress(pct, speed) }
                    }
                    coroutineContext.ensureActive()
                }
            }
        }
        conn.disconnect()
    }

    /**
     * 4 线程 Range 分块并行下载。探测到不支持 Range 或文件过小则返回 false（交由单线程）。
     * 各段下载到独立临时分片，完成后按序合并为 file。任一段失败抛异常（由上层回退单线程）。
     */
    private suspend fun tryMultithreaded(
        url: String,
        file: File,
        threads: Int,
        onProgress: (percent: Int, speedKBps: Int) -> Unit
    ): Boolean = coroutineScope {
        val total = probeSize(url) ?: return@coroutineScope false
        if (total < 1_000_000L) return@coroutineScope false   // 小文件不值得分块
        val partDir = File(file.parentFile, "update_parts").apply { mkdirs() }
        val startTime = System.currentTimeMillis()
        val downloaded = AtomicLong(0)
        val lastCb = AtomicLong(0)
        fun report(final: Boolean = false) {
            val now = System.currentTimeMillis()
            val last = lastCb.get()
            if (final || (now - last >= 120 && lastCb.compareAndSet(last, now))) {
                val d = downloaded.get()
                val elapsedSec = (now - startTime) / 1000.0
                val speed = if (elapsedSec > 0.1) (d / 1024.0 / elapsedSec).toInt() else 0
                val pct = if (total > 0) (d * 100 / total).toInt() else 0
                onProgress(pct, speed)
            }
        }
        val chunk = total / threads
        try {
            (0 until threads).map { i ->
                async(Dispatchers.IO) {
                    val start = i * chunk
                    val end = if (i == threads - 1) total - 1 else (i + 1) * chunk - 1
                    val partFile = File(partDir, "part_$i")
                    downloadRange(url, start, end, partFile) { n ->
                        downloaded.addAndGet(n.toLong())
                        report()
                    }
                }
            }.awaitAll()
            // 按序合并分片为最终文件
            FileOutputStream(file).use { out ->
                for (i in 0 until threads) {
                    File(partDir, "part_$i").inputStream().use { it.copyTo(out, 64 * 1024) }
                }
            }
            report(final = true)
            true
        } finally {
            partDir.listFiles()?.forEach { it.delete() }
            partDir.delete()
        }
    }

    /** 探测 url 是否支持 Range 并返回总大小（不支持返回 null） */
    private fun probeSize(url: String): Long? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Range", "bytes=0-0")
            val code = conn.responseCode
            val cr = conn.getHeaderField("Content-Range")   // 形如 bytes 0-0/6204070
            conn.disconnect()
            if (code == 206 && cr != null && cr.contains("/")) {
                cr.substringAfterLast('/').trim().toLongOrNull()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /** 下载 [start,end] 字节段到 partFile；每读到一块回调 onBytes(本次字节数) */
    private suspend fun downloadRange(
        url: String,
        start: Long,
        end: Long,
        partFile: File,
        onBytes: (Int) -> Unit
    ) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Range", "bytes=$start-$end")
        conn.inputStream.use { input ->
            FileOutputStream(partFile).use { out ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    out.write(buf, 0, read)
                    onBytes(read)
                    coroutineContext.ensureActive()
                }
            }
        }
        conn.disconnect()
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
