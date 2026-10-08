package app.forgetit.txn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import app.forgetit.data.TxnRepository
import app.forgetit.domain.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object SmsScanner {
    private const val MAX_MESSAGES = 5000

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Reads the SMS inbox on this phone, keeps only payments. Returns the number of new transactions, or -1 without permission. */
    suspend fun scanInbox(context: Context, repo: TxnRepository, since: LocalDate, defaultCurrency: String, zone: ZoneId, muted: Set<String> = emptySet(), onNew: suspend (String, LocalDate) -> Unit = { _, _ -> }): Int =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext -1
            val sinceMs = since.atStartOfDay(zone).toInstant().toEpochMilli()
            var added = 0
            var seen = 0
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.ADDRESS),
                "${Telephony.Sms.DATE} >= ?", arrayOf(sinceMs.toString()), "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                val bodyCol = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateCol = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val addrCol = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                while (c.moveToNext() && seen++ < MAX_MESSAGES) {
                    val body = c.getString(bodyCol) ?: continue
                    val sender = c.getString(addrCol).orEmpty()
                    if (sender in muted) continue
                    val day = Instant.ofEpochMilli(c.getLong(dateCol)).atZone(zone).toLocalDate()
                    val parsed = SmsParser.parse(body, day, defaultCurrency)
                    if (parsed != null && repo.addIfNew(parsed, "SMS", body, sender)) added++
                    onNew(body, day)
                }
            }
            added
        }
}
