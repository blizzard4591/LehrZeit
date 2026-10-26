# LehrZeit – Plan

A quick time tracker for teachers. Android, open source (GPL-3.0), distributed via F-Droid.

Status: product decisions agreed on 2026-10-25. Items under [Defaults](#defaults-veto-if-wrong) are proposals that stand unless someone objects.

## Project identity

| | |
|---|---|
| Package name (`applicationId`) | `de.janaberger.lehrzeit` |
| Copyright holder | Jana Berger |
| License | GPL-3.0-or-later |
| Source code | GitHub (public) |

## Glossary

| Term (code / EN UI) | DE UI | Meaning |
|---|---|---|
| Account | Konto | Top level of what time is booked on, e.g. "7b", "Fachschaft Mathe", "General" |
| Activity (code: `WorkActivity`) | Tätigkeit | Second level, from one shared catalog, e.g. "Correcting", "Lesson prep" |
| Combination | – | Account + optional activity, e.g. "7b › Correcting" or just "7b" |
| Entry | Eintrag | One booked time span: combination, start, end, note |
| Worklog | Protokoll | List of all entries |
| `Activity` (Android) | – | The Android component. Written in code font to avoid confusion with the work activity |

## Product decisions

**Booking model**
- Two levels: account › activity. Not deeper.
- Activities come from one shared catalog. Each account enables a subset of them, so stats can add up "Correcting" across all classes.
- Booking on an account alone (no activity) is allowed. The activity can be added later in the worklog.
- **General** is a permanent account with no activities. It is always on the home screen.

**Starter content and first launch**
- On first launch the app adds General and this starter catalog of activities, in the phone's language at that moment:

  | EN | DE |
  |---|---|
  | Teaching | Unterricht |
  | Lesson prep | Unterrichtsvorbereitung |
  | Correcting | Korrekturen |
  | Exams | Prüfungen |
  | Meetings & conferences | Konferenzen & Besprechungen |
  | Parent communication | Elternarbeit |
  | Supervision | Aufsichten |
  | Trips & events | Fahrten & Veranstaltungen |
  | Further training | Fortbildung |
  | Administration | Verwaltung |

- After that they are ordinary data: they can be renamed, archived, or deleted if they have no entries. Changing the phone language later does not rename them.
- A short, skippable **welcome setup** asks "Which classes/areas do you work for?". Every account created there gets all starter activities enabled.
- Accounts created later start with **no** activities enabled. You tick the ones that apply.

**Tracking**
- Only one timer runs at a time.
- Tapping a combination when nothing is running starts it immediately.
- Tapping a different combination while one runs shows a confirmation: "Stop A and start B?" Confirming ends A and starts B at the same instant.
- **Pause** ends the current entry and remembers the combination, so **Resume** starts a new entry for it.
- **Short entries:** an entry shorter than the minimum length (default 1 minute) is discarded when its timer is stopped, paused or switched, and the app says so. Under More › Settings this can be turned off or set from 30 seconds to 10 minutes. Entries added or edited by hand are never discarded.
- **Forgotten timer:** after a configurable time, a reminder notification asks "Still on 7b › Correcting?" with *Stop* / *Keep going*. Nothing is changed automatically.

**Notification**
- Ongoing notification showing the combination and a live timer. Android draws the timer from the start timestamp, so the app doesn't need to keep running.
- Actions: **Pause** and **Stop**, with the combination as the notification's title right above them (confirmed 2026-10-25; Android cuts off long button labels). When paused, the notification shows **Resume**.
- If the user swipes the running notification away (always possible on Android 13+), tracking continues and the notification is posted again immediately.

**Home tiles**
- Each tile is a combination.
- The General tile is always present. Then come pinned favorites, and any free slots fill with the most-used combinations.

**Worklog editing**
- Tap a time to change it with a picker. Nudge buttons −15 / −5 / +5 / +15 min sit next to start and end (−15 added so a forgotten stop is one tap).
- Optional note per entry.
- Missed entries can be added afterwards.
- If an edit makes an entry overlap another one, a dialog offers to shorten the other entry, e.g. "Overlaps 7b › Prep (14:00–14:30). Shorten it to 14:10–14:30?" with [Shorten] [Cancel].

**Stats**
- Hours per day, week and month, broken down by account (which expands to its activities) or by activity across all accounts.
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

**Distribution**
- F-Droid, so no proprietary dependencies (no Google Play Services, no Firebase).
- The app needs no internet permission.

## Android components

The app uses a single `Activity`: every screen is a Compose destination inside `MainActivity`.

| Component | Type | Purpose |
|---|---|---|
| `MainActivity` | `Activity` | Hosts all screens (Compose + Navigation). Handles deep links from the notification and widget, e.g. "open picker" |
| `SwitchConfirmActivity` | `Activity`, dialog theme, translucent | Shows the "Stop A and start B?" confirmation when a **widget** tile is tapped while another timer runs. A widget can't show a dialog itself, and opening the whole app for this would be slow |
| `TrackingActionReceiver` | BroadcastReceiver | Handles Stop, Pause, Resume and Keep going without opening the app. Also handles the notification being swiped away, by posting it again |
| `ReminderReceiver` | BroadcastReceiver + AlarmManager (inexact, no special permission) | Fires the "still working?" reminder |
| `SystemEventsReceiver` | BroadcastReceiver | Runs on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`. Posts the notification again, reschedules the reminder and refreshes the widget. Tracking state lives in the database, so it survives reboots and the process being killed |
| `TrackingWidget` / `TrackingWidgetReceiver` | Glance app widget | Shows the running timer with Stop/Pause, plus General and the favorite tiles |
| `FileProvider` | ContentProvider | Shares export and backup files with other apps |

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
- No storage permission: files are saved through the system file dialog.

## Screens

Bottom navigation: **Track · Worklog · Stats · More** (DE: Erfassen · Protokoll · Statistik · Mehr).

0. **Welcome setup** (first launch only)
   - Short explanation of accounts and activities.
   - Enter the names of your classes or areas. Each one gets all starter activities.
   - Skip button.
1. **Track (home)**
   - Status card with three states:
     - idle
     - running: combination, live timer, start time, Pause, Stop; the start can be adjusted
     - paused: Resume
   - Tile grid: General, then favorites, then most-used.
   - "All accounts…" button, which opens the picker.
   - Today's total.
   - The switch confirmation dialog.
   - A banner if notifications are blocked.
2. **Picker** (bottom sheet)
   - Accounts, each expandable to its enabled activities.
   - Tapping an account row books the account alone. Tapping an activity books the combination.
   - Search.
   - A star to pin a combination as a favorite.
   - Link to "Manage…".
3. **Worklog**
   - Entries grouped by day, newest first, under sticky date headers that show the day total.
   - The running entry is on top with a live duration.
   - "+" adds an entry. Tapping an entry opens the editor.
   - Loads the last 60 days; "Show older entries" loads 90 more each time.
4. **Edit entry** (full screen, like the other editors; also opened by tapping "Since …" on the Track screen's running card)
   - Fields: account + activity (same searchable picker), date, start and end time (each with nudge buttons), resulting duration, note.
   - A running entry shows "Running" instead of an end.
   - Delete, with an Undo snackbar instead of a confirmation.
   - Save runs validation and then the overlap check.
5. **Stats**
   - Switch between Day, Week and Month, with ‹ › to move between periods. Opens on the current week.
   - Total for the period as one large number.
   - Bar chart (one color, hairline grid at clean hour steps): days of the week in the week view, days of the month in the month view. Tapping a bar highlights it, shows its total and offers "Show day". Every bar has a screen-reader description.
   - Breakdown list, toggled between "by account" (expandable to its activities) and "by activity", largest first, each with hours, share and a thin share bar. Account colors appear only as dots next to the name.
6. **Export** (More › Export)
   - Period: this week, last week, this month (default), last month, or a custom range (date range picker).
   - Format: CSV (default), ODS or PDF, each with a one-line description.
   - A preview of what will be exported ("71 entries · 133:30 h").
   - **Save…** (system file dialog, file name suggested) or **Share…** (share sheet, also offers printing).
7. **More**: a menu that links to the screens below, plus Export.
8. **Manage accounts**
   - List with drag to reorder, and an archive section.
   - Edit an account: name, color, checklist of enabled activities, archive/unarchive.
   - General is pinned at the top and cannot be archived.
9. **Manage activities**: the shared catalog. Add, rename, reorder, archive.
10. **Backup & restore** (More › Backup & restore)
   - "Create backup…" saves `LehrZeit-Backup_2026-10-26.zip` through the system file dialog; the screen shows when the last backup was made.
   - "Restore backup…" picks a file, checks it, and asks for confirmation with a summary ("Backup from … with 2 accounts, 10 activities and 71 entries") before replacing everything.
   - Files that aren't backups, damaged backups and backups from newer app versions are refused with an explanation; the current data stays untouched.
   - The welcome setup offers "Restore a backup" too, so a new phone can start from a backup.
11. **Settings**: discard short entries (on/off and minimum length), reminder on/off and after how many hours, and a shortcut to the system's per-app language setting.
12. **About**: version, license (GPL-3.0), source code link, and licenses of the libraries used.

## Data model (Room)

```
Account          id, name, color, isGeneral, archived, sortOrder
Activity         id, name, archived, sortOrder              -- shared catalog
AccountActivity  accountId, activityId                      -- activities an account offers
Favorite         id, accountId, activityId?, sortOrder      -- pinned tiles
Entry            id, accountId, activityId?, startUtc, endUtc?, note, createdAt, updatedAt
```

- Times are stored as UTC epoch millis and shown in the device's time zone. Durations come from the stored instants, so the summer/winter time change can't distort them. Day boundaries are calculated in local time.
- `endUtc == null` means the entry is running. A partial unique index (`WHERE endUtc IS NULL`) guarantees there is never more than one running entry.
- The paused state lives in DataStore: the last combination and the time it was paused. "Paused" just means nothing is running and that combination is remembered for Resume.
- General is created on first launch. Its display name comes from string resources (Allgemein / General).
- Archived accounts and activities disappear from the picker and tiles. Their entries stay in the worklog, stats and exports.

## Export formats

- **CSV and ODS columns:** Date, Start, End, Duration (h:mm), Hours (decimal), Account, Activity, Note.
- **CSV encoding:** UTF-8 with a BOM, so Excel shows umlauts correctly, and CRLF line ends. Where the app language uses a decimal comma (German), the separator is `;` and the decimal mark `,`; otherwise `,` and `.`. Dates are ISO (2026-10-25) and times 24 h, which every spreadsheet program recognizes.
- **ODS:** a sheet "Entries" plus a sheet "Summary" with totals per account and per activity. Written directly as a zip file (the `mimetype` entry first and uncompressed, then `content.xml`, `styles.xml` and `META-INF/manifest.xml`), with no library.
- **PDF:** an A4 report built with Android's `PdfDocument`. It contains the period, the total, the per-account (with activities) and per-activity tables with hours, share and a share bar, the daily totals (bars compared with the busiest day), and the entry list with notes. Footer: creation date and "Page x of y".
- **What's included:** finished entries that start in the period. Each entry counts on its start day and is never split, so the rows always add up to the totals. (The Stats screen splits at midnight instead, so an entry crossing a period boundary can make the two differ slightly.)
- **File names:** `LehrZeit_2026-10-01_2026-10-31.csv`.
- **Backup:** a zip with one `backup.json` (kotlinx.serialization): format id, version, creation time, app version, all accounts, activities, links, favorites and entries (with their IDs), and the settings. The JSON is independent of the database schema; a version number lets later app versions read older backups, and unknown fields are ignored. Reading checks consistency (exactly one General, unique IDs, no dangling links, at most one running entry) and caps the size at 50 MB. Restoring runs in one database transaction.

## Tech stack

- Kotlin, Jetpack Compose, Material 3, Navigation Compose.
- Room (with KSP) for the database, DataStore Preferences for settings, Glance for the widget, kotlinx.serialization for backups.
- Manual dependency injection (one `AppContainer`), which is enough for an app this size.
- Charts drawn with Compose `Canvas`, with no chart library.
- Strings in `values/` (English, the fallback) and `values-de/`.
- F-Droid metadata in `fastlane/metadata/android/{en-US,de-DE}/`.

## Milestones

1. **(done 2026-10-25)** Skeleton: Gradle setup, Compose, navigation, string resources in both languages, LICENSE, README.
2. **(done 2026-10-25)** Data layer: entities, DAOs, General seeding, welcome setup, account and activity management screens.
3. **(done 2026-10-25)** Tracking: Track screen, picker, switch confirmation, notification (Stop, Pause, Resume, re-post after swipe, reboot).
4. **(done 2026-10-25)** Worklog and entry editor, with nudge buttons and overlap handling.
5. **(done 2026-10-25)** Stats.
6. **(done 2026-10-25)** Export: CSV, ODS, PDF.
7. **(done 2026-10-26)** Backup and restore.
8. Reminder.
9. Widget, plus `SwitchConfirmActivity`.
10. F-Droid metadata and a check that the build is reproducible.

Unit tests cover the time arithmetic: midnight split, the summer/winter time change, overlap resolution and most-used ranking. They also cover the exporters: CSV escaping and ODS structure.

## Defaults (veto if wrong)

- Weeks start on Monday.
- An entry running past midnight is split between both days in the stats.
- The running entry counts towards the stats up to "now". It is **not** included in exports.
- The home screen shows 8 tiles including General. "Most used" means the number of entries per combination in the last 30 days.
- A paused notification can be swiped away and stays away, because nothing is running.
- The reminder fires after 3 h by default. After *Keep going* it fires again 3 h later.
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
- Accounts and activities can be deleted only while they have no entries. After that they can only be archived.
- A new activity starts out enabled for no account, consistent with new accounts. The exception is an activity created from the picker inside an account, which is enabled for that account.
- Names are at most 40 characters. They must be unique among non-archived items, ignoring case, and an account can't be called "General"/"Allgemein". Archived items don't block a name, so next school year's "7b" can exist next to last year's archived one. Restoring a duplicate from the archive asks for a rename first.
- In the editors, *Archive* and *Restore from archive* also save the other changes on the screen. Leaving with unsaved changes asks first. An item restored from the archive goes to the end of its list.
- The German UI addresses the user with "du".
- While there is little history, free home tiles are filled with the accounts on their own (e.g. "7b"), so the home screen is useful from day one.
- Tapping the tile that is already running does nothing.
- Swiping away the paused notification also clears the paused card in the app.
- The "notifications are off" banner only appears while a timer runs or is paused, so it never shows before the first permission prompt.
- The −5/+5 min buttons on the running card can't move the start before the previous entry's end or after now. For anything else, tapping "Since …" opens the full editor.
- An entry crossing midnight appears in the worklog under both days; each day's total counts only its own part.
- A new entry starts as the last full hour (rounded to 5 minutes) on General.
- If the running entry is stopped (e.g. from the notification) while its editor is open, saving is refused instead of restarting it.

Confirmed on 2026-10-25: the overlap edge cases, leaving running entries out of exports, and restore replacing all data.
