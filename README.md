# LehrZeit

A quick time tracker for teachers, for Android.

Start a timer with one tap on a favorite account and activity (e.g. "7b › Correcting"). Stop or pause it from the notification. Fix start and end times in the worklog, see daily, weekly and monthly totals, and export to CSV, ODS or PDF. All data stays on the phone: the app has no internet permission and no account.

> **Status:** early development. The app does not track time yet. See [PLAN.md](PLAN.md) for the screens, data model and milestones.

## Building

Requirements: JDK 17 or newer (Android Studio's bundled JDK works) and the Android SDK with platform 37.

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`. Alternatively, open the folder in Android Studio and run the `app` configuration.

## Languages

German and English. The app follows the phone's language, which can also be set per app in Android's settings.

## License

Copyright © 2026 Jana Berger

LehrZeit is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [LICENSE](LICENSE).
