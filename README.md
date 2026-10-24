# Shift alarm

An Android app that wakes you before your next Ubeya shift. Every night at 03:00 it reads your
bookings from the Ubeya crew portal and sets an alarm 90 minutes before the next shift.

Not affiliated with or endorsed by Ubeya. It signs in the way you would in a browser and reads
only your own bookings.

## What it does

- **Sign in once, inside the app.** Ubeya's own crew site (crew.ubeya.com) opens in a WebView and
  you sign in with your phone number and the code Ubeya texts you. The app never sees what you
  type; it keeps the session cookies, as a browser would. Google sign-in does not work inside apps.
- **Every night at 03:00** it reads your bookings and picks the next shift whose alarm time (start
  minus 90 minutes) is still ahead. At 03:00 that is normally the same day's shift.
- **Rings its own alarm.** A real alarm clock (`AlarmManager.setAlarmClock`), shown in the status
  bar and on the lock screen, with a full-screen Stop and Snooze screen, the phone's alarm tone on
  the alarm stream, and vibration. If the tone can't be opened (before the first unlock after a
  restart) it beeps instead.
- **Keeps going.** Alarms are re-armed after a restart (before the PIN is entered), an app update,
  or a clock or time-zone change. A failed check retries every 15 minutes until about 05:00, and
  the alarm already set stays in place.

## Phones that stop apps in the background

Some phones force-stop apps they consider idle, and a force stop deletes every alarm the app has
set. On a vivo Y20i (Funtouch OS 11.5, Android 12), vivo's power manager `com.vivo.abe` did exactly
that a few minutes after the screen went off, so the alarm never rang. Apps on Android's battery
allow-list were left alone. So, after installing:

1. Tap **Allow** on the app's warning card, then **Allow** on Android's "Let app always run in
   background?".
2. On vivo, check that **Autostart**, **Display on lock screen** and **Floating window** are on
   for Shift alarm. The app has a button for each page.
3. Lock the app in recent apps.

[dontkillmyapp.com](https://dontkillmyapp.com) covers other brands. The app reads Android's own
exit records and tells you if it was force-stopped, and by whom. From a computer:
`adb shell dumpsys activity exit-info com.cyber1sh.shiftalarm`.

## Build

You need JDK 17 and the Android SDK (compileSdk 37). The Gradle wrapper fetches Gradle 9.6.1.

```
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

The release APK is unsigned unless you add your own key: put `keystore/shift-alarm-release.jks`
and `keystore/keystore.properties` (`storePassword`, `keyAlias`, `keyPassword`) in the project.
Both are git-ignored. Sign every update with the same key, or Android refuses it.

On vivo phones `adb install` tends to hang. This works:

```
adb push app/build/outputs/apk/release/app-release.apk /data/local/tmp/shift-alarm.apk
adb shell pm install -r /data/local/tmp/shift-alarm.apk
```

## How it works

- `LoginActivity`: crew.ubeya.com in a WebView, where you sign in.
- `Ubeya`: `GET crew.api.ubeya.com/bookings` with the WebView's cookies and the `X-App-Name: Ubeya`
  header the web app sends. On a 401 it calls `POST auth.api.ubeya.com/user/refresh` once and
  retries, as the web app does.
- `Bookings`, `Times`, `Shift`: read the answer (`startTimeUTC` first, then the wall-clock
  `startTime`) and pick the next shift whose alarm is still ahead. These are unit-tested,
  including the night the clocks go back.
- `Alarms`: the 03:00 check is an exact allow-while-idle alarm; shift alarms use `setAlarmClock`.
  State lives in device-protected storage, so a restart re-arms everything before the first unlock.
- `CheckService`, `RingService`: foreground services for the check and the ringing.
  `RingActivity` is the full-screen Stop or Snooze screen.
- `Stops`: reads Android's process-exit records to spot force stops.
- `Vivo`: opens vivo's Autostart, Display on lock screen and Floating window pages.

## Privacy

- The app talks only to Ubeya: crew.ubeya.com, crew.api.ubeya.com and auth.api.ubeya.com.
- Your session stays in the app's WebView cookie store on the phone. Nothing is sent anywhere else.
- For debugging, the last answer from Ubeya is kept on the phone in the app's own folder
  (`Android/data/com.cyber1sh.shiftalarm/files/last-bookings.json`). It goes with an uninstall.
- No analytics, no ads and no third-party libraries.

## License

MIT. See [LICENSE](LICENSE).
