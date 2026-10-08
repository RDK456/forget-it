package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class RateCodecTest {
    @Test fun roundTrips() {
        val rates = mapOf("EUR" to Rate(BigDecimal("1.10"), d("2026-10-08")), "JPY" to Rate(BigDecimal("0.0067"), d("2026-10-01")))
        assertEquals(rates, RateCodec.decode(RateCodec.encode(rates)))
    }

    @Test fun emptyAndGarbageDecodeToEmpty() {
        assertEquals(emptyMap<String, Rate>(), RateCodec.decode(""))
        assertEquals(emptyMap<String, Rate>(), RateCodec.decode("junk;EUR|x|2026-01-01;EUR|0|2026-01-01;XX1|1|2026-01-01"))
    }

    @Test fun keepsGoodEntriesAroundBadOnes() {
        val out = RateCodec.decode("bad;EUR|1.5|2026-01-02")
        assertEquals(BigDecimal("1.5"), out.getValue("EUR").value)
    }
}
