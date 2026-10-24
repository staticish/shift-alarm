package com.cyber1sh.shiftalarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

/**
 * Plays the alarm: the phone's alarm tone on the alarm stream, looped, with vibration, until Stop,
 * Snooze, or [RING_MINUTES] pass. It runs as a foreground service so the sound does not depend on
 * the ringing screen being allowed to open.
 */
class RingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var ring: RingInfo? = null
    private var player: MediaPlayer? = null
    private var beeper: AudioTrack? = null
    private var vibrator: Vibrator? = null
    private var focus: AudioFocusRequest? = null
    private var wake: PowerManager.WakeLock? = null
    private val timeout = Runnable { finish(timedOut = true) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finish(timedOut = false)
            ACTION_SNOOZE -> snooze(ring ?: RingInfo.from(intent))
            else -> start(RingInfo.from(intent))
        }
        return START_NOT_STICKY
    }

    private fun start(info: RingInfo) {
        try {
            Notices.startForeground(this, Notices.ID_RING, Notices.ringing(this, info), ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } catch (e: Exception) {
            Notices.ringBackup(this, info)
            stopSelf()
            return
        }
        silence() // a new ring replaces one already sounding
        ring = info
        ringing = true
        wake =
            getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShiftAlarm:ring").apply {
                setReferenceCounted(false)
                acquire(RING_MS + 60_000L)
            }
        play()
        vibrate()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, RING_MS)
        Store(this).log("ringing: ${info.headline(this)}")
    }

    private fun snooze(info: RingInfo) {
        Alarms.armSnooze(this, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L, info)
        Store(this).log("snoozed for $SNOOZE_MINUTES minutes")
        finish(timedOut = false)
    }

    private fun finish(timedOut: Boolean) {
        handler.removeCallbacks(timeout)
        silence()
        val info = ring
        ring = null
        ringing = false
        wake?.takeIf { it.isHeld }?.release()
        wake = null
        if (timedOut && info != null) {
            Notices.missed(this, info)
            Store(this).log("alarm rang for $RING_MINUTES minutes unanswered; stopped")
        }
        getSystemService(NotificationManager::class.java).cancel(Notices.ID_RING)
        stopForeground(STOP_FOREGROUND_REMOVE)
        sendBroadcast(Intent(ACTION_FINISHED).setPackage(packageName))
        stopSelf()
    }

    private fun play() {
        val audio = Notices.alarmAudio()
        val am = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(audio).build().also { am.requestAudioFocus(it) }
        val tone = runCatching { RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM) }.getOrNull() ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(audio)
            mp.setDataSource(this, tone)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            // Before the first unlock the tone file may be out of reach: beep instead.
            mp.release()
            beeper = Beeper.start(audio)
            Store(this).log("alarm tone unavailable (${e.javaClass.simpleName}); beeping instead")
        }
    }

    private fun vibrate() {
        val v =
            if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
        if (!v.hasVibrator()) return
        vibrator = v
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0), Notices.alarmAudio())
    }

    private fun silence() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        beeper?.runCatching { stop() }
        beeper?.release()
        beeper = null
        vibrator?.cancel()
        vibrator = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        silence()
        ringing = false
        wake?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RING = "com.cyber1sh.shiftalarm.RING_START"
        const val ACTION_STOP = "com.cyber1sh.shiftalarm.RING_STOP"
        const val ACTION_SNOOZE = "com.cyber1sh.shiftalarm.RING_SNOOZE"
        const val ACTION_FINISHED = "com.cyber1sh.shiftalarm.RING_FINISHED"

        const val RING_MINUTES = 20
        const val SNOOZE_MINUTES = 10
        private const val RING_MS = RING_MINUTES * 60_000L

        /** True while this process is sounding an alarm; the ringing screen closes when it is not. */
        @Volatile
        var ringing = false

        fun command(c: Context, action: String): Intent = Intent(c, RingService::class.java).setAction(action)

        fun start(c: Context, info: RingInfo) {
            try {
                c.startForegroundService(info.put(command(c, ACTION_RING)))
            } catch (e: Exception) {
                Notices.ringBackup(c, info)
            }
        }
    }
}
