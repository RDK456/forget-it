package app.forgetit.domain

/**
 * Decides whether a message is a real, completed payment: a debit or credit that happened, from a bank-style sender or a receipt.
 * Ads, offers, forwarded mail, newsletters, reminders and failed or requested payments are turned away. Pure logic, unit tested.
 */
object TxnFilter {
    private val IC = RegexOption.IGNORE_CASE

    private val PROMO = Regex(
        """\b(unsubscribe|view (?:this )?(?:email )?in (?:your )?browser|manage (?:your )?(?:email )?preferences|offers?|discounts?|\d+\s?% ?(?:off|cashback|discount)|cashback (?:up ?to|of up ?to)|get (?:up ?to )?(?:rs\.?|inr|₹)\s?\d[\d,]* (?:off|cashback)|up ?to (?:rs\.?|inr|₹)\s?\d|win|won|congratulations|lottery|jackpot|limited time|hurry|apply now|pre-?approved|coupons?|use code|promo(?:tion|tional)? code|vouchers?|reward points|redeem|click here|tap here|bit\.ly|tinyurl|instant loan|loan offer|kyc (?:update|expir\w*|pending)|free gift|t&c|terms apply|download the app|refer(?:ral)? and earn|exclusive|bonus)\b""",
        IC,
    )
    private val FORWARD = Regex("""(?:^|[\n.]\s*)(?:fwd?|fw|re)\s*:|-{2,}\s*(?:original|forwarded) message|begin forwarded message|\bforwarded message\b""", IC)
    private val NOT_DONE = Regex(
        """\b(failed|declined|unsuccessful|not (?:been )?(?:processed|completed|successful)|insufficient|will be (?:debited|charged|deducted)|to be (?:debited|charged)|is due|are due|due (?:on|by|date)|overdue|pay (?:now|by|before)|reminder|requested|request(?:s|ed)? (?:money|payment)|collect request|expir\w+|minimum (?:amount )?due|payable|if (?:this )?was not (?:done )?by you)\b""",
        IC,
    )
    private val DONE = Regex(
        """\b(debited|credited|spent|paid|received|withdrawn|deposited|charged|deducted|purchased|refunded|transferred|sent|billed|renewed|auto-renewed|successful(?:ly)?)\b""",
        IC,
    )
    private val EVIDENCE = Regex("""\b(a/c|acct|account|card|upi|ref|txn|utr|imps|neft|rtgs|avl|bal(?:ance)?|vpa|wallet|order|invoice|receipt|transaction|payment)\b""", IC)
    private val ACCOUNT_EVIDENCE = Regex("""\b(a/c|acct|account|card|upi|utr|imps|neft|rtgs)\b""", IC)
    private val PHONE_NUMBER = Regex("""^\+?[0-9][0-9 -]{7,}$""")
    private val PROMO_SENDER = Regex("""-p$""", IC)

    /** Ads, offers, newsletters and forwarded mail. */
    fun isJunk(text: String) = PROMO.containsMatchIn(text) || FORWARD.containsMatchIn(text)

    /**
     * [strict] also demands a completed-payment word and an account, card, UPI or receipt marker; use it for new messages.
     * Stored snippets are cut short, so cleaning up old entries passes strict = false.
     */
    fun accept(text: String, sender: String = "", strict: Boolean = true): Boolean {
        if (isJunk(text) || NOT_DONE.containsMatchIn(text)) return false
        val s = sender.trim()
        if (PROMO_SENDER.containsMatchIn(s)) return false
        // A message from an ordinary phone number is a person, not a bank, unless it names an account or card.
        if (PHONE_NUMBER.matches(s) && !ACCOUNT_EVIDENCE.containsMatchIn(text)) return false
        if (strict && !(DONE.containsMatchIn(text) && EVIDENCE.containsMatchIn(text))) return false
        return true
    }
}
