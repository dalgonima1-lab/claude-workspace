package com.jpntech.bizassistant.update

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import kotlinx.coroutines.delay
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
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
        data class ReadyToInstall(val versionName: String, val fileName: String) : State
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

    /**
     * 시스템 다운로드 관리자로 받는다. 받기가 끝나면 알림이 뜨고, 그 알림을 누르면 설치 화면이 열린다.
     * 앱이 직접 설치를 시작하지 않으므로 별도 설치 권한이 필요 없다.
     */
    suspend fun download(context: Context) {
        val a = _state.value as? State.Available ?: return
        _state.value = State.Downloading(a.versionName, 0)
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = "biz-assistant-${a.versionName}.apk"
            val req = DownloadManager.Request(Uri.parse(a.apkUrl))
                .setTitle("사업 비서 ${a.versionName}")
                .setDescription("받기가 끝나면 이 알림을 눌러 설치하세요")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            val id = dm.enqueue(req)
            withContext(Dispatchers.IO) {
                while (true) {
                    delay(700)
                    val q = DownloadManager.Query().setFilterById(id)
                    val c = dm.query(q) ?: break
                    val (status, pct, reason) = c.use { cur ->
                        if (!cur.moveToFirst()) return@use Triple(DownloadManager.STATUS_FAILED, 0, -1)
                        val st = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val rs = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        Triple(st, if (total > 0) (done * 100 / total).toInt() else 0, rs)
                    }
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> { _state.value = State.ReadyToInstall(a.versionName, fileName); break }
                        DownloadManager.STATUS_FAILED -> throw IllegalStateException("다운로드 실패 (코드 $reason)")
                        else -> _state.value = State.Downloading(a.versionName, pct)
                    }
                }
            }
        } catch (e: Exception) {
            _state.value = State.Error("다운로드 실패: ${e.message}")
        }
    }

    /** 시스템 다운로드 목록을 연다. 거기서 파일을 누르면 설치가 시작된다. */
    fun openDownloads(context: Context) {
        val i = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    fun dismiss() { _state.value = State.Idle }
}
