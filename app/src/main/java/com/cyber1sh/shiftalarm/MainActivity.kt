package com.cyber1sh.shiftalarm

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** The status screen: the next alarm, the Ubeya sign-in, what vivo must allow, and the history. */
class MainActivity : Activity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var store: Store
    private lateinit var alarmTime: TextView
    private lateinit var alarmDetail: TextView
    private lateinit var alarmTitle: TextView
    private lateinit var checkNow: Button
    private lateinit var checkStatus: TextView
    private lateinit var ubeyaState: TextView
    private lateinit var ubeyaDetail: TextView
    private lateinit var ubeyaButton: Button
    private lateinit var upcoming: LinearLayout
    private lateinit var setup: LinearLayout
    private lateinit var history: TextView
    private lateinit var warn: View
    private lateinit var warnTitle: TextView
    private lateinit var warnText: TextView

    /** One thing that may need allowing on the phone. [on] is null when no app can read it. */
    private class Step(val title: String, val on: Boolean?, val detail: String, val action: String?, val open: () -> Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = Store(this)
        alarmTime = findViewById(R.id.alarm_time)
        alarmDetail = findViewById(R.id.alarm_detail)
        alarmTitle = findViewById(R.id.alarm_title)
        checkNow = findViewById(R.id.check_now)
        checkStatus = findViewById(R.id.check_status)
        ubeyaState = findViewById(R.id.ubeya_state)
        ubeyaDetail = findViewById(R.id.ubeya_detail)
        ubeyaButton = findViewById(R.id.ubeya_button)
        upcoming = findViewById(R.id.upcoming)
        setup = findViewById(R.id.setup)
        history = findViewById(R.id.history)
        warn = findViewById(R.id.warn)
        warnTitle = findViewById(R.id.warn_title)
        warnText = findViewById(R.id.warn_text)
        findViewById<View>(R.id.warn_allow).setOnClickListener {
            if (!Vivo.openBattery(this)) Toast.makeText(this, "That page is not on this phone.", Toast.LENGTH_SHORT).show()
        }

        checkNow.setOnClickListener { CheckService.start(this, AlarmReceiver.REASON_MANUAL) }
        ubeyaButton.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
        findViewById<View>(R.id.test_ring).setOnClickListener {
            Alarms.armTestRing(this, 60_000L)
            store.log("test alarm set for a minute from now")
            Toast.makeText(this, "A test alarm rings in 1 minute. You can lock the phone.", Toast.LENGTH_LONG).show()
        }
        findViewById<View>(R.id.test_check).setOnClickListener {
            Alarms.armTestCheck(this, 120_000L)
            store.log("test run of the nightly check set for 2 minutes from now")
            Toast.makeText(this, "The nightly check runs in 2 minutes. You can close the app.", Toast.LENGTH_LONG).show()
        }
        findViewById<TextView>(R.id.version).text = "Shift alarm " + packageManager.getPackageInfo(packageName, 0).versionName

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        Stops.record(this, store)
        Alarms.rearm(this, "app opened")
        store.prefs.registerOnSharedPreferenceChangeListener(this)
        render()
        if (store.signedIn == 1 && store.lastCheckAt == 0L && !store.checking) CheckService.start(this, AlarmReceiver.REASON_MANUAL)
    }

    override fun onPause() {
        store.prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) = render()

    private fun render() {
        val now = System.currentTimeMillis()
        renderWarning(now)

        val alarm = store.alarm?.takeIf { it.alarmAt > now }
        if (alarm != null) {
            alarmTime.text = Fmt.time(this, alarm.alarmAt)
            alarmTime.setTextColor(getColor(R.color.ink))
            alarmDetail.text = "${Fmt.dayWord(this, alarm.alarmAt)}, 90 minutes before your ${Fmt.time(this, alarm.start)} shift"
            alarmTitle.text = alarm.title
            alarmTitle.visibility = if (alarm.title.isBlank()) View.GONE else View.VISIBLE
        } else {
            alarmTime.text = "None"
            alarmTime.setTextColor(getColor(R.color.muted))
            alarmDetail.text = if (store.signedIn == 1) "No upcoming shift found on Ubeya." else "Sign in to Ubeya and the app finds your next shift."
            alarmTitle.visibility = View.GONE
        }
        if (store.snoozeAt > now) alarmDetail.append("\nSnoozed until ${Fmt.time(this, store.snoozeAt)}.")

        val checking = store.checking
        checkNow.isEnabled = !checking
        checkNow.text = if (checking) "Checking Ubeya…" else "Check now"
        checkStatus.text =
            buildString {
                if (store.lastCheckAt > 0L) append(store.lastCheckText).append(" Checked ").append(Fmt.at(this@MainActivity, store.lastCheckAt)).append(". ")
                if (store.nextCheckAt > now) append("Next check ").append(Fmt.at(this@MainActivity, store.nextCheckAt)).append(".")
            }.trim()

        when (store.signedIn) {
            1 -> {
                ubeyaState.text = if (store.who.isNotBlank()) "Signed in as ${store.who}" else "Signed in"
                ubeyaDetail.text = "You won't need to sign in again unless Ubeya ends the session; the app tells you if it does."
                // No way back into Ubeya's site from here: opening it later logs the session out.
                ubeyaButton.visibility = View.GONE
            }
            0 -> {
                ubeyaButton.visibility = View.VISIBLE
                ubeyaState.text = "Signed out"
                ubeyaDetail.text = "Ubeya ended the session. Sign in again."
                ubeyaButton.text = "Sign in"
            }
            else -> {
                ubeyaButton.visibility = View.VISIBLE
                ubeyaState.text = "Not signed in"
                ubeyaDetail.text = "Use your phone number. Google sign-in does not work inside apps."
                ubeyaButton.text = "Sign in"
            }
        }

        renderUpcoming(now)
        renderSetup()
        history.text = store.history.take(15).joinToString("\n").ifEmpty { "Nothing yet." }
    }

    /**
     * The one failure that silences everything: vivo force-stopping the app, which deletes its
     * alarms. Shown until the app is on Android's battery allow-list, and for a day after any stop.
     */
    private fun renderWarning(now: Long) {
        val exempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val stoppedAt = store.lastForceStop
        val recentStop = stoppedAt > 0L && now - stoppedAt < 24L * 60L * 60L * 1000L
        warn.visibility = if (!exempt || recentStop) View.VISIBLE else View.GONE
        val by = store.lastForceStopBy.ifEmpty { "Android" }
        val stopNote = if (stoppedAt > 0L) "$by stopped this app ${Fmt.at(this, stoppedAt)}, which deleted its alarm. " else ""
        if (!exempt) {
            warnTitle.text = "Allow it to run in the background"
            warnText.text = stopNote + "Without this, vivo can stop the app at night and the alarm never rings."
            findViewById<View>(R.id.warn_allow).visibility = View.VISIBLE
        } else {
            warnTitle.text = "$by stopped this app"
            warnText.text = stopNote + "Background running is allowed now. Also lock the app in recent apps."
            findViewById<View>(R.id.warn_allow).visibility = View.GONE
        }
    }

    private fun renderUpcoming(now: Long) {
        upcoming.removeAllViews()
        val shifts = store.upcoming.filter { it.alarmAt > now }.take(6)
        if (shifts.isEmpty()) {
            upcoming.addView(layoutInflater.inflate(R.layout.row_empty, upcoming, false))
            return
        }
        for (s in shifts) {
            val row = layoutInflater.inflate(R.layout.row_shift, upcoming, false)
            val hours = if (s.end > s.start) "${Fmt.time(this, s.start)} to ${Fmt.time(this, s.end)}" else Fmt.time(this, s.start)
            row.findViewById<TextView>(R.id.shift_when).text = "${Fmt.dayWord(this, s.start)}, $hours"
            row.findViewById<TextView>(R.id.shift_more).text = listOf("Alarm ${Fmt.time(this, s.alarmAt)}", s.title).filter { it.isNotBlank() }.joinToString(" · ")
            upcoming.addView(row)
        }
    }

    private fun renderSetup() {
        val nm = getSystemService(NotificationManager::class.java)
        val power = getSystemService(PowerManager::class.java)
        val steps = ArrayList<Step>()
        steps +=
            Step("Notifications", nm.areNotificationsEnabled(), "The ringing screen and the nightly result come as notifications.", "Open") {
                Vivo.openNotifications(this)
            }
        if (Build.VERSION.SDK_INT >= 31) {
            steps += Step("Alarms and reminders", Alarms.canExact(this), "Lets the alarm ring at the exact minute.", "Open") { Vivo.openExactAlarms(this) }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            steps += Step("Full-screen alarm", nm.canUseFullScreenIntent(), "Lets the ringing screen open over the lock screen.", "Open") { Vivo.openFullScreen(this) }
        }
        steps +=
            Step("Run in the background", power.isIgnoringBatteryOptimizations(packageName), "Android's battery saver may hold back the 03:00 check. Tap Allow, then Allow again.", "Allow") {
                Vivo.openBattery(this)
            }
        steps +=
            Step("Autostart", null, "vivo's switch. Without it the app cannot start itself after the phone restarts. Shift alarm should be on.", "Open") {
                Vivo.openAutostart(this)
            }
        steps +=
            Step("Display on lock screen", null, "vivo's switch. Lets the ringing screen show over the lock screen. Shift alarm should be on.", "Open") {
                Vivo.openLockScreen(this)
            }
        steps +=
            Step("Floating window", null, "vivo's switch. Lets the ringing screen open while you use another app. Shift alarm should be on.", "Open") {
                Vivo.openFloatingWindow(this)
            }
        steps +=
            Step("Lock it in recent apps", null, "Open recent apps and pull this app's card down until a lock shows, so clearing recents does not stop it.", null) { false }

        setup.removeAllViews()
        for (step in steps) {
            val row = layoutInflater.inflate(R.layout.row_setup, setup, false)
            row.findViewById<TextView>(R.id.step_title).text = step.title
            row.findViewById<TextView>(R.id.step_detail).text = step.detail
            val state = row.findViewById<TextView>(R.id.step_state)
            when (step.on) {
                true -> {
                    state.text = "On"
                    state.setTextColor(getColor(R.color.muted))
                }
                false -> {
                    state.text = "Off"
                    state.setTextColor(getColor(R.color.accent))
                }
                null -> {
                    state.text = "Check"
                    state.setTextColor(getColor(R.color.muted))
                }
            }
            val button = row.findViewById<Button>(R.id.step_button)
            if (step.action == null || step.on == true) {
                button.visibility = View.GONE
            } else {
                button.text = step.action
                button.setOnClickListener { if (!step.open()) Toast.makeText(this, "That page is not on this phone.", Toast.LENGTH_SHORT).show() }
            }
            setup.addView(row)
        }
    }
}
