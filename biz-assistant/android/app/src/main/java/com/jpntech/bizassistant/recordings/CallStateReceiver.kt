package com.jpntech.bizassistant.recordings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * 통화가 끊기는 순간을 받아 바로 처리 작업을 깨운다.
 * 통화 중(OFFHOOK) → 대기(IDLE)로 바뀔 때만 반응한다. 같은 통화에 방송이 두 번 오므로 5초 안의 중복은 무시한다.
 */
class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val prefs = context.getSharedPreferences("call-state", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> prefs.edit().putBoolean("offhook", true).apply()
            TelephonyManager.EXTRA_STATE_IDLE -> {
                val wasOffhook = prefs.getBoolean("offhook", false)
                val last = prefs.getLong("lastIdle", 0L)
                prefs.edit().putBoolean("offhook", false).putLong("lastIdle", now).apply()
                if (wasOffhook && now - last > 5000) RecordingWatcher.runAfterCall(context)
            }
        }
    }
}
