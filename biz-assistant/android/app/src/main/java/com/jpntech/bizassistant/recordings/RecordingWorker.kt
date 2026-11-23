package com.jpntech.bizassistant.recordings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import androidx.work.WorkerParameters
import com.jpntech.bizassistant.BizApp
import com.jpntech.bizassistant.MainActivity
import com.jpntech.bizassistant.R
import com.jpntech.bizassistant.data.ConfirmDraft
import com.jpntech.bizassistant.data.db.PendingEntity
import com.jpntech.bizassistant.data.db.RecordingEntity
import com.jpntech.bizassistant.data.remote.Analysis
import com.jpntech.bizassistant.data.sync.SyncEngine
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.io.File

const val CHANNEL_PENDING = "pending"
const val CHANNEL_PROGRESS = "progress"
const val EXTRA_PENDING_ID = "pendingId"
private const val PROGRESS_ID = 9_001

data class CallRecording(val mediaId: Long, val name: String, val dateAdded: Long, val size: Long, val mime: String?, val uri: Uri)

/**
 * 새 통화 녹음을 찾아 글자로 바꾸고 분석해 대기함에 넣는다.
 * 1) 미디어 저장소에서 통화 녹음 폴더의 새 파일을 찾는다
 * 2) 파일 크기가 더 변하지 않을 때(녹음이 끝났을 때)만 처리한다
 * 3) 조각으로 잘라 중계 서버에 올려 글자로 받는다
 * 4) 분석 결과를 대기함에 저장하고 알림을 띄운다 (설정에 따라 바로 등록)
 */
class RecordingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object { const val KEY_AFTER_CALL = "afterCall"; const val KEY_RETRY_ERRORS = "retryErrors" }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    override suspend fun doWork(): Result {
        val app = BizApp.from(applicationContext)
        val settings = app.settings.current()
        val afterCall = inputData.getBoolean(KEY_AFTER_CALL, false)
        val retryErrors = inputData.getBoolean(KEY_RETRY_ERRORS, false)
        var needRetry = false
        try {
            if (!settings.autoProcess || !RecordingWatcher.hasAudioPermission(applicationContext)) return Result.success()
            if (afterCall) runCatching { setForeground(progressInfo("통화 녹음을 기다리는 중")) }
            // 통화 직후에는 파일이 저장소에 잡힐 때까지 최대 1분 정도 다시 찾아본다.
            val attempts = if (afterCall) 14 else 1
            var processedAny = false
            for (attempt in 0 until attempts) {
                if (attempt > 0) delay(4500)
                val candidates = findRecordings(settings.autoSince).take(5)
                for (rec in candidates) {
                    val seen = app.repository.db.recordings().byMediaId(rec.mediaId)
                    // 연결 실패로 미뤄 둔 건(retry)은 항상 다시, 다른 실패(error)는 "지금 녹음 확인"을 눌렀을 때만 다시 시도한다.
                    if (seen != null && seen.status != "retry" && !(retryErrors && seen.status == "error")) continue
                    if (afterCall && System.currentTimeMillis() - rec.dateAdded > 10 * 60 * 1000) continue
                    if (!isStable(rec)) continue
                    runCatching { setForeground(progressInfo("통화 내용을 정리하는 중")) }
                    if (!process(rec, settings.autoRegister)) needRetry = true
                    processedAny = true
                }
                if (processedAny) break
            }
            if (needRetry) RecordingWatcher.runLater(applicationContext, 2)
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        } finally {
            if (settings.autoProcess && tags.none { it.contains("PeriodicWorkRequest") }) RecordingWatcher.rearm(applicationContext)
        }
    }

    private fun findRecordings(since: Long): List<CallRecording> {
        val resolver = applicationContext.contentResolver
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.RELATIVE_PATH,
        )
        val sinceSec = (since / 1000).coerceAtLeast(0)
        val selection = "(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?) AND ${MediaStore.Audio.Media.DATE_ADDED} >= ?"
        val args = arrayOf("%Recordings/Call%", "통화 녹음%", sinceSec.toString())
        val out = mutableListOf<CallRecording>()
        resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, args, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += CallRecording(
                    mediaId = id, name = c.getString(1) ?: "", dateAdded = c.getLong(2) * 1000, size = c.getLong(3), mime = c.getString(4),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                )
            }
        }
        return out
    }

    /** 녹음이 끝나 파일이 더 자라지 않고, 미디어 저장소가 아직 쓰는 중(IS_PENDING)이 아닐 때만 처리한다. */
    private suspend fun isStable(rec: CallRecording): Boolean {
        if (rec.size <= 0) return false
        delay(5000)
        val cols = arrayOf(MediaStore.Audio.Media.SIZE, MediaStore.MediaColumns.IS_PENDING)
        val (now, pending) = applicationContext.contentResolver.query(rec.uri, cols, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) to (c.getInt(1) == 1) else -1L to false
        } ?: (-1L to false)
        return now == rec.size && !pending
    }

    /** 처리 성공이면 true. 네트워크 연결 실패면 retry 로 표시하고 false (WorkManager 가 다시 부른다). */
    private suspend fun process(rec: CallRecording, autoRegister: Boolean): Boolean {
        val app = BizApp.from(applicationContext)
        val db = app.repository.db
        db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "processing"))
        val callerHint = callerFromName(rec.name)
        val tmp = File(applicationContext.cacheDir, "rec-${rec.mediaId}")
        try {
            // 녹음 형식이 무엇이든 폰에서 풀어 WAV 조각으로 만든 뒤 올린다 (갤럭시 녹음 코덱 호환 문제 대응).
            val probe = AudioChunker.probe(applicationContext, rec.uri)
            val parts = try {
                AudioChunker.toWavChunks(applicationContext, rec.uri, tmp)
            } catch (e: Exception) {
                // 통화 직후에는 파일이 아직 다 써지지 않아 열리지 않는 일이 있다. 세 번까지 잠시 뒤 다시 읽는다.
                val previous = db.recordings().byMediaId(rec.mediaId)?.error ?: ""
                val attempt = Regex("""\((\d)/3\)""").find(previous)?.groupValues?.get(1)?.toInt() ?: 0
                if (attempt < 3) {
                    db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "retry", System.currentTimeMillis(), null, "녹음 파일이 아직 준비되지 않아 잠시 뒤 다시 읽습니다 (${attempt + 1}/3)"))
                    return false
                }
                throw IllegalStateException("녹음을 풀지 못했습니다 (${probe?.mime ?: rec.mime ?: "형식 미상"}, ${probe?.sampleRate ?: 0}Hz): ${e.message}")
            }
            if (parts.isEmpty()) {
                db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "empty", System.currentTimeMillis()))
                return true
            }
            // 조각은 순서만 지키면 되므로 동시에 올린다.
            val texts = coroutineScope {
                parts.map { f -> async { app.repository.api.transcribe(f.readBytes(), "audio/wav", callerHint) } }.awaitAll()
            }
            val transcript = texts.filter { it.isNotBlank() }.joinToString("\n").trim()
            if (transcript.length < 10) {
                db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "empty", System.currentTimeMillis()))
                return true
            }
            val header = if (callerHint != null) "[통화 전사본] 상대: $callerHint\n" else "[통화 전사본]\n"
            val fullText = header + transcript
            val res = app.repository.analyze(fullText, "call", callerHint)
            val a = res.analysis
            if (!a.isBusiness) {
                db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "skipped", System.currentTimeMillis()))
                return true
            }
            if (autoRegister) {
                val settings = app.settings.current()
                val saved = app.repository.save(ConfirmDraft.from(a, fullText, "call", settings.defaultEntity))
                db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "done", System.currentTimeMillis()))
                notify(rec.mediaId.toInt(), "등록했습니다: ${a.summary}", "일정 ${saved.calendarWritten}건, 할 일 ${saved.tasksSaved}건" + if (saved.ticketCreated) ", 장애 티켓 1건" else "", null)
            } else {
                val now = System.currentTimeMillis()
                val pendingUid = SyncEngine.newUid()
                app.repository.addPending(
                    PendingEntity(pendingUid, now, "call", fullText, json.encodeToString(Analysis.serializer(), a), callerHint, a.summary, now, true, false),
                )
                db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "done", now, pendingUid))
                val who = listOfNotNull(a.counterpart.org, a.counterpart.person).joinToString(" ").ifBlank { callerHint ?: "상대 미상" }
                notify(rec.mediaId.toInt(), "통화 정리가 준비되었습니다", "$who: ${a.summary}", pendingUid)
            }
            return true
        } catch (e: com.jpntech.bizassistant.data.remote.RelayException) {
            if (e.status == 0) {
                // 인터넷이 없거나 서버에 닿지 못함: 조용히 미뤄 두고 네트워크가 되면 다시 한다.
                db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "retry", System.currentTimeMillis(), null, "연결 대기 중: ${e.message}"))
                return false
            }
            db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "error", System.currentTimeMillis(), null, e.message))
            notify(rec.mediaId.toInt(), "통화 정리에 실패했습니다", e.message ?: "알 수 없는 오류", null)
            return true
        } catch (e: Exception) {
            db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "error", System.currentTimeMillis(), null, e.message))
            notify(rec.mediaId.toInt(), "통화 정리에 실패했습니다", e.message ?: "알 수 없는 오류", null)
            return true
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** "통화 녹음 (레디시스템) 최성탁 차장님_251123_113000.m4a" → "(레디시스템) 최성탁 차장님" */
    private fun callerFromName(name: String): String? {
        var s = name.substringBeforeLast('.')
        s = s.removePrefix("통화 녹음").removePrefix("Call recording").trim()
        val idx = s.indexOf('_')
        if (idx > 0) s = s.substring(0, idx)
        s = s.trim()
        return s.takeIf { it.isNotBlank() && !it.all { ch -> ch.isDigit() || ch == '-' || ch == ' ' } }
    }

    /** 처리하는 동안 보이는 조용한 진행 알림. 시스템이 작업을 끊지 않게 한다. */
    private fun progressInfo(text: String): ForegroundInfo {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_PROGRESS) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, "통화 정리 진행", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("사업 비서")
            .setContentText(text)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(PROGRESS_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(PROGRESS_ID, n)
    }

    private fun notify(id: Int, title: String, text: String, pendingUid: String?) {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_PENDING) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_PENDING, "통화 정리", NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (pendingUid != null) intent.putExtra(EXTRA_PENDING_ID, pendingUid)
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL_PENDING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { nm.notify(10_000 + id, n) }
    }
}
