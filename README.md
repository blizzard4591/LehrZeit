# LehrZeit

A quick time tracker for teachers, for Android.

Start a timer with a tap on an activity, and pick the class next if it's booked per class (e.g. "Correcting › 7b"). Stop or pause it from the notification or the home-screen widget. Fix start and end times in the worklog, see daily, weekly and monthly totals, and export to CSV, ODS or PDF. All data stays on the phone, and there is no account. The app only goes online if you enter a timetable link (e.g. from WebUntis), to download that timetable once a day.

> **Status:** in development and not released yet. See [PLAN.md](PLAN.md) for the screens, data model and milestones.

## Building

Requirements: JDK 17 or newer (Android Studio's bundled JDK works) and the Android SDK with platform 37.

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. Alternatively, open the folder in Android Studio and run the `app` configuration.

To hand out a test build, use `./gradlew distDebug` instead. It copies the APK to `dist/`, named with the version and commit, e.g. `dist/LehrZeit-0.2.0-debug-9eaae7b.apk`. If there were uncommitted changes, the name ends in `-dirty`. A debug APK is signed with the debug key of the computer that built it, so an APK from another computer only installs after uninstalling the old one, which deletes its data. `./gradlew distRelease` works the same way. Its APK is signed only where the release key is set up (see RELEASING.md); otherwise its name has `-unsigned`, and it can't be installed.

The release build is reproducible. [RELEASING.md](RELEASING.md) describes how to publish a version, on GitHub and on F-Droid.

## Development

Debug builds can fill the three weeks before today, and today up to now, with sample entries, which helps when trying out the worklog and stats:

```sh
adb shell am broadcast -n de.janaberger.lehrzeit.debug/de.janaberger.lehrzeit.debug.DemoDataReceiver
```

The receiver exists only in debug builds and can only be triggered through adb. Debug builds install as `de.janaberger.lehrzeit.debug` ("LehrZeit (Debug)"), next to a released version.

## Languages

German and English. The app follows the phone's language, which can also be set per app in Android's settings.

## License

Copyright © 2026 Jana Berger

LehrZeit is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).
