package app.forgetit.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxnFilterTest {
    private fun ok(text: String, sender: String = "AX-HDFCBK-S") = TxnFilter.accept(text, sender)

    @Test fun realBankAndReceiptMessagesPass() {
        assertTrue(ok("Rs 499.00 spent on HDFC Bank Card ending 1234 at NETFLIX on 08-Oct-26. Avl bal Rs 5,000"))
        assertTrue(ok("Your a/c XX1234 is debited by INR 1,299.00 on 05-10-2026 for UPI to Swiggy ref 123456"))
        assertTrue(ok("Salary of INR 50,000.00 credited to your a/c XX1234 on 01-Oct"))
        assertTrue(ok("Spent USD 15.99 at SPOTIFY on your card ending 4455.", "Chase"))
        assertTrue(ok("Amazon Pay. You paid \$12.99 to Hulu via UPI", "Amazon Pay"))
    }

    @Test fun adsOffersAndNewslettersAreRejected() {
        assertFalse(ok("Get 20% cashback on payment of Rs 500 with your card. Limited time offer! T&C apply"))
        assertFalse(ok("Congratulations! You won Rs 50,000 credited soon. Click here to claim", "VM-PROMO"))
        assertFalse(ok("Pre-approved loan offer of Rs 5,00,000. Apply now. Spent less, earn more on card"))
        assertFalse(ok("Your receipt: \$9.99 paid for order. Unsubscribe from these emails"))
    }

    @Test fun forwardedMailIsRejected() {
        assertFalse(ok("Fwd: Rs 500 debited from your a/c XX1234 at Amazon"))
        assertFalse(ok("FW: Payment received. Rs 900 credited to your account"))
        assertFalse(ok("---------- Forwarded message ---------\nRs 500 debited from a/c XX1234"))
        assertFalse(ok("Re: Rs 500 debited from a/c XX1234 at Zomato"))
    }

    @Test fun remindersFailuresAndRequestsAreNotTransactions() {
        assertFalse(ok("Your EMI of Rs 12,450 is due on 05-Nov. Rs 12,450 will be debited from a/c XX1234"))
        assertFalse(ok("Transaction of Rs 500 on card ending 1234 failed due to insufficient balance"))
        assertFalse(ok("Ravi has requested money Rs 500 from you on UPI. Pay now"))
        assertFalse(ok("Rs 700 payment reminder for your a/c, pay before 10th"))
    }

    @Test fun senderRules() {
        assertFalse(TxnFilter.accept("Rs 500 debited from a/c XX1234 at Amazon", "VM-SHOP-P"))
        assertFalse(TxnFilter.accept("I paid you Rs 500 yesterday, sent it already", "+919812345678"))
        assertTrue(TxnFilter.accept("Rs 500 debited from your a/c XX1234 at Amazon UPI ref 99", "+919812345678"))
        assertTrue(TxnFilter.accept("Rs 500 debited from a/c XX1234", "AD-HDFCBK-T"))
    }

    @Test fun nothingThatIsNotDoneOrWithoutAnAccountMarkerPasses() {
        assertFalse(ok("Rs 500 is the price of the new plan"))
        assertFalse(ok("Rs 500 debited"))
        assertTrue(TxnFilter.accept("Rs 500 debited", "AX-HDFCBK-S", strict = false))
    }
}
