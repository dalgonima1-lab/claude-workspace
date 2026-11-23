package com.jpntech.bizassistant.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jpntech.bizassistant.BizApp
import java.util.concurrent.TimeUnit

/** 15분마다, 그리고 필요할 때 서버와 기록을 맞춘다. 웹에서 고친 내용이 폰에 들어오는 길이다. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = BizApp.from(applicationContext)
        if (app.settings.current().appToken.isBlank()) return Result.success()
        val status = app.sync.syncNow()
        return if (status.lastError == null) Result.success() else Result.retry()
    }

    companion object {
        fun ensureScheduled(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "sync-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("sync-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<SyncWorker>().build())
        }
    }
}
