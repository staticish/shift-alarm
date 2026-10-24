package com.cyber1sh.shiftalarm

import android.app.Application

class ShiftAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Also runs before the first unlock (the ringing parts are direct-boot aware), so nothing
        // here may touch credential-protected storage.
        Notices.createChannels(this)
    }
}
