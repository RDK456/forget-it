# Forget-it: product document

## What it is
An offline-first Android app that remembers the money things you would otherwise forget: subscriptions, EMIs and loans, bills, household stock, and day-to-day spending. Data stays on the phone.

## Who it is for
People who pay for many recurring things (streaming, software, EMIs, utilities) and want one place that says what is due, what it costs per month, and where the money goes.

## Core jobs
1. **Never miss a renewal or EMI**: reminders (several per item), calendar, widget, weekly summary.
2. **Know the monthly cost**: totals per tab, Overview spend card, budgets with alerts.
3. **Capture without typing**: SMS, mail notifications, Gmail, photos and screenshots (on-device ML Kit), voice, Excel/CSV.
4. **Stay in control of automation**: guesses are added on hold, with Keep, Delete and Undo; deleted items are never re-added.

## Main areas
Overview, Money (ledger, categories, budgets), Subs, Loans, Stock, More (Bills, Calendar, Insights, Settings).

## Automation rules
- A confirmation or receipt message creates a subscription with amount, paid date, cycle, renewal date, card and cancel link; the payment goes to the ledger.
- Repeating charges and EMI patterns become subscriptions or loans **on hold** for review. Lender payments are never subscriptions.
- Switches in Settings, Capture turn each behaviour off.

## Principles
Offline by default; network only for the optional Gmail sync and update checks. Read-only Gmail scope. Only payments are kept from messages. Signed releases; updates verified against `SHA256SUMS`.

## Distribution and known limits
GitHub Releases (APK per ABI plus delta patches). Sideloaded installs may be blocked by Play Protect (see README). Gmail needs the owner's Google Cloud setup. Not built: barcode scanning, database encryption, shared household.
