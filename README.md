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

## Updates and releases

- **In-app updates**: Settings, top card. Forget-it checks this repository's latest GitHub release once a day (switchable), shows what changed, and installs it through Android's installer. Every download is checked against the release's `SHA256SUMS`, and Android refuses an update that is not signed with the same key.
- **Delta updates**: when a release carries a `delta-<old checksum>-to-<apk>.patch` made for the APK that is installed, only that patch is downloaded (typically under 10 percent of the APK) and applied on the phone; otherwise the whole APK for the phone's architecture is downloaded.
- **Publishing**: bump `versionName` and `versionCode` in `app/build.gradle.kts`, then run `tools/release.sh` (needs `keystore.properties` with the signing key, and the `gh` CLI). It builds one APK per architecture plus a universal one, writes `SHA256SUMS`, adds patches from the previous release, and creates the release. Use `--dry-run` to build without publishing.
- **Signing key**: keep `keystore.properties` and the `.jks` file safe and out of git. Updates only install over an app signed with the same key.

## Installing from the APK

Forget-it is installed from a file, so Android adds some safety steps. They are normal for apps outside the Play Store:

1. **"Blocked by Play Protect" or "App not installed as it looks harmful"**: Android shows an "App scan recommended" screen with Scan app, Don't install app and a small Install without scanning link: tap Scan app to let Google check it, or Install without scanning to continue. If it says the app is blocked, tap More details, then Install anyway. If there is no such button, open Play Store, tap your profile picture, Play Protect, the gear icon, and turn off Scan apps with Play Protect while you install; turn it back on afterwards. The app asks for SMS access and installing updates, which Play Protect treats cautiously for apps from unknown developers. Newer Android versions are also rolling out a developer-verification check; where it blocks the install, Android offers an Install without verifying option for advanced users, and installing from a computer with `adb install forget-it-<version>-arm64-v8a.apk` works as well.
2. **Allow installs from your browser or files app** when Android asks.
3. **SMS and email permissions greyed out or the Allow button does nothing** (Android 13 and later): open Settings, Apps, Forget-it, tap the three-dot menu, choose Allow restricted settings, confirm with your fingerprint or PIN, then go back to Forget-it and allow SMS and notification access. If the menu item is missing, tap Allow in the app once first so Android shows the block, then try again. The app has the same steps under Settings, Capture.

Nothing here sends data anywhere: messages are read on the phone, and only payments are kept.

## Money and budgets

- **Money tab**: every payment and income in one ledger: found in SMS, email, Gmail and photos, or typed in with the plus button or the Spend shortcut on Overview. Filter by All, Out or In, search, tap a month arrow to look back, and tap any row to edit, recategorise or delete it.
- **Where it went**: spending by category for the month, highest first, with the change against last month and a six-month trend. Tap a category to filter the ledger to it.
- **Categories**: found payments are sorted automatically (food, groceries, transport, shopping, bills, subscriptions, EMI, health and more). Change one and Forget-it remembers it for that merchant from then on.
- **Budgets**: set an overall monthly budget and a limit for any category. You see what is left, about how much you can spend each remaining day, and where the month is heading at the current pace. Alerts arrive at 80 percent and again when a budget or limit is passed.
- **Overview** shows the month's spending, the budget bar, income and the top three categories. The old "going out" card is now labelled Fixed commitments (subscriptions, EMIs and bills).

## Category drill-down and bills for stock

- **Tap any category** on the Money tab (or the Highest spending card) to see where that money went: the total and change against last month, a day-by-day chart with weekly totals, the merchants ranked by spend, and every payment with its date. Tap a merchant to list only its payments, tap a payment to edit it, and set the category's limit from there.
- **Scan a bill for stock**: on the Stock tab the camera button reads a grocery bill and adds every item with its quantity automatically (or restocks items you already track). Lines like "2 x 1.50 3.00", "Rice 5kg" and "Milk 1L" are understood, and photographing the same bill twice does not add it twice.

## Phones, tablets and screen sizes

The layout adapts to the screen. Phones get the bottom tab bar; screens 600 dp wide and up (tablets, foldables, landscape) get a navigation rail on the left instead. Content keeps a comfortable reading width and stays centred on large screens, and the quick-action buttons shrink to fit narrow phones. Below 360 dp the tab bar shows icons only. Lists leave room under the add button so nothing is hidden behind it.
