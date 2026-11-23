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
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jpntech.bizassistant.BizApp
import com.jpntech.bizassistant.MainActivity
import com.jpntech.bizassistant.R
import com.jpntech.bizassistant.data.ConfirmDraft
import com.jpntech.bizassistant.data.db.PendingEntity
import com.jpntech.bizassistant.data.db.RecordingEntity
import com.jpntech.bizassistant.data.remote.Analysis
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.io.File

const val CHANNEL_PENDING = "pending"
const val EXTRA_PENDING_ID = "pendingId"

data class CallRecording(val mediaId: Long, val name: String, val dateAdded: Long, val size: Long, val mime: String?, val uri: Uri)

/**
 * 새 통화 녹음을 찾아 글자로 바꾸고 분석해 대기함에 넣는다.
 * 1) 미디어 저장소에서 통화 녹음 폴더의 새 파일을 찾는다
 * 2) 파일 크기가 더 변하지 않을 때(녹음이 끝났을 때)만 처리한다
 * 3) 조각으로 잘라 중계 서버에 올려 글자로 받는다
 * 4) 분석 결과를 대기함에 저장하고 알림을 띄운다 (설정에 따라 바로 등록)
 */
class RecordingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    override suspend fun doWork(): Result {
        val app = BizApp.from(applicationContext)
        val settings = app.settings.current()
        try {
            if (!settings.autoProcess || !RecordingWatcher.hasAudioPermission(applicationContext)) return Result.success()
            val candidates = findRecordings(settings.autoSince).take(5)
            for (rec in candidates) {
                val seen = app.repository.db.recordings().byMediaId(rec.mediaId)
                if (seen != null && seen.status != "retry") continue
                if (!isStable(rec)) continue
                process(rec, settings.autoRegister)
            }
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

    private suspend fun isStable(rec: CallRecording): Boolean {
        if (rec.size <= 0) return false
        delay(4000)
        val now = applicationContext.contentResolver.query(rec.uri, arrayOf(MediaStore.Audio.Media.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else -1L
        } ?: -1L
        return now == rec.size
    }

    private suspend fun process(rec: CallRecording, autoRegister: Boolean) {
        val app = BizApp.from(applicationContext)
        val db = app.repository.db
        db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "processing"))
        val callerHint = callerFromName(rec.name)
        val tmp = File(applicationContext.cacheDir, "rec-${rec.mediaId}")
        try {
            val parts = AudioChunker.split(applicationContext, rec.uri, rec.size, tmp)
            val texts = parts.map { f -> app.repository.api.transcribe(f.readBytes(), rec.mime ?: "audio/mp4", callerHint) }
            val transcript = texts.filter { it.isNotBlank() }.joinToString("\n").trim()
            if (transcript.length < 10) {
                db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "empty", System.currentTimeMillis()))
                return
            }
            val header = if (callerHint != null) "[통화 전사본] 상대: $callerHint\n" else "[통화 전사본]\n"
            val fullText = header + transcript
            val res = app.repository.analyze(fullText, "call", callerHint)
            val a = res.analysis
            if (!a.isBusiness) {
                db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "skipped", System.currentTimeMillis()))
                return
            }
            if (autoRegister) {
                val settings = app.settings.current()
                val saved = app.repository.save(ConfirmDraft.from(a, fullText, "call", settings.defaultEntity))
                db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "done", System.currentTimeMillis()))
                notify(rec.mediaId.toInt(), "등록했습니다: ${a.summary}", "일정 ${saved.calendarWritten}건, 할 일 ${saved.tasksSaved}건" + if (saved.ticketCreated) ", 장애 티켓 1건" else "", null)
            } else {
                val pendingId = db.pending().insert(
                    PendingEntity(createdAt = System.currentTimeMillis(), source = "call", rawText = fullText, analysisJson = json.encodeToString(Analysis.serializer(), a), callerHint = callerHint, title = a.summary),
                )
                db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "done", System.currentTimeMillis(), pendingId))
                val who = listOfNotNull(a.counterpart.org, a.counterpart.person).joinToString(" ").ifBlank { callerHint ?: "상대 미상" }
                notify(rec.mediaId.toInt(), "통화 정리가 준비되었습니다", "$who: ${a.summary}", pendingId)
            }
        } catch (e: Exception) {
            db.recordings().upsert(RecordingEntity(rec.mediaId, rec.name, rec.dateAdded, "error", System.currentTimeMillis(), null, e.message))
            notify(rec.mediaId.toInt(), "통화 정리에 실패했습니다", e.message ?: "알 수 없는 오류", null)
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

    private fun notify(id: Int, title: String, text: String, pendingId: Long?) {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_PENDING) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_PENDING, "통화 정리", NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (pendingId != null) intent.putExtra(EXTRA_PENDING_ID, pendingId)
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
