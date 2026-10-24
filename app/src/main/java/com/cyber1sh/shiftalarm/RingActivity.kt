package com.cyber1sh.shiftalarm

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * The ringing screen, shown over the lock screen by the notification's full-screen intent. It
 * holds no sound of its own: [RingService] rings, this only shows Stop and Snooze.
 */
class RingActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private var ring: RingInfo = RingInfo(RingInfo.TEST, null)

    private val finished =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = finish()
        }

    private val tick =
        object : Runnable {
            override fun run() {
                clock.text = Fmt.time(this@RingActivity, System.currentTimeMillis())
                handler.postDelayed(this, 1000L)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_ring)
        clock = findViewById(R.id.ring_clock)
        show(intent)

        findViewById<View>(R.id.ring_stop).setOnClickListener {
            startService(ring.put(RingService.command(this, RingService.ACTION_STOP)))
            finish()
        }
        findViewById<View>(R.id.ring_snooze).setOnClickListener {
            startService(ring.put(RingService.command(this, RingService.ACTION_SNOOZE)))
            finish()
        }
        val filter = IntentFilter(RingService.ACTION_FINISHED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(finished, filter, RECEIVER_NOT_EXPORTED) else registerReceiver(finished, filter)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    private fun show(intent: Intent?) {
        ring = RingInfo.from(intent)
        findViewById<TextView>(R.id.ring_kind).text = if (ring.shift == null) "Shift alarm test" else "Shift alarm"
        findViewById<TextView>(R.id.ring_headline).text = ring.headline(this)
        findViewById<TextView>(R.id.ring_detail).text = ring.detail(this)
        findViewById<TextView>(R.id.ring_snooze).text = "Snooze ${RingService.SNOOZE_MINUTES} minutes"
    }

    override fun onResume() {
        super.onResume()
        // A stale full-screen intent (the ring already ended) should not leave this screen up. The
        // backup notification rings without the service, so it is exempt.
        if (!RingService.ringing && !intent.getBooleanExtra(EXTRA_BACKUP, false)) {
            finish()
            return
        }
        handler.post(tick)
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(finished)
        super.onDestroy()
    }

    @Deprecated("Back does nothing here: an alarm is answered with Stop or Snooze.")
    override fun onBackPressed() {
        // Swallowed on purpose.
    }

    companion object {
        private const val EXTRA_BACKUP = "backup"

        fun intent(c: Context, ring: RingInfo, backup: Boolean): Intent =
            ring.put(Intent(c, RingActivity::class.java))
                .putExtra(EXTRA_BACKUP, backup)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
