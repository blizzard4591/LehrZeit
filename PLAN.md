# LehrZeit – Plan

A quick time tracker for teachers. Android, open source (GPL-3.0), distributed via F-Droid.

Status: product decisions agreed on 2026-10-25 and revised on 2026-10-27 after the first tester's feedback (activity before class, running card). Items under [Defaults](#defaults-veto-if-wrong) are proposals that stand unless someone objects.

## Project identity

| | |
|---|---|
| Package name (`applicationId`) | `de.janaberger.lehrzeit` |
| Copyright holder | Jana Berger |
| License | GPL-3.0-or-later |
| Source code | <https://github.com/blizzard4591/LehrZeit> (public) |

## Glossary

| Term (code / EN UI) | DE UI | Meaning |
|---|---|---|
| Activity (code: `WorkActivity`) | Tätigkeit | Top level of what time is booked on, from one shared catalog, e.g. "Correcting", "Lesson prep", or the permanent "General" |
| Class (code: `SchoolClass`) | Klasse | Optional second level: a class, course or AG, e.g. "7b", "Q1" |
| Combination | – | Activity + optional class, e.g. "Correcting › 7b" or just "Correcting" |
| Entry | Eintrag | One booked time span: combination, start, end, note |
| Worklog | Protokoll | List of all entries |
| `Activity` (Android) | – | The Android component. Written in code font to avoid confusion with the work activity |

## Product decisions

**Booking model** (revised 2026-10-27, milestone 12; until then it was account › activity)
- Two levels: activity › class. Not deeper.
- Every entry has an activity. The class is optional: an entry without one shows just the activity ("Lesson prep"). The class can be added later in the worklog.
- Each activity has a **per class** switch. While it's on, every non-archived class can be chosen under the activity, including classes added later. While it's off, the activity is only booked on its own.
- Stats can add up an activity across all classes, and a class across all activities.
- **General** is a permanent activity. It is always on the home screen and can be booked per class too.

**Starter content and first launch**
- On first launch the app adds General and this starter catalog of activities, in the phone's language at that moment:

  | EN | DE | Per class |
  |---|---|---|
  | Teaching | Unterricht | on |
  | Lesson prep | Unterrichtsvorbereitung | on |
  | Correcting | Korrekturen | on |
  | Exams | Prüfungen | on |
  | Meetings & conferences | Konferenzen & Besprechungen | off |
  | Parent communication | Elternarbeit | on |
  | Supervision | Aufsichten | off |
  | Trips & events | Fahrten & Veranstaltungen | on |
  | Further training | Fortbildung | off |
  | Administration | Verwaltung | off |

- After that they are ordinary data: they can be renamed, archived, or deleted if they have no entries. Changing the phone language later does not rename them.
- A short, skippable **welcome setup** asks which classes and courses you teach. They can then be chosen under every activity whose per-class switch is on.
- An activity created later starts with its per-class switch on.

**Tracking**
- Only one timer runs at a time.
- Booking takes one tap for an activity without classes, and two for one with classes: the activity, then the class (or "Without class") in the class sheet (revised 2026-10-30, milestone 14, after tester feedback; until then the tiles were combinations).
- Tapping an activity without classes when nothing is running starts it immediately.
- Tapping a different activity without classes while one runs shows a confirmation: "Stop A and start B?" Confirming ends A and starts B at the same instant.
- Picking a class in the class sheet while another timer runs switches at once: the sheet already says "Stops A" under its title, and the pick is a second, deliberate tap.
- **Pause** ends the current entry and remembers the combination, so **Resume** starts a new entry for it.
- **Short entries:** an entry shorter than the minimum length (default 1 minute) is discarded when its timer is stopped, paused or switched, and the app says so. Under More › Settings this can be turned off or set from 30 seconds to 10 minutes. Entries added or edited by hand are never discarded.
- **Forgotten timer:** after a configurable time, a reminder notification asks "Still on Correcting › 7b?" with *Stop* / *Keep going*. Nothing is changed automatically.

**Notification**
- Ongoing notification showing the combination and a live timer. Android draws the timer from the start timestamp, so the app doesn't need to keep running.
- Actions: **Pause** and **Stop**, with the combination as the notification's title right above them (confirmed 2026-10-25; Android cuts off long button labels). When paused, the notification shows **Resume**.
- If the user swipes the running notification away (always possible on Android 13+), tracking continues and the notification is posted again immediately.

**Home tiles** (revised 2026-10-30, milestone 14; until then: General, pinned favorites and the most-used combinations)
- Each tile is an activity. Every non-archived activity has one: General first, then in the order from Manage activities, where they can be dragged.
- There are no favorites and no most-used ranking any more. The first tester found the classes under each activity in the old picker impractical: after choosing "Correcting" only its classes matter.

**Worklog editing**
- Tap a time to change it with a picker. Nudge buttons −15 / −5 / +5 / +15 min sit next to start and end (−15 added so a forgotten stop is one tap).
- Optional note per entry.
- Missed entries can be added afterwards.
- If an edit makes an entry overlap another one, a dialog offers to shorten the other entry, e.g. "Overlaps Lesson prep › 7b (14:00–14:30). Shorten it to 14:10–14:30?" with [Shorten] [Cancel].

**Stats**
- Hours per day, week and month, broken down by activity (which expands to its classes) or by class across all activities.
- No target hours or overtime balance in v1.

**Export**
- Formats: CSV, ODS (spreadsheet) and PDF (report).

**Backup**
- Manual backup file export and import. This works on phones without Google services.

**Platform**
- UI in German and English, following the phone language.
- Minimum Android 10 (API 29). Compile and target SDK 37.

**Extras in v1**
- Home-screen widget.

**Timetable link (optional, milestone 13, decided 2026-10-27)**
- Under More › Settings, a timetable link can be entered: any https link to an iCalendar file, e.g. the personal iCal link from WebUntis. `webcal://` links are accepted and fetched over https.
- LehrZeit downloads it right away and then at the update times (since milestone 15), while there is a network connection, and stores the lessons locally. In this step it only shows the result: how many lessons are stored, for which period, and when the last update was. Booking teaching time from it comes later.
- The link works like a password, so it isn't part of the backup file, Android's own backup leaves it out, and the app shows only its host (e.g. "schule.webuntis.com").

**Update times (milestone 15, decided 2026-10-30)**
- Cancellations and substitutions are planned in the morning, after teachers have called in sick (07:30–07:40). So the timetable is downloaded at set times instead of once a day: by default at 07:00 (the day as planned), 07:55 and 08:30 (after the changes), and 17:00 (the day's last changes), Monday to Friday.
- Settings › Automatic updates: the times are a free list (add, change, remove), and the days are chosen with weekday buttons. Without times or days, the timetable is only downloaded when "Update" is tapped.
- Roughly on time, without the "Alarms & reminders" permission: an update never starts before its time, usually within minutes after it, and Android may delay it while the phone has been lying unused for a long time. It waits for a network connection.
- Only the next update is planned; when it has run, it plans the one after it. So the times follow the clock across summer and winter time, and an update that was very late doesn't run twice.
- The times are settings, so they are part of the backup (unlike the link).

**Distribution**
- F-Droid, so no proprietary dependencies (no Google Play Services, no Firebase).
- The app goes online only to download the timetable link the user entered, and sends nothing else. Without a link it never connects. The store texts and the README say so. F-Droid will probably mark the app with the "NonFreeNet" anti-feature, because WebUntis is a proprietary service.

## Android components

The app uses a single `Activity`: every screen is a Compose destination inside `MainActivity`.

| Component | Type | Purpose |
|---|---|---|
| `MainActivity` | `Activity` | Hosts all screens (Compose + Navigation). Tapping the notification or the widget's status bar opens it |
| `WidgetDialogActivity` | `Activity`, translucent | Shows the class sheet when a **widget** tile for an activity with classes is tapped, and the "Stop A and start B?" confirmation when another tile is tapped while a timer runs. A widget can't show a dialog itself, and opening the whole app for this would be slow (until milestone 14 it was `SwitchConfirmActivity`, with only the confirmation) |
| `TrackingActionReceiver` | BroadcastReceiver | Handles Stop, Pause, Resume and Keep going without opening the app, the notifications being swiped away (the timer one is posted again), and the reminder alarm |
| `AlarmReminderScheduler` | AlarmManager (`setWindow`, 10-minute window, no special permission) | Wakes `TrackingActionReceiver` when the "still working?" reminder is due. A plain inexact alarm could be up to 75 % of the waiting time late; in deep sleep (Doze) the alarm still waits until the phone is used |
| `SystemEventsReceiver` | BroadcastReceiver | Runs on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. Posts the notification again, reschedules the reminder and refreshes the widget. Tracking state lives in the database, so it survives reboots and the process being killed |
| `TrackingWidget` / `TrackingWidgetReceiver` | Glance app widget | Shows the running timer with Pause/Stop (or Resume), plus as many of the Track screen's tiles as fit. The timer is a system chronometer, so the widget needs no updates while it counts. `TrackingController` updates it on every change; edits in the app (names, order, archiving) reach it through an observer that runs while the app process lives |
| `FileProvider` | ContentProvider | Shares export and backup files with other apps |
| `TimetableSyncWorker` | WorkManager worker | Downloads the timetable link: once right after it was entered or "Update" was tapped, and at the update times, each time only with a network connection. A planned run plans the next one when it's done |

**No foreground service.**
- The notification timer is drawn by the system.
- On Android 13+ even a foreground service's notification can be swiped away, so a service would add nothing.
- Leaving it out also avoids Android's restrictions on foreground service types.

**Notification channels:**
- "Tracking": default importance with sound and vibration off. Low importance would be quieter in the list, but it hides the status-bar icon on current Android versions.
- "Reminders": default importance.

**Permissions:**
- `POST_NOTIFICATIONS`, requested on the first timer start (Android 13+).
- `RECEIVE_BOOT_COMPLETED`.
- `INTERNET`, only used for the optional timetable link.
- No storage permission: files are saved through the system file dialog.

## Screens

Bottom navigation: **Track · Worklog · Stats · More** (DE: Erfassen · Protokoll · Statistik · Mehr).

0. **Welcome setup** (first launch only)
   - Short explanation of activities and classes.
   - Enter the names of your classes and courses.
   - Skip button.
1. **Track (home)**
   - Status card with three states:
     - idle
     - running: combination, live timer, the start time with −5 / +5 min buttons on either side ("Start [−5] 10:15 [+5]"), the note, Pause, Stop
     - paused: Resume
   - Tile grid: one tile per activity, General first, then in the order from Manage activities. A running activity's tile is highlighted and shows the class.
   - Tapping an activity with classes opens the class sheet.
   - A "Manage activities" link below the tiles.
   - Today's total.
   - The switch confirmation dialog.
   - A banner if notifications are blocked.
2. **Class sheet** (bottom sheet, also over the home screen for the widget)
   - The activity as the title; while a timer runs, "Stops A" below it.
   - "Without class", then the non-archived classes in their order from Manage classes. The running class has a check mark; picking it changes nothing.
   - Shown only for an activity whose per-class switch is on, and only if there are classes; otherwise the tile books the activity on its own.
3. **Worklog**
   - Entries grouped by day, newest first, under sticky date headers that show the day total.
   - The running entry is on top with a live duration.
   - "+" adds an entry. Tapping an entry opens the editor.
   - Loads the last 60 days; "Show older entries" loads 90 more each time.
4. **Edit entry** (full screen, like the other editors; also opened by tapping "Since …" on the Track screen's running card)
   - Fields: activity + class (a picker in the same two steps as on the Track screen: the activities, then the activity's classes with a back arrow), date, start and end time (each with nudge buttons), resulting duration, note.
   - A running entry shows "Running" instead of an end.
   - Delete, with an Undo snackbar instead of a confirmation.
   - Save runs validation and then the overlap check.
5. **Stats**
   - Switch between Day, Week and Month, with ‹ › to move between periods. Opens on the current week.
   - Total for the period as one large number.
   - Bar chart (one color, hairline grid at clean hour steps): days of the week in the week view, days of the month in the month view. Tapping a bar highlights it, shows its total and offers "Show day". Every bar has a screen-reader description.
   - Breakdown list, toggled between "by activity" (expandable to its classes) and "by class", largest first, each with hours, share and a thin share bar. Activity colors appear only as dots next to the name.
6. **Export** (More › Export)
   - Period: this week, last week, this month (default), last month, or a custom range (date range picker).
   - Format: CSV (default), ODS or PDF, each with a one-line description.
   - A preview of what will be exported ("71 entries · 133:30 h").
   - **Save…** (system file dialog, file name suggested) or **Share…** (share sheet, also offers printing).
7. **More**: a menu that links to the screens below, plus Export.
8. **Manage activities**: the shared catalog.
   - List with drag to reorder, and an archive section.
   - Edit an activity: name, color, per-class switch, archive/unarchive.
   - General is pinned at the top and cannot be archived.
9. **Manage classes**: add, rename, reorder, archive.
10. **Backup & restore** (More › Backup & restore)
   - "Create backup…" saves `LehrZeit-Backup_2026-10-26.zip` through the system file dialog; the screen shows when the last backup was made.
   - "Restore backup…" picks a file, checks it, and asks for confirmation with a summary ("Backup from … with 10 activities, 2 classes and 71 entries") before replacing everything.
   - Files that aren't backups, damaged backups and backups from newer app versions are refused with an explanation; the current data stays untouched.
   - The welcome setup offers "Restore a backup" too, so a new phone can start from a backup.
11. **Settings**: discard short entries (on/off and minimum length), reminder on/off and after how many hours, the timetable link with its status ("386 lessons stored, 2 Nov 2026 – 16 Mar 2027", last update, "Update") and its automatic updates ("Mon–Fri at 07:00, 07:55, 08:30, 17:00"), and a shortcut to the system's per-app language setting.
12. **About**: version, license (GPL-3.0), source code link, and licenses of the libraries used.

## Data model (Room)

```
Activity         id, name, color, isGeneral, perClass, archived, sortOrder   -- shared catalog
SchoolClass      id, name, archived, sortOrder
Entry            id, activityId, classId?, startUtc, endUtc?, note, createdAt, updatedAt
Lesson           uid, startUtc, endUtc, summary, description, location   -- from the timetable link
```

- Lessons are a local copy of the timetable link. Each update replaces the stored lessons from today on (or from the file's first day, if that is earlier) with the file's content. Older lessons are kept even after they drop out of the file, because WebUntis no longer provides them.
- Milestone 13 raises the database to version 2 with an automatic migration that adds the lesson table, so installing over a milestone-12 build keeps the data.
- Milestone 14 raises it to version 3, whose automatic migration drops the favorite table (pinned tiles).

- No version has been released, so milestone 12 starts the database over at version 1 instead of migrating the old account › activity data. Testers uninstall the old build first. Backups made before the change are refused.

- Times are stored as UTC epoch millis and shown in the device's time zone. Durations come from the stored instants, so the summer/winter time change can't distort them. Day boundaries are calculated in local time.
- `endUtc == null` means the entry is running. A partial unique index (`WHERE endUtc IS NULL`) guarantees there is never more than one running entry.
- The paused state lives in DataStore: the last combination and the time it was paused. "Paused" just means nothing is running and that combination is remembered for Resume.
- General is created on first launch. Its display name comes from string resources (Allgemein / General).
- Archived activities and classes disappear from the picker and tiles. Their entries stay in the worklog, stats and exports.

## Export formats

- **CSV and ODS columns:** Date, Start, End, Duration (h:mm), Hours (decimal), Activity, Class, Note.
- **CSV encoding:** UTF-8 with a BOM, so Excel shows umlauts correctly, and CRLF line ends. Where the app language uses a decimal comma (German), the separator is `;` and the decimal mark `,`; otherwise `,` and `.`. Dates are ISO (2026-10-25) and times 24 h, which every spreadsheet program recognizes.
- **ODS:** a sheet "Entries" plus a sheet "Summary" with totals per activity and per class. Written directly as a zip file (the `mimetype` entry first and uncompressed, then `content.xml`, `styles.xml` and `META-INF/manifest.xml`), with no library.
- **PDF:** an A4 report built with Android's `PdfDocument`. It contains the period, the total, the per-activity (with classes) and per-class tables with hours, share and a share bar, the daily totals (bars compared with the busiest day), and the entry list with notes. Footer: creation date and "Page x of y".
- **What's included:** finished entries that start in the period. Each entry counts on its start day and is never split, so the rows always add up to the totals. (The Stats screen splits at midnight instead, so an entry crossing a period boundary can make the two differ slightly.)
- **File names:** `LehrZeit_2026-10-01_2026-10-31.csv`.
- **Backup:** a zip with one `backup.json` (kotlinx.serialization): format id, version, creation time, app version, all activities, classes and entries (with their IDs), and the settings. Version 2 also had the pinned tiles; such backups still restore, and the pins are skipped. The JSON is independent of the database schema; a version number lets later app versions read older backups, and unknown fields are ignored. Reading checks consistency (exactly one General, unique IDs, no dangling links, at most one running entry) and caps the size at 50 MB. Restoring runs in one database transaction.

## Tech stack

- Kotlin, Jetpack Compose, Material 3, Navigation Compose.
- Room (with KSP) for the database, DataStore Preferences for settings, Glance for the widget, kotlinx.serialization for backups.
- Manual dependency injection (one `AppContainer`), which is enough for an app this size.
- Charts drawn with Compose `Canvas`, with no chart library.
- Strings in `values/` (English, the fallback) and `values-de/`.
- F-Droid metadata in `fastlane/metadata/android/{en-US,de-DE}/`: title, short and full description, changelog per `versionCode`, icon, feature graphic and six phone screenshots per language. [RELEASING.md](RELEASING.md) describes the release steps and has a draft of the F-Droid recipe.

## Milestones

1. **(done 2026-10-25)** Skeleton: Gradle setup, Compose, navigation, string resources in both languages, LICENSE, README.
2. **(done 2026-10-25)** Data layer: entities, DAOs, General seeding, welcome setup, account and activity management screens.
3. **(done 2026-10-25)** Tracking: Track screen, picker, switch confirmation, notification (Stop, Pause, Resume, re-post after swipe, reboot).
4. **(done 2026-10-25)** Worklog and entry editor, with nudge buttons and overlap handling.
5. **(done 2026-10-25)** Stats.
6. **(done 2026-10-25)** Export: CSV, ODS, PDF.
7. **(done 2026-10-26)** Backup and restore.
8. **(done 2026-10-26)** Reminder.
9. **(done 2026-10-26)** Widget, plus `SwitchConfirmActivity`.
10. **(done 2026-10-26)** F-Droid metadata and a check that the build is reproducible.
    - The unsigned release APK is byte-identical across Windows with JDK 25 (two different folders, CRLF and LF checkouts) and Linux with JDK 17. Line endings don't matter because the Kotlin compiler normalizes them. The APK's only build-specific content is the git commit in `version-control-info.textproto`.
    - Still open before a first release: the About screen (screen 12: version, license, source link, library licenses) and the Settings shortcut to the per-app language were never assigned to a milestone. The About screen's source link will be <https://github.com/blizzard4591/LehrZeit>.
11. **(done 2026-10-27)** Running card, after the first tester's feedback:
    - The −5 / +5 min buttons sit on either side of the start time they change, so their direction is visible. "Since 10:15" becomes "Start".
    - A button is greyed out when it can't move the start.
    - −5 also moves the previous entry's end back if it's in the way, e.g. right after a switch, with Undo.
    - A note for the running entry can be added and edited on the card, and shows there.
12. **(done 2026-10-27)** Activity › class instead of account › activity (see [Booking model](#product-decisions)), with store texts and screenshots updated. Backups from before this change are refused with their own message.
13. **(done 2026-10-27)** Timetable link (see [Product decisions](#product-decisions)): enter an iCal link, download it daily, store the lessons, show the status in Settings. Store texts and README updated for the internet access. Checked with the first tester's real WebUntis link, in the debug and the minified release build.
14. **(done 2026-10-30)** Activities first, then the class, after the first tester's feedback ("when I tap Correcting I don't need the other activities any more, but its classes"; see [Booking model](#product-decisions) and [Home tiles](#product-decisions)):
    - The Track screen and the widget show one tile per activity, in the user's order. Favorites, the most-used ranking and the "All activities…" picker are gone.
    - An activity with classes opens the class sheet: "Without class", then the classes. While a timer runs, picking a class switches at once.
    - The entry editor's picker uses the same two steps instead of expandable rows, and has no search any more.
    - Database version 3 drops the favorite table; backup version 3 leaves the pins out, and version-2 backups still restore. Store texts and screenshots updated.
15. **(done 2026-10-30)** Update times for the timetable, after looking at how cancellations show up in the file (see [Update times](#product-decisions)):
    - Downloads at 07:00, 07:55, 08:30 and 17:00 on Monday to Friday by default; times and days can be changed in Settings.
    - Only the next update is planned with WorkManager; each plans the following one. Version 0.2.0's daily job is cancelled.
    - The times are part of the backup. Older backups get the default times.

Later: book teaching time from the WebUntis timetable (see [Timetable from WebUntis](#timetable-from-webuntis)). Clocking in by location and a timetable typed into the app that books automatically every week were ruled out: the first needs location access in the background, and the second goes wrong on every holiday, cancellation or short day.

### Timetable from WebUntis

Findings from real exports of the first tester's timetable (downloaded 2026-10-27 and 2026-10-30, both during the autumn holidays):

- **The link:** WebUntis offers a personal iCalendar subscription link (`https://<school>.webuntis.com/WebUntis/ical_export?school=…&id=…&elemId=…&token=…`). Whoever has the link can read the timetable, so it has to be treated like a password.
- **Range:** a window of 140 days (20 weeks) from the day of the download. The export started with the first school day after the holidays and ended exactly 140 days after the download, in the middle of a week. Lessons from before the holidays weren't in it, so the past is missing, apart from at most a few days. The second download again started with the first school day after the holidays and ended 140 days after its own download. *Still to check:* re-download after the first school day and see whether that day's lessons are still included.
- **Consequence:** past lessons can't be fetched afterwards. Whatever LehrZeit wants to book has to be kept while it is still in the file, i.e. downloaded at least daily or ahead of time.
- **Refresh:** the file asks calendar apps to reload it once a day (`REFRESH-INTERVAL: P1D`), and the server forbids caching it. It is generated on every download.
- **Holidays and other free days** have no lessons in the file, so they need no handling of their own.
- **Events:** every lesson is its own event, with no repeat rules. Start and end are in Europe/Berlin time.
  - `SUMMARY`: the subject, with the course group in the upper school (`BI`, `S1 G2`). Some entries are special, such as `BER` (standby duty, "Bereitschaft") or `V_Std.DoBe` (probably a substitution or team-teaching lesson).
  - `DESCRIPTION`: the class or year group, followed by the teacher's abbreviation (`08.2 XYZ`, `Q1 XYZ`).
  - `LOCATION`: the room, or the area for a break supervision.
  - `UID`: an ID per lesson, stable across downloads. All 385 lessons that were in both downloads kept their UID and all their content. It has the form `<lesson>-<period>`: the first number stays the same for a weekly lesson, the second stands for the period on that day. A double lesson is one event with both periods (`<lesson>-<period>-<period>`).
  - `STATUS` is always `CONFIRMED`, and no other property marks a lesson as cancelled or replaced. A one-off entry (probably an exam supervision) can overlap the regular lesson at the same time.
  - `DTSTAMP` is the time of the download for every event, so it doesn't show when a lesson changed.
- **Lengths:** lessons are 45 minutes and double lessons 95 (including the 5-minute break). Entries without a subject, of 30 or 5 minutes at break times, are probably break supervisions.
- **Cancelled lessons disappear** from the file instead of being marked. A weekly lesson that WebUntis shows as cancelled ("entfällt") on 10 Nov was missing from both downloads, while it is there in every other week. Between the two downloads, one more lesson (on 22 Dec, no lessons after noon before the winter break) was cancelled and disappeared, and nothing took its place. The file doesn't say why a lesson is missing, so a cancellation looks the same as a lesson that was moved away.
- **Exams and other changes within a lesson** aren't marked either. An exam that replaces the first hour of a double lesson (on 12 Nov) keeps the lesson's subject and class; only the room differs. The double lesson then becomes two single events, each with its own room and its own period in the UID. For booking, that is still the same lesson.
- **For LehrZeit:** each update replaces the stored lessons from today on, so a lesson cancelled before an update drops out with it. A lesson cancelled after the day's last update, e.g. in the morning after an early download, stays stored, unless a later download still contains that day. Whether a day stays in the file once it is past is the open point under *Range*.

Unit tests cover the time arithmetic: midnight split, the summer/winter time change, and overlap resolution. They also cover the exporters: CSV escaping and ODS structure.

## Defaults (veto if wrong)

- Weeks start on Monday.
- An entry running past midnight is split between both days in the stats.
- The running entry counts towards the stats up to "now". It is **not** included in exports.
- A paused notification can be swiped away and stays away, because nothing is running.
- The reminder fires after 3 h by default (choices: 1–12 h). After *Keep going* it fires again that long later; swiping the reminder away counts as *Keep going*. Without either, there is no second reminder for the same entry. Switching to another combination starts counting afresh.
- The reminder notification has its own group, so Android doesn't bundle it with the timer notification and hide its buttons.
- The start of the running entry can be adjusted, e.g. "I actually started 10 min ago".
- Entries cannot end in the future, and the end must be after the start.
- Overlap edge cases:
  - If the edited entry fully covers another, the dialog offers to *delete* the other entry.
  - If the edited entry lies entirely inside another, saving is blocked, because fixing it would need splitting, which is not in v1.
- Restoring a backup **replaces** all data (after confirmation). It does not merge.
- A restore also brings back the settings, completes the welcome setup, and keeps a timer that was running when the backup was made running.
- Debug builds install as `de.janaberger.lehrzeit.debug` ("LehrZeit (Debug)"), so a test install and the released app can live side by side.
- General cannot be renamed. Its name follows the app language.
- License wording is "GPL-3.0-or-later", the FSF's standard form, rather than "GPL-3.0-only".
- App colors follow the wallpaper on Android 12+ (Material You). Android 10/11 use a fixed green palette that matches the icon.
- Android's own backup (`allowBackup`) stays enabled as a bonus. It only does anything if the user has turned on backup on the phone.
- Activities and classes can be deleted only while they have no entries. After that they can only be archived.
- Colors belong to activities (tile edge, dots in the stats). Classes have none.
- On the Track screen's tiles, long activity names get two lines and are hyphenated ("Unterrichtsvorberei-tung"). The widget can't hyphenate, so there they stay on one line and end with "…".
- In the stats' "by class" view, entries without a class are listed as "Without class".
- Names are at most 40 characters. They must be unique among non-archived items, ignoring case, and neither an activity nor a class can be called "General"/"Allgemein". Archived items don't block a name, so next school year's "7b" can exist next to last year's archived one. Restoring a duplicate from the archive asks for a rename first.
- In the editors, *Archive* and *Restore from archive* also save the other changes on the screen. Leaving with unsaved changes asks first. An item restored from the archive goes to the end of its list.
- The German UI addresses the user with "du".
- Tapping the running activity's tile does nothing if it has no classes; with classes, it opens the class sheet, so the class can be changed.
- The widget shows the same activity tiles as the Track screen, as many as fit its size. It starts at 4 × 2 cells and can be made as small as one row, which shows only the timer.
- In the widget, a tile without classes starts at once while nothing runs; while another timer runs, the "Stop A and start B?" question appears over the home screen. A tile with classes opens the class sheet there.
- When a timer stopped or paused from the notification or the widget was too short to keep, a short toast says so (in the app it's the snackbar).
- On Android 10/11 the widget has square corners, and the widget picker shows the app icon instead of a preview.
- Starting a timer from the widget doesn't ask for the notification permission (a widget can't); the widget shows the timer, and the app asks on the first start there.
- Swiping away the paused notification also clears the paused card in the app.
- The "notifications are off" banner only appears while a timer runs or is paused, so it never shows before the first permission prompt.
- The −5/+5 min buttons on the running card only work when the full 5 minutes fit, so they never move by less. +5 can't move the start past now, so it is greyed out during the first 5 minutes. −5 shortens the previous entry if it's in the way, but leaves it at least 1 minute long; otherwise it is greyed out. For anything else, tapping the start time opens the full editor.
- The running entry's note is edited in a small dialog opened from the running card.
- An entry crossing midnight appears in the worklog under both days; each day's total counts only its own part.
- A new entry starts as the last full hour (rounded to 5 minutes) on General.
- If the running entry is stopped (e.g. from the notification) while its editor is open, saving is refused instead of restarting it.
- Timetable link:
  - A failed download leaves the stored lessons as they are. Settings shows the problem and when the last successful update was; there is no notification. After a network error, an update tries again a few times within minutes; then it waits for the next update time.
  - Files over 5 MB are refused.
  - All-day events are ignored (a timetable has none), and so are events marked as cancelled. Repeat rules aren't expanded; WebUntis doesn't use them.
  - Removing the link also deletes the stored lessons, including past ones.
  - Restoring a backup leaves the timetable link and its lessons as they are, but brings back the update times.
  - A new update time in the time picker starts at 12:00. The same time can't be added twice.
  - The day buttons and the summary in Settings follow the app language; three or more days in a row are shown as a range ("Mon–Fri").
  - After saving a new link, the field starts empty when it is opened again, so the token isn't shown on screen.

Confirmed on 2026-10-25: the overlap edge cases, leaving running entries out of exports, and restore replacing all data.
