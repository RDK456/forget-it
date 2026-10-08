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
