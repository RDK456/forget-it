package app.forgetit.domain

data class ReleaseAsset(val name: String, val url: String, val size: Long)

data class ReleaseInfo(val tag: String, val notes: String, val assets: List<ReleaseAsset>)

/**
 * What to download to reach [version]. A [delta] is a small binary patch that turns the APK installed on this phone into [target];
 * when there is none, [target] is downloaded whole. [expectedSha256] comes from the release's SHA256SUMS file.
 */
data class UpdatePlan(
    val version: String,
    val notes: String,
    val target: ReleaseAsset,
    val delta: ReleaseAsset?,
    val expectedSha256: String?,
) {
    val downloadSize: Long get() = delta?.size ?: target.size
}

/** Version comparison, checksum file parsing and the choice between a delta patch and a full download. Pure, so it is unit tested. */
object Updates {
    /** "v1.2.3-beta" becomes [1, 2, 3]. */
    fun parseVersion(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+').split('.').map { it.toIntOrNull() ?: 0 }

    private fun isPre(v: String) = '-' in v.trim().removePrefix("v")

    fun isNewer(latest: String, current: String): Boolean {
        val a = parseVersion(latest)
        val b = parseVersion(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        // Same numbers: the final release is newer than a beta of it.
        return isPre(current) && !isPre(latest)
    }

    /** Lines of "sha256  file name"; a leading * on the name marks binary mode and is ignored. */
    fun parseSums(text: String): Map<String, String> = text.lines().mapNotNull { line ->
        val parts = line.trim().split(Regex("""\s+"""), limit = 2)
        if (parts.size == 2 && parts[0].length == 64) parts[1].trimStart('*').trim() to parts[0].lowercase() else null
    }.toMap()

    private const val DELTA_PREFIX = "delta-"
    private const val TO = "-to-"
    private const val DELTA_SUFFIX = ".patch"

    fun deltaName(oldSha256: String, targetName: String) = "$DELTA_PREFIX${oldSha256.take(12)}$TO$targetName$DELTA_SUFFIX"

    /**
     * [abis] are this phone's preferred ABIs in order. [installedSha256] is the checksum of the APK that is installed now:
     * a patch named for it is used when the release has one, otherwise the APK for the phone's ABI (or the universal one) is downloaded whole.
     */
    fun plan(info: ReleaseInfo, current: String, abis: List<String>, installedSha256: String?, sums: Map<String, String>): UpdatePlan? {
        if (!isNewer(info.tag, current)) return null
        val apks = info.assets.filter { it.name.endsWith(".apk") }
        val sha12 = installedSha256?.take(12)
        val delta = sha12?.let { s -> info.assets.firstOrNull { it.name.startsWith("$DELTA_PREFIX$s$TO") && it.name.endsWith(DELTA_SUFFIX) } }
        val deltaTarget = delta?.let { d -> apks.firstOrNull { it.name == d.name.removePrefix("$DELTA_PREFIX$sha12$TO").removeSuffix(DELTA_SUFFIX) } }
        val target = deltaTarget
            ?: abis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.name.endsWith("-$abi.apk") } }
            ?: apks.firstOrNull { it.name.endsWith("-universal.apk") }
            ?: return null
        val usable = if (deltaTarget != null) delta else null
        return UpdatePlan(info.tag.removePrefix("v"), info.notes, target, usable, sums[target.name])
    }
}
