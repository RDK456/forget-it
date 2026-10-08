package app.forgetit.gmail

import app.forgetit.AppContainer
import app.forgetit.domain.GmailText
import app.forgetit.domain.SmsParser
import app.forgetit.reminders.Notifications
import app.forgetit.txn.AutoScan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reads payment-looking Gmail messages (read-only), keeps only payments, and stores nothing else. */
object GmailScanner {
    private const val API = "https://gmail.googleapis.com/gmail/v1/users/me"
    private const val MAX_MESSAGES = 100
    const val NOT_SIGNED_IN = -1
    const val FAILED = -2

    private fun getJson(token: String, url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 15_000
        conn.readTimeout = 25_000
        try {
            if (conn.responseCode !in 200..299) error("Gmail answered ${conn.responseCode}")
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun header(payload: JSONObject, name: String): String {
        val h = payload.optJSONArray("headers") ?: return ""
        for (i in 0 until h.length()) h.getJSONObject(i).let { if (it.optString("name").equals(name, true)) return it.optString("value") }
        return ""
    }

    /** First readable text in the payload: plain text preferred, HTML stripped otherwise. */
    private fun bodyText(payload: JSONObject): String {
        fun walk(p: JSONObject, mime: String): String? {
            if (p.optString("mimeType") == mime) GmailText.decodeBody(p.optJSONObject("body")?.optString("data")).takeIf { it.isNotBlank() }?.let { return it }
            val parts = p.optJSONArray("parts") ?: return null
            for (i in 0 until parts.length()) walk(parts.getJSONObject(i), mime)?.let { return it }
            return null
        }
        walk(payload, "text/plain")?.let { return it.take(4000) }
        return walk(payload, "text/html")?.let { GmailText.stripHtml(it).take(4000) }.orEmpty()
    }

    /** Syncs mail since the last sync (90 days the first time). Returns new payments, NOT_SIGNED_IN or FAILED. */
    suspend fun sync(c: AppContainer, notify: Boolean = false): Int = withContext(Dispatchers.IO) {
        val s = c.settings.flow.first()
        if (s.gmailEmail.isEmpty()) return@withContext NOT_SIGNED_IN
        val token = (GmailAuth.authorize(c.context) as? GmailAuth.Result.Token)?.value ?: return@withContext NOT_SIGNED_IN
        val nowSec = Instant.now(c.clock).epochSecond
        val after = if (s.gmailLastSync == 0L) nowSec - 90L * 86_400 else s.gmailLastSync - 3_600
        try {
            val q = URLEncoder.encode(GmailText.query(after), "UTF-8")
            val list = getJson(token, "$API/messages?q=$q&maxResults=$MAX_MESSAGES")
            val ids = list.optJSONArray("messages")
            var added = 0
            for (i in 0 until (ids?.length() ?: 0)) {
                val msg = getJson(token, "$API/messages/${ids!!.getJSONObject(i).getString("id")}?format=full")
                val payload = msg.optJSONObject("payload") ?: continue
                val subject = header(payload, "Subject")
                val from = GmailText.senderName(header(payload, "From"))
                if (from in s.mutedSenders) continue
                val day = LocalDate.ofInstant(Instant.ofEpochMilli(msg.optLong("internalDate", nowSec * 1000)), ZoneId.systemDefault())
                val text = "$subject. ${msg.optString("snippet")}"
                val parsed = SmsParser.parse(text, day, s.defaultCurrency) ?: SmsParser.parse("$subject. ${bodyText(payload)}", day, s.defaultCurrency) ?: continue
                val p = if (parsed.merchant == null) parsed.copy(merchant = from.take(40)) else parsed
                if (c.txns.addIfNew(p, "GMAIL", text, from)) added++
            }
            c.settings.setGmailLastSync(nowSec)
            if (added > 0) AutoScan.markMatchedEmis(c)
            if (added > 0 && notify) Notifications.showFound(c.context, added)
            added
        } catch (e: Exception) {
            FAILED
        }
    }
}
