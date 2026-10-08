# Forget-it

Offline Android app that tracks subscriptions, EMIs/loans, bills and utilities, and household stock (milk, groceries), with photos, reminders (several per item, snooze, weekly summary), a home-screen widget and optional SMS-based transaction detection. No account is needed. Network access is used only for the optional Gmail sync; everything else, including photo reading, works offline.

## Build and run

Needs JDK 17 and the Android SDK (path in `local.properties`, forward slashes).

```
export JAVA_HOME=<your jdk 17>
./gradlew testDebugUnitTest      # 103 JVM tests
./gradlew installDebug           # installs on a connected device or emulator
```

## What is where

- `app/src/main/java/app/forgetit/domain` pure Kotlin logic with tests: renewal dates, money, totals, loan amortization, stock estimation, reminder planning, SMS parsing, CSV.
- `data` Room database (version 4, exported schemas in `app/schemas`), repositories, settings.
- `reminders` alarms, notifications, daily safety-net worker.
- `ui` Compose screens. `widget` Glance widget. `txn` SMS scanner and receiver.
- Design: `docs/superpowers/specs/2026-10-08-subtrack-design.md`; plan and status: `docs/superpowers/plans/2026-10-08-forgetit.md`.

## Notes

- Auto-scan: the first launch asks to read payment SMS; the switch lives in Settings and Transactions. New SMS are read as they arrive and the inbox is caught up daily. For an app installed outside Play, Android may require App info, three-dot menu, Allow restricted settings.
- Email alerts: Settings, Auto-scan, Email, Allow notification access. Forget-it then reads new-mail notifications from Gmail, Outlook and other mail apps on the phone.
- Emails: open the email in Gmail, Share, choose Forget-it.
- Backups: CSV export and import for subscriptions, loans and stock; photos are not included.

## Scan anything, spreadsheets and backup

- **Scan anything** (Overview, Scan): take a photo or share a screenshot into Forget-it. Text is read on the phone (ML Kit, bundled models) and sorted into subscription, EMI or loan, bill, groceries or a payment note. You review and edit every field, then it is added to the right tracker.
- **Voice**: Stock, microphone button. Say "2 litres milk" or "half kg sugar".
- **Excel and CSV**: Settings, Backup, Excel. Export everything as .xlsx (one sheet per tracker), download a template, or import an .xlsx or .csv. Columns are matched by header name in any order and nothing is added before you review it. Old .xls files must be saved as .xlsx first.
- **Full backup**: Settings, Full backup saves one zip with the database and all photos. Restore replaces everything and restarts the app.
- **Pull to refresh** on the main lists scans messages and Gmail, marks matched EMIs and rebuilds reminders. Transactions load 40 at a time as you scroll.

## Gmail setup (optional)

Gmail sync uses Google sign-in with the read-only Gmail scope. It needs a Google Cloud project that you own:

1. Create a project, enable the Gmail API, and set up the OAuth consent screen (add yourself as a test user while it is in testing).
2. Create an OAuth client of type Android with package name app.forgetit and the SHA-1 of your signing key (debug: gradlew signingReport).
3. Install the app, then Settings, Auto-scan, Gmail, Sign in.

Without this setup the Sign in button shows Google's error and the rest of the app is unaffected. Needs Google Play services on the phone.
