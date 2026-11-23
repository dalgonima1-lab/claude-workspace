package com.jpntech.bizassistant.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.jpntech.bizassistant.MainActivity
import com.jpntech.bizassistant.R
import java.util.concurrent.TimeUnit

const val CHANNEL_REMINDERS = "reminders"

fun ensureNotificationChannel(context: Context) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.getNotificationChannel(CHANNEL_REMINDERS) == null) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.notification_channel_reminders), NotificationManager.IMPORTANCE_HIGH),
        )
    }
}

/** 할 일 기한 전날 아침에 알림을 띄우는 예약. 폰이 꺼졌다 켜져도 WorkManager가 이어서 실행한다. */
class ReminderScheduler(private val context: Context) {
    fun schedule(taskId: Long, title: String, atMillis: Long) {
        val delay = atMillis - System.currentTimeMillis()
        if (delay <= 0) return
        val data = Data.Builder().putLong("taskId", taskId).putString("title", title).build()
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("task-$taskId")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        ensureNotificationChannel(ctx)
        val taskId = inputData.getLong("taskId", 0L)
        val title = inputData.getString("title") ?: "할 일"
        val open = PendingIntent.getActivity(
            ctx, taskId.toInt(), Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("내일까지: $title")
            .setContentText("사업 비서 할 일 알림")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(taskId.toInt(), n)
        return Result.success()
    }
}
