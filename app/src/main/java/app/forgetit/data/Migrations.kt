package app.forgetit.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Plan B: loans. Statements mirror the exported schema 2.json. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS loan (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                "lender TEXT NOT NULL, type TEXT NOT NULL, principalMinor INTEGER NOT NULL, currency TEXT NOT NULL, " +
                "annualRatePercent TEXT NOT NULL, tenureMonths INTEGER NOT NULL, firstEmiEpochDay INTEGER NOT NULL, " +
                "emiOverrideMinor INTEGER, remindDaysBefore INTEGER NOT NULL, notes TEXT NOT NULL, active INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS loan_payment (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "loanId INTEGER NOT NULL, installmentNo INTEGER NOT NULL, paidEpochDay INTEGER NOT NULL, amountMinor INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS loan_adjustment (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "loanId INTEGER NOT NULL, epochDay INTEGER NOT NULL, kind TEXT NOT NULL, amountMinor INTEGER NOT NULL)",
        )
    }
}

/** Plan C: household stock. Statements mirror the exported schema 3.json. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS stock_item (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                "unit TEXT NOT NULL, category TEXT NOT NULL, lowThresholdMilli INTEGER NOT NULL, dailyUsageMilli INTEGER, " +
                "expiryAlertDays INTEGER NOT NULL, baselineEpochDay INTEGER NOT NULL, notes TEXT NOT NULL, active INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS stock_batch (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, itemId INTEGER NOT NULL, " +
                "quantityMilli INTEGER NOT NULL, addedEpochDay INTEGER NOT NULL, expiryEpochDay INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS stock_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, itemId INTEGER NOT NULL, " +
                "epochDay INTEGER NOT NULL, deltaMilli INTEGER NOT NULL, kind TEXT NOT NULL)",
        )
    }
}

/** Plan D: transactions parsed from SMS and shared text. Mirrors the exported schema 4.json. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS txn (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, direction TEXT NOT NULL, " +
                "amountMinor INTEGER NOT NULL, currency TEXT NOT NULL, merchant TEXT, accountHint TEXT, epochDay INTEGER NOT NULL, " +
                "source TEXT NOT NULL, status TEXT NOT NULL, snippet TEXT NOT NULL, dedupe TEXT NOT NULL)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_txn_dedupe ON txn (dedupe)")
    }
}

/** Bills, snoozed reminders, several reminder lead times, and the stock pack size, lead time, brand, store and price. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS bill (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL, " +
                "currency TEXT NOT NULL, cycle TEXT NOT NULL, customDays INTEGER, anchorEpochDay INTEGER NOT NULL, " +
                "remindDaysBefore INTEGER NOT NULL, extraRemind TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL, active INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS bill_entry (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, billId INTEGER NOT NULL, " +
                "dueEpochDay INTEGER NOT NULL, amountMinor INTEGER NOT NULL, paidEpochDay INTEGER)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS snooze (snoozeKey TEXT NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, text TEXT NOT NULL, " +
                "triggerAtMillis INTEGER NOT NULL, PRIMARY KEY(snoozeKey))",
        )
        db.execSQL("ALTER TABLE subscription ADD COLUMN extraRemind TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE loan ADD COLUMN extraRemind TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE stock_item ADD COLUMN packSizeMilli INTEGER")
        db.execSQL("ALTER TABLE stock_item ADD COLUMN leadDays INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE stock_item ADD COLUMN brand TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE stock_item ADD COLUMN store TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE stock_log ADD COLUMN priceMinor INTEGER")
    }
}

/** Pay-now links on loans and bills. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE loan ADD COLUMN payUrl TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bill ADD COLUMN payUrl TEXT NOT NULL DEFAULT ''")
    }
}

/** Which sender a payment message came from, so a noisy sender can be muted. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE txn ADD COLUMN sender TEXT NOT NULL DEFAULT ''")
    }
}
