# Forget-it

Offline Android app that tracks subscriptions, EMIs/loans and household stock (milk, groceries), with photos, reminders, a home-screen widget and optional SMS-based transaction detection. No account, no internet permission.

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

- Reading SMS: grant the permission in Transactions. For an app installed outside Play, Android may require App info, three-dot menu, Allow restricted settings.
- Emails: open the email in Gmail, Share, choose Forget-it.
- Backups: CSV for subscriptions only so far; photos are not included.
