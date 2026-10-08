# Forget-it: Android tracker for subscriptions, EMIs/loans and household stock (design spec)

Date: 2026-10-08 · Status: awaiting user review · Supersedes the earlier subscription-only version of this file

## 1. Purpose

A personal, offline Android app so the user never forgets a recurring payment or a household item. It tracks:
1. **Subscriptions** (Netflix, Spotify, free trials that turn paid).
2. **EMIs and loans**, with full amortization (principal, interest, balance, prepayments, payoff date).
3. **Household stock** (milk, groceries): quantity, usage, low-stock and per-batch expiry.

Any record can carry photos (a bill, receipt, loan paper, product shot).

**Success criteria**
- Adding a record takes under 30 seconds; the next charge/due/expiry date is always visible.
- Renewal, EMI, trial-end, low-stock and expiry reminders fire on a normal device and again after reboot, app update or time-zone change.
- When reminders cannot work (permission denied, battery limits) the app says so instead of failing silently.
- No account, no network permission, no backend. Data stays on the device.

## 2. Scope and delivery in three plans

Single app, delivered as three implementation plans. Each ends with a working, runnable app.
- **Plan A, Foundation + Subscriptions:** project skeleton, shared reminder system, photo attachments, 5-tab shell, Overview, Subscriptions, Calendar, Insights, Settings, CSV, biometric lock, presets, widget.
- **Plan B, Loans/EMI:** loan model, amortization engine, Loans screens, EMI reminders, loan CSV, calendar/overview/widget/insights integration.
- **Plan C, Household stock:** stock model, usage estimation, batches, Stock screens, low-stock and expiry reminders, stock CSV, calendar/overview integration.

**Out of scope (all plans):** SMS/email/bank auto-detection, cloud sync or backup, family sharing, accounts, ads, in-app purchases, bundled brand logos, barcode scanning, shopping lists, zip backup with photos, tablet layouts, localization (English only; currency formatting follows system locale).

## 3. Platform and stack

- App name **Forget-it**; package and applicationId `app.forgetit`; database file `forgetit.db`.
- Kotlin, Jetpack Compose, Material 3 (dynamic color where available), single Gradle module, version catalog.
- min SDK 26, target/compile SDK 35, JDK 17 (not yet installed on the dev machine; Android SDK is present).
- Room (storage, explicit migrations, exported schemas), DataStore Preferences, WorkManager, AlarmManager, Glance (widget), AndroidX Biometric, Navigation Compose, Coil 2.7.0 (local image loading only).
- Manual wiring through an `AppContainer`; no Hilt. No chart or calendar library (Canvas donut, plain-Compose month grid).

## 4. Architecture

Packages under `app.forgetit`:
- `domain`: pure Kotlin, no Android imports, JVM-unit-tested: subscription/renewal rules, money, cost, totals, validation, loan amortization, stock estimation, reminder planning, calendar, insights, CSV codecs.
- `data`: Room entities/DAOs/database, repositories, `SettingsStore`, `PhotoStore` (file storage).
- `reminders`: scheduler, receivers, worker, notification channels, notification health.
- `ui`: screens per feature (`overview`, `subscriptions`, `loans`, `stock`, `calendar`, `insights`, `settings`, `lock`, `photos`), shared components, theme, navigation.
- `widget`: Glance widget.

Dependency direction: `ui` → `data`/`domain`; `reminders`/`widget` → `data`/`domain`; `domain` depends on nothing. Time is always injected (`Clock`, or `today`/`now` parameters).

**One sync path.** `AppContainer` runs a single collector over all repositories + settings. On every change (save, delete, import, setting change) it rebuilds reminders and refreshes the widget. Screens never call the scheduler directly.

## 5. Shared foundations (Plan A)

**Money.** Amounts are `Long` minor units (never floating point). Fraction digits come from the currency (JPY 0, USD 2, KWD 3). Amount text input accepts digits with one `.` or `,` decimal separator, rejects everything else and too many decimals. `BigDecimal` math; rounded HALF_UP once at the end.

**Currency.** Default currency is a setting (initial value from the device locale, fallback USD). Other currencies need a manual rate (units of default currency per 1 unit), shown with the date last edited. A record whose currency has no rate is shown in its own currency, excluded from totals, and totals show "N excluded, set rates". No network calls.

**Photos.** See section 10.

**Reminder model.** See section 9.

## 6. Subscriptions

Table `subscription`:

| Column | Type | Notes |
|---|---|---|
| id | Long PK | auto |
| name | String | required, ≤ 60 chars |
| amountMinor | Long | 0 ≤ amount ≤ 100,000,000,000 |
| currency | String | valid ISO 4217 |
| cycle | enum | WEEKLY, MONTHLY, QUARTERLY, YEARLY, CUSTOM_DAYS |
| customDays | Int? | 1..3650, required iff CUSTOM_DAYS |
| startDate | LocalDate | stored as epoch day |
| category | String | Streaming, Music, Software, Gaming, News, Cloud, Fitness, Utilities, Other |
| notes, paymentMethod | String | optional |
| cancelUrl | String? | http(s) only |
| presetKey | String? | bundled preset, for avatar color |
| isTrial, trialEndsAt | Boolean, LocalDate? | trialEndsAt required when isTrial |
| remindDaysBefore | Int | 0..30; default 2, trials default 3 |
| active | Boolean | inactive: excluded from totals and reminders |

`nextRenewal` is **computed, not stored**. Rules:
- n-th charge date is computed from the anchor (start date, or `trialEndsAt` for trials and after them), never iteratively, so no drift. WEEKLY +7n days; CUSTOM_DAYS +customDays·n; MONTHLY/QUARTERLY/YEARLY add 1/3/12 months × n to the anchor, keeping the day-of-month and clamping to the last day of shorter months (31 Jan → 28/29 Feb → 31 Mar; 29 Feb yearly → 28 Feb).
- Next renewal = first occurrence on or after today (today counts); a future anchor is the next renewal.
- While `isTrial` and today ≤ `trialEndsAt`: next charge is `trialEndsAt`; trial counts as 0 in totals. After that date the trial is settled into a regular subscription anchored at `trialEndsAt` (`isTrial` cleared on app open/daily job).
- **Monthly equivalent:** WEEKLY×52/12, MONTHLY×1, QUARTERLY÷3, YEARLY÷12, CUSTOM_DAYS×(365/customDays)/12. Yearly = unrounded monthly × 12, rounded once.
- **Validation** at the repository boundary rejects invalid records with field-level errors; nothing is saved partially.

## 7. Loans and EMIs (Plan B)

Tables:
- `loan`: id, name, lender, type (HOME, CAR, PERSONAL, EDUCATION, CARD_EMI, OTHER), principalMinor, currency, annualRatePercent (decimal text, 0..100), tenureMonths (1..600), firstEmiDate, emiOverrideMinor?, remindDaysBefore (default 2), notes, active.
- `loan_payment`: id, loanId, installmentNo, paidOn, amountMinor (informational; defaults to the EMI).
- `loan_adjustment`: id, loanId, date, kind (PREPAYMENT_REDUCE_TENURE, PREPAYMENT_REDUCE_EMI, BALANCE_RESET), amountMinor (prepaid amount, or the new outstanding balance for BALANCE_RESET).

**Amortization engine (pure, deterministic):**
- Monthly rate r = annualRatePercent / 1200.
- EMI = `emiOverrideMinor` if set, else P·r·(1+r)^n / ((1+r)^n − 1) rounded HALF_UP to minor units; if r = 0, EMI = P / n rounded.
- Installment k (1-based) is due `firstEmiDate.plusMonths(k−1)` (day-of-month clamped like subscriptions).
- Per installment: interest = round(balanceBefore × r); principal = EMI − interest; the final installment (balanceBefore + interest ≤ EMI, or k = n for an unadjusted loan) pays off exactly the remaining balance plus interest.
- Adjustments apply before the first installment whose due date is after the adjustment date. A prepayment reduces the balance (capped at the balance). REDUCE_TENURE keeps the EMI (loan ends sooner); REDUCE_EMI recomputes the EMI over the remaining installments. BALANCE_RESET sets the balance to the entered statement figure and keeps the EMI (schedule length floats).
- Guards: an EMI that does not exceed the first month's interest is rejected ("EMI too low to ever repay"); schedules are capped at 1,200 rows.
- Row status: PAID if a payment exists for that installment number; otherwise OVERDUE (due date < today), DUE (due today) or UPCOMING.
- **Outstanding balance** = balance after the highest paid installment (principal if none paid). **Next due** = lowest unpaid installment (may be overdue). **Total interest remaining** = sum of interest on unpaid rows. **Payoff date** = last row's due date. A loan with every row paid shows as completed.
- Reminders target the next unpaid installment's due date. Marking it paid moves the reminder to the next one.
- Overview "monthly outgo" adds the current EMI (next unpaid row's payment) of every active unfinished loan, converted to the default currency.
- Disclaimer shown on the loan screen: figures are computed from the entered terms and can differ slightly from the lender's statement; use the balance-reset action to re-sync.

## 8. Household stock (Plan C)

Quantities are stored as `Long` milli-units (1 L = 1000), entered with up to 3 decimals.

Tables:
- `stock_item`: id, name, unit (free text; suggestions L, ml, kg, g, pcs, pack), category (Dairy, Produce, Grocery, Household, Toiletries, Other), lowThresholdMilli, dailyUsageMilli? (manual), expiryAlertDays (default 2), baselineDate, lowAlerted (Boolean), notes, active.
- `stock_batch`: id, itemId, quantityMilli (remaining), addedOn, expiry (LocalDate?).
- `stock_log`: id, itemId, date, deltaMilli (negative = used), kind (USED, RESTOCK, DISCARD, ADJUST).

**Rules:**
- Stored quantity = sum of batch quantities.
- **Usage rate/day** = the manual `dailyUsageMilli` if set; else automatic = total USED over the last 30 days ÷ days observed (min(30, days since the item's first USED log)), but only when there are at least 2 USED logs spanning at least 7 days; otherwise no rate.
- **Estimated quantity now** = max(0, stored − rate × days since `baselineDate`). `baselineDate` resets to today on every used/restock/discard/adjust action. The estimate affects only displays and alerts; stored batches change only through user actions.
- **Run-out date** = today + floor(estimated / rate) days (only when rate > 0).
- **Low** = estimated ≤ lowThreshold, or run-out date within 2 days.
- **Use(amount):** consumes from batches in order of earliest expiry (no-expiry batches last, then oldest addedOn), **skipping expired batches**; the logged amount is what was actually consumed; if the request exceeds available stock the UI says so.
- **Restock** creates a new batch (quantity, optional expiry) and logs RESTOCK. **Discard** removes a batch (e.g. expired) and logs DISCARD.
- **Alerts:** low-stock alert once per low episode (`lowAlerted` set when notified, cleared when the item is no longer low); the planner also schedules the future date when the estimate will cross the threshold. Expiry alert for each batch with quantity > 0 at (expiry − expiryAlertDays) at reminder time. Expired batches are flagged on the item and the Overview.

## 9. Reminders (shared, built in Plan A, extended by B and C)

**Model.** Each tracker's planner outputs `ReminderSpec(key, kind, entityId, eventDate, triggerAt, title, text)`. `key = "<kind>:<entityId>:<eventDate>"` (batch expiry uses the batch id as entityId). Kinds: RENEWAL, TRIAL_END, EMI_DUE, LOW_STOCK, EXPIRY. A table `reminder_log(key PK, notifiedAt)` records what was shown; a key in the log is never shown again. Planners emit at most one pending spec per (kind, entity), the first not yet logged.

**Scheduling.**
- One alarm per spec using `setAndAllowWhileIdle` (inexact; **no exact-alarm permission**); request code derived from the key.
- Trigger = event date − lead days at the user's reminder time (default 09:00). If that moment has already passed but the event is still ahead, the trigger is now + 1 minute.
- On firing, the receiver recomputes the plan from current data and shows the notification only if a spec with that key still exists (deleted/deactivated/settled records show nothing) and the key is not in `reminder_log`; then logs it and re-syncs.
- **Safety net:** a daily WorkManager job settles trials, re-syncs all alarms and refreshes the widget. This also recovers alarms dropped by OEM battery management.
- Re-sync also runs on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIMEZONE_CHANGED`, `TIME_SET`, and on every data/setting change (section 4).
- Notification channels: Renewals (default importance), Trials (high), EMI due (high), Low stock (default), Expiry (high). Tapping opens the record.
- `POST_NOTIFICATIONS` (Android 13+) is requested in context, the first time a record with reminders is saved.
- **Notification health** (Settings): notifications allowed, each channel enabled, battery optimization status, with buttons to the relevant system pages. A banner on Overview appears when notifications are blocked.

## 10. Photos (Plan A foundation, used by all trackers)

- Table `photo`: id, ownerType (SUBSCRIPTION, LOAN, STOCK_ITEM), ownerId, fileName, addedOn, sortOrder. Up to **5 photos per record**; the first is the cover thumbnail on list rows.
- Add via the system camera app (`ActivityResultContracts.TakePicture` into a `FileProvider` cache file) or the system photo picker (`PickVisualMedia`). The app declares **no CAMERA or storage permission**.
- On add, the image is decoded with sampling, downscaled so the longest side is ≤ 1280 px, saved as JPEG quality 80 to `filesDir/photos/<uuid>.jpg`, and the temp camera file is deleted. EXIF orientation is applied before saving.
- Deleting a record deletes its photo rows and files in one operation; at app start, files in `photos/` not referenced by any row are deleted (orphan sweep).
- Edit screens show a thumbnail strip with an add button; tapping opens a full-screen pager viewer with delete. Images load through Coil from local files only.
- Photos are **not** included in CSV export, so they are lost if app data is cleared (stated in Settings next to Export).
- When the biometric lock is on, the photo viewer sits behind the lock like every screen.

## 11. Navigation and screens

Bottom navigation, five tabs: **Overview, Subscriptions, Loans, Stock, More** (More = Calendar, Insights, Settings). In Plan A, Loans and Stock tabs show an "arrives in a later update" empty state; plans B and C replace them.

- **Overview:** this month's outgo (subscriptions + current EMIs, default currency), a merged "coming up in 14 days" list (renewals, EMI dues, expiring batches, low stock), and a "needs attention" block (overdue EMIs, expired batches, notifications blocked).
- **Subscriptions:** list sorted by next renewal; total card; trial badge; cover thumbnail/avatar; search; category filter; delete with undo; empty state. **Add/Edit:** preset picker or custom entry, amount, currency, cycle, start date, reminder lead, trial toggle + end date, notes, payment method, cancel URL (with "Open cancel page"), active toggle, photos.
- **Loans (B):** list with outstanding balance, progress bar, next due; **detail** with the installment table, mark paid, add prepayment, set balance from statement, totals; **Add/Edit** loan terms, photos.
- **Stock (C):** list grouped by status (expired, low, ok) with quantity, estimated quantity, run-out date, nearest expiry; **item detail** with batches, Use / Restock / Discard actions, usage history; **Add/Edit** item, photos.
- **Calendar:** month grid with colored dots (subscription charges, EMI dues, batch expiries); tapping a day lists its entries.
- **Insights:** category breakdown (Canvas donut; loans appear as a "Loans" slice), cost per day/month/year, top 3 costliest, counts of subscriptions/trials/loans.
- **Settings:** default currency + rates, reminder time, biometric lock, CSV export/import per tracker, notification health, theme.
- **Widget (Glance):** next 3 upcoming charges/dues and the month's outgo; hides amounts when the lock is on.
- **Avatars:** colored circle with the service's initial when no photo exists. About 30 bundled subscription presets supply name, category, cancel URL and avatar color.

## 12. CSV export / import

One file per tracker, UTF-8 with header row and a BOM tolerated on import, `\r\n` or `\n` line endings, ISO `yyyy-MM-dd` dates, amounts as decimal strings, via the system file pickers.
- `subscriptions.csv`: all section 6 columns except id.
- `loans.csv` (B): single file with a `record_type` column (LOAN, PAYMENT, ADJUSTMENT) and a `loan_key` column linking rows, so a restore is complete.
- `stock.csv` (C): single file with `record_type` (ITEM, BATCH, LOG) and `item_key`.
- Import validates each row with the domain rules, imports valid rows, skips invalid ones and shows a summary ("12 imported, 2 skipped" with row numbers and reasons). Import always adds; no de-duplication.
- **Formula-injection safety:** on export, any free-text cell starting with `=`, `+`, `-`, `@`, tab or CR is prefixed with `'`; on import a leading `'` followed by one of those characters is stripped. Numeric columns are never prefixed.
- Fields containing commas, quotes or newlines are quoted per RFC 4180.
- `allowBackup` is false; CSV is the backup path (without photos).

## 13. Biometric lock

Optional. `BiometricPrompt` with device-credential fallback. Locks on cold start and when returning after more than 60 s in the background. When enabled, `FLAG_SECURE` hides content in recents and blocks screenshots. If the device has no enrolled biometrics or credential, the toggle is disabled with an explanation.

## 14. Error handling

- Database/file errors surface as a snackbar with retry; nothing fails silently.
- Scheduling one record's alarm must not block the others; failures are logged.
- A corrupt CSV or unreadable image yields a clear message, never a crash.
- Permission denial degrades gracefully (notification health, photo picker needs none).
- Database migrations between plans are explicit and covered by a migration test; no destructive fallback.

## 15. Testing

- **JVM unit tests (domain):** renewal rules (every cycle, month-end, leap day, trials), money parse/format incl. JPY/KWD and comma decimals, cost and totals with and without rates, validation; loan schedule against known worked examples (including 0% rate, EMI override, last-installment payoff, each adjustment kind, EMI-too-low guard); stock estimation, FEFO use skipping expired batches, usage-rate rule, low/run-out; reminder planning and `shouldNotify` (deleted, inactive, already logged, past trigger); calendar and insights aggregation; CSV round-trip with commas, quotes, newlines, BOM, CRLF and formula-injection cells.
- **Room instrumented tests:** DAOs, cascade/orphan behavior, each migration.
- **Manual emulator verification per plan:** add/edit flows, photo add (camera + picker) and delete, reminder notifications, reboot re-schedule, permission-denied banner, widget, biometric lock, dark mode.

## 16. Risks and open points

- **OEM battery management** can delay or kill alarms; mitigated by the daily worker and notification health, not eliminated.
- **Loan figures vs. the lender's** can differ by small amounts; mitigated by EMI override and balance reset, and stated on screen.
- **Stock estimates** are only as good as the logged usage; they never alter stored quantities.
- **Photos and CSV:** photos are not backed up; a zip backup is a possible later addition.
- **Manual exchange rates** go stale; the date each was last edited is shown.
- **JDK 17 must be installed** before the first build.
- The project folder is not a git repository; Plan A's first task runs `git init`.

## 17. Changes after approval (2026-10-08, built during the beta)

- **Plan D, Transactions (added at the user request):** payment SMS are read on the device (READ_SMS/RECEIVE_SMS, offline), parsed by `SmsParser`, and kept as transactions (parsed fields plus a 160-character snippet). A Transactions screen lists them, suggests recurring charges as subscriptions and matches debits to loan EMIs. Emails: the Gmail API needs an OAuth client and an internet permission, so it is not built; instead the app accepts shared text (Gmail, Share, Forget-it) and parses it the same way. Database v4.
- **Design system:** brand palette (teal, indigo, amber, berry), Manrope bundled font, Lucide icons (`com.composables:icons-lucide:1.1.0`), outlined cards, hero card on Overview, press feedback, animated lists/donut and screen transitions. Wallpaper-derived dynamic colour was dropped on purpose.
- **Reminders:** low-stock reminders use one key per episode (the baseline date) instead of a `lowAlerted` column. A missed reminder fires one minute after the app next syncs.
- **Not built yet:** CSV for loans and stock, EMIs in the home-screen widget, Room migration instrumented tests, zip backup with photos.
