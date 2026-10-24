package com.cyber1sh.shiftalarm

import android.content.Context
import android.os.PowerManager

/**
 * Keeps the CPU awake from the moment the 03:00 alarm is delivered until the check is done. The
 * system only holds its own wake lock while the receiver's onReceive runs.
 */
object WakeLocks {
    private var lock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(c: Context, timeoutMs: Long) {
        val held =
            lock ?: c.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShiftAlarm:check")
                .apply { setReferenceCounted(false) }
        lock = held
        held.acquire(timeoutMs)
    }

    @Synchronized
    fun release() {
        lock?.takeIf { it.isHeld }?.release()
    }
}
