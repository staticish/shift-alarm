package com.cyber1sh.shiftalarm

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * Force stops Android recorded for this app. A force stop deletes every alarm the app has set and
 * keeps it from running until it is opened again, so a stopped app rings nothing. On a vivo Y20i
 * (Funtouch 11.5) vivo's power manager did exactly that minutes after the screen went off, and the
 * next alarm never rang. Apps on Android's battery allow-list were spared.
 *
 * The exit records' descriptions, as the Y20i writes them (Android 12):
 * - "stop com.cyber1sh.shiftalarm due to stop by com.vivo.abe": a force stop, alarms deleted;
 * - "stop com.cyber1sh.shiftalarm due to installPackageLI": an update, which re-arms itself;
 * - "kill background by 20792" (`am kill`), "Not important process", "empty #17": the process
 *   ended but the alarms stayed, and Android starts the app again when one fires.
 */
object Stops {
    /**
     * Logs force stops not seen before and returns the newest one's time, or 0. It is worked out
     * from the records each time, so a wrong earlier verdict does not stick.
     */
    fun record(c: Context, store: Store): Long {
        if (Build.VERSION.SDK_INT < 30) return store.lastForceStop
        val infos =
            runCatching { c.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(c.packageName, 0, 16) }
                .getOrDefault(emptyList())
                .filter { it.processName == c.packageName }
        val stops = infos.filter { it.reason == ApplicationExitInfo.REASON_USER_REQUESTED && isForceStop(it.description) }.sortedBy { it.timestamp }
        val seen = store.lastExitSeen
        for (stop in stops.filter { it.timestamp > seen }) {
            store.log("${who(stop.description)} force-stopped the app at ${Fmt.stamp(stop.timestamp)}; that deletes its alarms")
        }
        val newest = stops.lastOrNull()
        val stored = store.lastForceStop
        when {
            newest != null -> {
                store.lastForceStop = newest.timestamp
                store.lastForceStopBy = if (who(newest.description).startsWith("vivo")) "vivo" else "Android"
            }
            // A stop recorded earlier that the records now show was not one: undo it.
            stored > 0L && infos.any { it.timestamp == stored } -> {
                store.lastForceStop = 0L
                store.log("correction: the stop logged for ${Fmt.stamp(stored)} only ended the process; the alarms were kept")
            }
        }
        infos.maxOfOrNull { it.timestamp }?.let { if (it > seen) store.lastExitSeen = it }
        return store.lastForceStop
    }

    private fun isForceStop(description: String?): Boolean {
        val d = description.orEmpty()
        return d.startsWith("stop ") && "install" !in d.lowercase() && "replace" !in d.lowercase()
    }

    private fun who(description: String?): String {
        val by = description.orEmpty().substringAfter("stop by ", "").trim()
        return if (by.startsWith("com.vivo") || by.startsWith("com.iqoo")) "vivo's power manager ($by)" else by.ifEmpty { "Android" }
    }
}
