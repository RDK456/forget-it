package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test fun versionsCompareNumerically() {
        assertTrue(Updates.isNewer("1.0.1", "1.0.0"))
        assertTrue(Updates.isNewer("v1.10.0", "1.9.9"))
        assertTrue(Updates.isNewer("2.0", "1.99.99"))
        assertFalse(Updates.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updates.isNewer("1.0.0", "1.0.1"))
    }

    @Test fun aFinalReleaseBeatsItsBeta() {
        assertTrue(Updates.isNewer("1.0.0", "1.0.0-beta"))
        assertFalse(Updates.isNewer("1.0.0-beta", "1.0.0"))
        assertTrue(Updates.isNewer("1.0.1-beta", "1.0.0"))
    }

    @Test fun checksumFileIsParsed() {
        val sha = "a".repeat(64)
        val m = Updates.parseSums("$sha  forget-it-1.1.0-arm64-v8a.apk\n${"B".repeat(64)} *other.apk\nnot a line\n")
        assertEquals(sha, m["forget-it-1.1.0-arm64-v8a.apk"])
        assertEquals("b".repeat(64), m["other.apk"])
        assertEquals(2, m.size)
    }

    private fun asset(n: String, size: Long = 10) = ReleaseAsset(n, "https://x/$n", size)
    private val installed = "0123456789abcdef".repeat(4)
    private val info = ReleaseInfo(
        "v1.1.0", "notes",
        listOf(
            asset("forget-it-1.1.0-arm64-v8a.apk", 30_000_000), asset("forget-it-1.1.0-armeabi-v7a.apk", 21_000_000),
            asset("forget-it-1.1.0-universal.apk", 90_000_000), asset("SHA256SUMS", 300),
        ),
    )

    @Test fun picksTheApkForThePhonesAbiInPreferenceOrder() {
        val p = Updates.plan(info, "1.0.0", listOf("x86_64", "armeabi-v7a", "armeabi"), installed, emptyMap())!!
        assertEquals("forget-it-1.1.0-armeabi-v7a.apk", p.target.name)
        assertNull(p.delta)
        assertEquals("1.1.0", p.version)
    }

    @Test fun fallsBackToTheUniversalApk() {
        assertEquals("forget-it-1.1.0-universal.apk", Updates.plan(info, "1.0.0", listOf("riscv64"), null, emptyMap())!!.target.name)
    }

    @Test fun aPatchForTheInstalledApkIsPreferred() {
        val patch = asset(Updates.deltaName(installed, "forget-it-1.1.0-arm64-v8a.apk"), 1_200_000)
        val sums = mapOf("forget-it-1.1.0-arm64-v8a.apk" to "c".repeat(64))
        val p = Updates.plan(info.copy(assets = info.assets + patch), "1.0.0", listOf("armeabi-v7a"), installed, sums)!!
        assertEquals(patch, p.delta)
        assertEquals("forget-it-1.1.0-arm64-v8a.apk", p.target.name)
        assertEquals(1_200_000L, p.downloadSize)
        assertEquals("c".repeat(64), p.expectedSha256)
    }

    @Test fun aPatchForAnotherBuildIsIgnored() {
        val patch = asset(Updates.deltaName("f".repeat(64), "forget-it-1.1.0-arm64-v8a.apk"), 1_000)
        val p = Updates.plan(info.copy(assets = info.assets + patch), "1.0.0", listOf("arm64-v8a"), installed, emptyMap())!!
        assertNull(p.delta)
        assertEquals(30_000_000L, p.downloadSize)
    }

    @Test fun nothingWhenAlreadyCurrentOrNoApk() {
        assertNull(Updates.plan(info, "1.1.0", listOf("arm64-v8a"), installed, emptyMap()))
        assertNull(Updates.plan(ReleaseInfo("v2.0.0", "", listOf(asset("SHA256SUMS"))), "1.0.0", listOf("arm64-v8a"), installed, emptyMap()))
    }
}
