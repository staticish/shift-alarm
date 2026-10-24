package com.cyber1sh.shiftalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings

/** Notification channels, the notifications themselves, and going foreground. */
object Notices {
    const val CH_RING = "ring"
    const val CH_RING_BACKUP = "ring_backup"
    const val CH_STATUS = "status"
    const val CH_PROBLEM = "problem"

    const val ID_RING = 1
    const val ID_CHECK = 2
    const val ID_RESULT = 3
    const val ID_PROBLEM = 4
    const val ID_MISSED = 5

    /** Where tapping a problem notification goes. */
    enum class Open { MAIN, LOGIN }

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun manager(c: Context): NotificationManager = c.getSystemService(NotificationManager::class.java)

    fun createChannels(c: Context) {
        val ring =
            NotificationChannel(CH_RING, "Alarm ringing", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "The alarm before a shift. The app plays the sound itself."
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        val backup =
            NotificationChannel(CH_RING_BACKUP, "Alarm ringing, backup sound", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Only used if the app cannot start its own alarm sound."
                setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, alarmAudio())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 700, 500, 700, 500, 700)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        val status =
            NotificationChannel(CH_STATUS, "Nightly check", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Quiet notes: the check running, and the alarm it set."
            }
        val problem =
            NotificationChannel(CH_PROBLEM, "Problems", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Signed out of Ubeya, Ubeya unreachable, or a missed alarm."
            }
        manager(c).createNotificationChannels(listOf(ring, backup, status, problem))
    }

    fun alarmAudio(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    /** The ringing notification: opens [RingActivity] full screen over the lock screen. */
    fun ringing(c: Context, ring: RingInfo, backup: Boolean = false): Notification {
        val screen = PendingIntent.getActivity(c, 10, RingActivity.intent(c, ring, backup), FLAGS)
        val stop = PendingIntent.getService(c, 11, ring.put(RingService.command(c, RingService.ACTION_STOP)), FLAGS)
        val snooze = PendingIntent.getService(c, 12, ring.put(RingService.command(c, RingService.ACTION_SNOOZE)), FLAGS)
        val n =
            Notification.Builder(c, if (backup) CH_RING_BACKUP else CH_RING)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setColor(c.getColor(R.color.accent))
                .setContentTitle(ring.headline(c))
                .setContentText(ring.detail(c))
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setShowWhen(false)
                .setFullScreenIntent(screen, true)
                .setContentIntent(screen)
                .addAction(Notification.Action.Builder(null, "Snooze ${RingService.SNOOZE_MINUTES} min", snooze).build())
                .addAction(Notification.Action.Builder(null, "Stop", stop).build())
                .build()
        // The backup repeats its sound until it is answered, like an alarm.
        if (backup) n.flags = n.flags or Notification.FLAG_INSISTENT
        return n
    }

    /** When the ring service cannot start: the notification carries the sound instead. */
    fun ringBackup(c: Context, ring: RingInfo) {
        manager(c).notify(ID_RING, ringing(c, ring, backup = true))
        Store(c).log("ring service refused to start; used the backup notification sound")
    }

    fun checking(c: Context): Notification =
        Notification.Builder(c, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setColor(c.getColor(R.color.accent))
            .setContentTitle("Checking your Ubeya shifts")
            .setOngoing(true)
            .build()

    fun result(c: Context, title: String, text: String) {
        val n =
            Notification.Builder(c, CH_STATUS)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setColor(c.getColor(R.color.accent))
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(tapTo(c, Open.MAIN))
                .setAutoCancel(true)
                .build()
        manager(c).notify(ID_RESULT, n)
    }

    fun problem(c: Context, title: String, text: String, open: Open) {
        val n =
            Notification.Builder(c, CH_PROBLEM)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setColor(c.getColor(R.color.accent))
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(tapTo(c, open))
                .setAutoCancel(true)
                .build()
        manager(c).notify(ID_PROBLEM, n)
    }

    fun clearProblem(c: Context) = manager(c).cancel(ID_PROBLEM)

    fun missed(c: Context, ring: RingInfo) {
        val n =
            Notification.Builder(c, CH_PROBLEM)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setColor(c.getColor(R.color.accent))
                .setContentTitle("Alarm stopped after ${RingService.RING_MINUTES} minutes")
                .setContentText(ring.headline(c) + ". " + ring.detail(c))
                .setContentIntent(tapTo(c, Open.MAIN))
                .setAutoCancel(true)
                .build()
        manager(c).notify(ID_MISSED, n)
    }

    private fun tapTo(c: Context, open: Open): PendingIntent {
        val target = if (open == Open.LOGIN) LoginActivity::class.java else MainActivity::class.java
        return PendingIntent.getActivity(c, 20 + open.ordinal, Intent(c, target), FLAGS)
    }

    /** startForeground with a type where Android asks for one (14+), without it before. */
    fun startForeground(s: Service, id: Int, n: Notification, type: Int) {
        if (Build.VERSION.SDK_INT >= 34) s.startForeground(id, n, type) else s.startForeground(id, n)
    }
}
