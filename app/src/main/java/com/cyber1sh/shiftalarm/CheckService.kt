package com.cyber1sh.shiftalarm

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.webkit.CookieManager

/**
 * Runs one schedule check in the foreground (a quiet notification), so vivo cannot kill it halfway
 * and so it has network access even in Doze.
 */
class CheckService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notices.startForeground(this, Notices.ID_CHECK, Notices.checking(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        val reason = intent?.getStringExtra(AlarmReceiver.EXTRA_REASON) ?: AlarmReceiver.REASON_MANUAL
        Thread {
            try {
                Checker(applicationContext).run(reason)
            } finally {
                WakeLocks.release()
                Handler(Looper.getMainLooper()).post {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
        }.start()
        return START_NOT_STICKY
    }

    companion object {
        /**
         * Starts a check. From a receiver, pass its [pending] result: if Android refuses the
         * foreground service, the check runs in the receiver's own time instead.
         */
        fun start(context: Context, reason: String, pending: BroadcastReceiver.PendingResult? = null) {
            WakeLocks.acquire(context, 3L * 60L * 1000L)
            // WebView's cookie store must first be opened on the main thread; this is it.
            CookieManager.getInstance()
            val intent = Intent(context, CheckService::class.java).putExtra(AlarmReceiver.EXTRA_REASON, reason)
            try {
                context.startForegroundService(intent)
                pending?.finish()
            } catch (e: Exception) {
                Store(context).log("$reason: foreground check refused (${e.javaClass.simpleName}); running it in the background")
                val app = context.applicationContext
                Thread {
                    try {
                        Checker(app).run(reason)
                    } finally {
                        WakeLocks.release()
                        pending?.finish()
                    }
                }.start()
            }
        }
    }
}
