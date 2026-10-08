package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class GmailTextTest {
    @Test fun decodesUrlSafeBase64AndToleratesGarbage() {
        val enc = Base64.getUrlEncoder().withoutPadding().encodeToString("Total \$15.49 charged".toByteArray())
        assertEquals("Total \$15.49 charged", GmailText.decodeBody(enc))
        assertEquals("", GmailText.decodeBody("%%%"))
        assertEquals("", GmailText.decodeBody(null))
    }

    @Test fun stripsHtmlAndScripts() {
        val t = GmailText.stripHtml("<style>p{}</style><p>Your total&nbsp;is <b>\$9.99</b></p><script>x()</script>")
        assertEquals("Your total is \$9.99", t)
    }

    @Test fun senderNameFromHeader() {
        assertEquals("Netflix", GmailText.senderName("Netflix <info@mailer.netflix.com>"))
        assertEquals("Spotify", GmailText.senderName("\"Spotify\" <no-reply@spotify.com>"))
        assertEquals("Spotify", GmailText.senderName("no-reply@spotify.com"))
    }

    @Test fun queryIsLimitedToMailSinceLastSync() {
        assertTrue(GmailText.query(1_700_000_000).endsWith("after:1700000000"))
    }
}
