# Handoff

## 1. Goal
Forget-it (package `app.forgetit`): offline-first Android app that tracks subscriptions, EMIs/loans, bills, household stock and money (income, spend, budgets). It captures data on its own from SMS, mail notifications, Gmail, photos and spreadsheets. Public repo https://github.com/RDK456/forget-it (branch `main`), signed releases with an in-app updater and delta patches.

## 2. Current state
- Latest release: **v1.3.1** (versionCode 8), published with per-ABI APKs, `SHA256SUMS` and delta patches from 1.3.0.
- ~225 JVM unit tests pass. Verified on the emulator: held loan with Keep/Delete strip, Delete, Keep, swipe-right hold on a subscription, Delete button on the subscription edit screen.
- Not verified: notification buttons (Keep/Delete/Undo) by tap, Gmail sign-in, a real mail-app notification, instrumented Room migration tests, a real phone install.
- Phone install is blocked by Play Protect ("App blocked to protect your device", OK only). Workaround is documented in README: turn off Play Protect scanning during install, or `adb install`.

## 3. Active files
- `app/src/main/java/app/forgetit/domain/AutoTrack.kt`: subscription and loan guessing, `isEmiTxn`, review markers.
- `app/src/main/java/app/forgetit/domain/AutoSub.kt`: subscription from a confirmation message.
- `app/src/main/java/app/forgetit/txn/AutoTracker.kt`: saves guesses (on hold), `forget`, `keep`, notification actions.
- `app/src/main/java/app/forgetit/ui/ReviewStrip.kt`, `ui/subscriptions/SubscriptionsScreen.kt`, `ui/loans/LoansScreen.kt`, `ui/edit/EditScreen.kt`, `ui/MainViewModel.kt`, `ui/Nav.kt`.
- `app/src/main/java/app/forgetit/gmail/GmailAuth.kt`: `explain()` for sign-in errors.
- `README.md` (install help, Gmail setup), `build/RELEASE_NOTES_1.3.1.md`, `tools/release.sh`, `docs/PRODUCT.md`.

## 4. Changes made (1.3.1)
- EMIs no longer become subscriptions: lender-like merchants and amounts equal to a tracked loan's EMI are excluded.
- Guesses are added **on hold** (`active=false`): history-based subscriptions and all auto-created loans. Loan notes list what was read from messages and what is a placeholder.
- Keep / Delete strip on unreviewed items (list and notification). Delete (any subscription or loan) adds the name to `autoDismissed` so it is not re-added.
- Subscriptions: swipe right = hold/resume, swipe left = delete, Delete button on the edit screen, "On hold" label.
- Gmail errors explained in-app; README has full Google Cloud steps and the release SHA-1.

## 5. Failed attempts and gotchas
- Gmail sign-in cannot work until the owner creates a Google Cloud project, an Android OAuth client (`app.forgetit` + SHA-1 `14:3B:F2:8C:1E:74:24:BC:11:C7:51:8D:51:E7:53:58:72:44:32:4F`) and adds their Gmail as a test user. `gmail.readonly` is restricted, so tokens expire about every 7 days in Testing mode. IMAP has no read-only option.
- Editing Kotlin through node scripts with `${}` inside JS template literals breaks; use the Edit tool or plain quotes.
- `git push` fails with a stale credential helper path; use `git -c credential.helper= -c credential.helper='!"/c/Program Files/GitHub CLI/gh.exe" auth git-credential' push`.
- GateGuard denies the first edit or write of each file: state importers, API, schema and the user's instruction, then retry.
- In Git Bash prefix adb calls with `MSYS_NO_PATHCONV=1` and use `//sdcard/...`.

## 6. Next steps
1. Reinstall 1.3.1 on the real phone (Play Protect workaround) and delete the two EMI subscriptions via swipe left or the edit screen's Delete.
2. Do the Google Cloud setup, then test Gmail sign-in and sync.
3. Tap-test the notification Keep/Delete/Undo buttons.
4. Consider a Review screen listing all unreviewed items, and instrumented Room migration tests.
5. Small UI fix: on the Money screen at 320dp the + button overlaps the hint text.
