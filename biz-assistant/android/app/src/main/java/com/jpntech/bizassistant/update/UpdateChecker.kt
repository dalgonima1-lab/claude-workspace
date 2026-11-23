package com.jpntech.bizassistant.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * GitHub 릴리스에서 새 버전을 찾아 받아 설치한다.
 * 릴리스 태그는 app-v<버전이름>-<버전코드> 꼴이고, APK 파일이 첨부되어 있다.
 */
object UpdateChecker {
    private const val LATEST = "https://api.github.com/repos/dalgonima1-lab/claude-workspace/releases/latest"
    private const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data class Available(val versionName: String, val versionCode: Long, val apkUrl: String, val notes: String) : State
        data class Downloading(val versionName: String, val percent: Int) : State
        data class ReadyToInstall(val versionName: String, val file: File) : State
        data class Error(val message: String) : State
        data object UpToDate : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    @Serializable private data class GhAsset(val name: String = "", val browser_download_url: String = "")
    @Serializable private data class GhRelease(val tag_name: String = "", val body: String = "", val assets: List<GhAsset> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val tagPattern = Regex("""app-v([0-9][0-9.]*)-(\d+)""")

    fun installedVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun installedVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** 앱이 켜질 때. 6시간에 한 번만 조회한다. */
    suspend fun checkIfDue(context: Context) {
        val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)
        val last = prefs.getLong("lastCheck", 0L)
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS && _state.value !is State.Idle) return
        if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return
        check(context)
    }

    suspend fun check(context: Context) {
        _state.value = State.Checking
        val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)
        prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
        try {
            val release = withContext(Dispatchers.IO) {
                val req = Request.Builder().url(LATEST).header("accept", "application/vnd.github+json").header("user-agent", "biz-assistant-app").build()
                client.newCall(req).execute().use { res ->
                    if (res.code == 404) return@withContext null
                    if (!res.isSuccessful) throw IllegalStateException("GitHub ${res.code}")
                    json.decodeFromString(GhRelease.serializer(), res.body?.string().orEmpty())
                }
            }
            if (release == null) { _state.value = State.UpToDate; return }
            val m = tagPattern.find(release.tag_name)
            val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
            if (m == null || apk == null) { _state.value = State.UpToDate; return }
            val code = m.groupValues[2].toLong()
            _state.value = if (code > installedVersionCode(context)) {
                State.Available(m.groupValues[1], code, apk.browser_download_url, release.body)
            } else State.UpToDate
        } catch (e: Exception) {
            _state.value = State.Error("업데이트 확인 실패: ${e.message}")
        }
    }

    suspend fun download(context: Context) {
        val a = _state.value as? State.Available ?: return
        _state.value = State.Downloading(a.versionName, 0)
        try {
            val file = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val out = File(dir, "biz-assistant-${a.versionName}.apk")
                val req = Request.Builder().url(a.apkUrl).header("user-agent", "biz-assistant-app").build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) throw IllegalStateException("다운로드 실패 ${res.code}")
                    val body = res.body ?: throw IllegalStateException("빈 응답")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        out.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            var done = 0L
                            var lastPct = -1
                            while (input.read(buf).also { read = it } >= 0) {
                                output.write(buf, 0, read)
                                done += read
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) { lastPct = pct; _state.value = State.Downloading(a.versionName, pct) }
                                }
                            }
                        }
                    }
                }
                out
            }
            _state.value = State.ReadyToInstall(a.versionName, file)
        } catch (e: Exception) {
            _state.value = State.Error("다운로드 실패: ${e.message}")
        }
    }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    /** 시스템 설치 화면을 연다. 같은 서명키라 기존 앱 위에 업데이트로 설치된다. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    fun dismiss() { _state.value = State.Idle }
}
