package app.forgetit.domain

import java.time.LocalDate
import java.util.Locale

enum class TxnDirection { DEBIT, CREDIT }

data class ParsedTxn(
    val direction: TxnDirection,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val accountHint: String?,
    val date: LocalDate,
)

/** Reads bank, card, UPI and receipt-style text. Anything that does not look like a payment returns null. */
object SmsParser {
    private val IC = RegexOption.IGNORE_CASE
    private val OTP = Regex("""\b(otp|one[- ]time|verification code|passcode|cvv)\b""", IC)
    private val DEBIT = Regex("""\b(debited|debit|spent|purchase|purchased|paid|payment of|withdrawn|sent|charged|deducted|billed|renewed|auto-renewed)\b""", IC)
    private val CREDIT = Regex("""\b(credited|credit|received|refund|refunded|deposited)\b""", IC)
    private val CUR = listOf("rs[.]?", "inr", "usd", "eur", "gbp", "aed", "[$]").joinToString("|") + "|" +
        listOf(0x20B9, 0x20AC, 0xA3).joinToString("|") { String(Character.toChars(it)) }
    private val AMOUNT_FIRST = Regex("""($CUR)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", IC)
    private val AMOUNT_LAST = Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(rs|inr|usd|eur|gbp|aed)\b""", IC)
    private val MERCHANT = Regex(
        """(?:\bat\b|\bto\b|\btowards\b|\bfor\b|@)\s+([A-Za-z0-9][A-Za-z0-9 &._*-]{1,40}?)(?=\s+(?:on|via|using|ref|from|with|txn|if|has|is|dated|date|avl|bal|available)\b|\s+\d{1,2}[-/]|[.,;]|$)""",
        IC,
    )
    private val ACCOUNT = Regex("""(?:a/c|acct|account|card)(?:\s+no\.?)?\s*(?:ending|number|no\.?)?\s*[xX*]*\s*(\d{3,4})""", IC)
    private val NOT_MERCHANT = Regex("""^(your|you|a/c|acct|account|card|my|the|bank|upi)\b""", IC)

    private fun currencyOf(token: String, fallback: String): String = when (token.lowercase(Locale.ROOT).trim('.')) {
        "rs", "inr", "₹" -> "INR"
        "usd", "$" -> "USD"
        "eur", "€" -> "EUR"
        "gbp", "£" -> "GBP"
        "aed" -> "AED"
        else -> fallback
    }

    private fun cleanMerchant(raw: String): String? {
        var m = raw.trim().replace(Regex("""\s+"""), " ").replace(Regex("""^upi[- ]*(to)?\s*""", IC), "")
        m = m.trim(' ', '.', '-', '*')
        if (m.length < 2 || NOT_MERCHANT.containsMatchIn(m)) return null
        return if (m == m.uppercase(Locale.ROOT)) m.lowercase(Locale.ROOT).split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } } else m
    }

    fun parse(text: String, receivedOn: LocalDate, defaultCurrency: String = "USD"): ParsedTxn? {
        if (OTP.containsMatchIn(text)) return null
        val debit = DEBIT.find(text)
        val credit = CREDIT.find(text)
        val direction = when {
            debit != null && (credit == null || debit.range.first <= credit.range.first) -> TxnDirection.DEBIT
            credit != null -> TxnDirection.CREDIT
            else -> return null
        }
        val first = AMOUNT_FIRST.find(text)
        val (curToken, amountText) = if (first != null) first.groupValues[1] to first.groupValues[2]
        else AMOUNT_LAST.find(text)?.let { it.groupValues[2] to it.groupValues[1] } ?: return null
        val currency = currencyOf(curToken, defaultCurrency)
        val minor = Money.parseMinor(amountText.replace(",", ""), currency)?.takeIf { it > 0 } ?: return null
        val merchant = MERCHANT.findAll(text).mapNotNull { cleanMerchant(it.groupValues[1]) }.firstOrNull()?.let(Merchants::canonical)
        val account = ACCOUNT.find(text)?.groupValues?.get(1)
        return ParsedTxn(direction, minor, currency, merchant, account, receivedOn)
    }
}
