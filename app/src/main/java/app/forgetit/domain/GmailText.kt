package app.forgetit.domain

import java.util.Base64

/** Small text helpers for reading Gmail API message payloads. No Android or JSON types, so they are unit tested. */
object GmailText {
    /** Gmail sends body parts as URL-safe base64. Returns empty text for anything undecodable. */
    fun decodeBody(data: String?): String =
        if (data.isNullOrEmpty()) "" else runCatching { String(Base64.getUrlDecoder().decode(data.trim()), Charsets.UTF_8) }.getOrDefault("")

    fun stripHtml(html: String): String = html
        .replace(Regex("""(?is)<(script|style)[^>]*>.*?</\1>"""), " ")
        .replace(Regex("""(?s)<[^>]+>"""), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&#36;", "$")
        .replace(Regex("""\s+"""), " ").trim()

    /** "Netflix <info@mailer.netflix.com>" becomes "Netflix"; a bare address becomes its domain name part. */
    fun senderName(from: String): String {
        val name = if ('<' in from) from.substringBefore('<').trim().trim('"') else ""
        if (name.isNotEmpty()) return name
        val domain = from.substringAfter('@', "").substringBefore('>').trim()
        return domain.split('.').dropLast(1).lastOrNull()?.replaceFirstChar { it.uppercase() } ?: from.trim()
    }

    /** The search that finds payment-looking mail. [afterEpochSeconds] limits it to mail since the last sync. */
    fun query(afterEpochSeconds: Long): String =
        "(receipt OR invoice OR payment OR subscription OR renewed OR billed OR charged OR \"your order\") after:$afterEpochSeconds"
}
