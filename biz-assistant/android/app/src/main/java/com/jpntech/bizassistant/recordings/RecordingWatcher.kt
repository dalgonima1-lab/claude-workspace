package com.jpntech.bizassistant.recordings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** 새 통화 녹음이 생기면 처리 작업을 깨운다. 콘텐츠 변경 트리거 + 15분 주기 점검 + 수동 실행. */
object RecordingWatcher {
    private const val WATCH = "recording-watch"
    private const val SWEEP = "recording-sweep"
    private const val NOW = "recording-now"

    fun hasAudioPermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun audioPermission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    /** 앱 시작이나 설정 변경 때. 이미 대기 중이면 그대로 둔다. */
    fun ensureScheduled(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniqueWork(WATCH, ExistingWorkPolicy.KEEP, watchRequest())
        wm.enqueueUniquePeriodicWork(
            SWEEP, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecordingWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    /** 작업이 끝난 뒤 다음 변경을 기다리도록 다시 건다. */
    fun rearm(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(WATCH, ExistingWorkPolicy.APPEND_OR_REPLACE, watchRequest())
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RecordingWorker>().build())
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WATCH); wm.cancelUniqueWork(SWEEP)
    }

    private fun watchRequest() = OneTimeWorkRequestBuilder<RecordingWorker>()
        .setConstraints(
            Constraints.Builder()
                .addContentUriTrigger(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true)
                .setTriggerContentUpdateDelay(12, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(90, TimeUnit.SECONDS)
                .build(),
        )
        .build()
}
