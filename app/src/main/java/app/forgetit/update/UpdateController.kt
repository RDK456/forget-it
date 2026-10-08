package app.forgetit.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import app.forgetit.domain.ReleaseAsset
import app.forgetit.domain.ReleaseInfo
import app.forgetit.domain.UpdatePlan
import app.forgetit.domain.Updates
import io.sigpipe.jbsdiff.Patch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val plan: UpdatePlan) : UpdateState
    /** [fraction] is -1 when the size is not known. */
    data class Working(val label: String, val fraction: Float) : UpdateState
    data object NeedsPermission : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Checks this app's GitHub Releases for a newer version and installs it. When the release carries a small binary patch made for the
 * APK that is installed now, only that patch is downloaded and applied; every result is checked against the release's SHA256SUMS first,
 * and Android refuses the install unless it is signed with the same key as the installed app.
 */
class UpdateController(private val context: Context, private val scope: CoroutineScope) {
    companion object {
        const val REPO = "RDK456/forget-it"
        private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    }

    val state = MutableStateFlow<UpdateState>(UpdateState.Idle)

    val currentVersion: String
        get() = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    private val dir get() = File(context.cacheDir, "updates").also { it.mkdirs() }

    private fun http(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "Forget-it/$currentVersion")
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val buf = ByteArray(64 * 1024); while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fetchText(url: String): String {
        val c = http(url)
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    private fun parseRelease(json: String): ReleaseInfo {
        val o = JSONObject(json)
        val assets = o.optJSONArray("assets")
        return ReleaseInfo(
            o.optString("tag_name"), o.optString("body"),
            (0 until (assets?.length() ?: 0)).map { i -> assets!!.getJSONObject(i).let { ReleaseAsset(it.getString("name"), it.getString("browser_download_url"), it.optLong("size")) } },
        )
    }

    /** Looks for a newer release. Safe to call often: it is one small request. */
    suspend fun check() {
        state.value = UpdateState.Checking
        state.value = withContext(Dispatchers.IO) {
            try {
                val info = parseRelease(fetchText(API))
                val sums = info.assets.firstOrNull { it.name == "SHA256SUMS" }?.let { Updates.parseSums(fetchText(it.url)) }.orEmpty()
                val installed = runCatching { sha256(File(context.applicationInfo.sourceDir)) }.getOrNull()
                Updates.plan(info, currentVersion, Build.SUPPORTED_ABIS.toList(), installed, sums)?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate(currentVersion)
            } catch (e: Exception) {
                UpdateState.Failed(if (e.message?.contains("404") == true) "No release has been published yet." else "Could not check for updates. Check your connection.")
            }
        }
    }

    fun checkAsync() { scope.launch { check() } }

    private fun download(asset: ReleaseAsset, to: File, label: String) {
        val c = http(asset.url)
        c.setRequestProperty("Accept", "application/octet-stream")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: asset.size
            var done = 0L
            c.inputStream.use { input ->
                FileOutputStream(to).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        state.value = UpdateState.Working(label, if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
                    }
                }
            }
        } finally { c.disconnect() }
    }

    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Downloads (a patch when there is one, else the whole APK), verifies it, and hands it to Android's installer. */
    fun installAsync(plan: UpdatePlan) {
        if (!canInstall()) { state.value = UpdateState.NeedsPermission; return }
        scope.launch(Dispatchers.IO) {
            try {
                val expected = plan.expectedSha256 ?: error("The release has no checksum file")
                val apk = File(dir, plan.target.name)
                apk.delete()
                var ready = false
                if (plan.delta != null) {
                    try {
                        val patch = File(dir, plan.delta.name)
                        download(plan.delta, patch, "Downloading update patch")
                        state.value = UpdateState.Working("Applying patch", -1f)
                        FileOutputStream(apk).use { Patch.patch(File(context.applicationInfo.sourceDir).readBytes(), patch.readBytes(), it) }
                        patch.delete()
                        ready = sha256(apk) == expected
                    } catch (e: Exception) { ready = false }
                    if (!ready) apk.delete()
                }
                if (!ready) {
                    download(plan.target, apk, "Downloading update")
                    if (sha256(apk) != expected) { apk.delete(); error("The download is damaged. Try again.") }
                }
                state.value = UpdateState.Working("Installing", -1f)
                install(apk)
            } catch (e: Exception) {
                state.value = UpdateState.Failed(e.message ?: "The update failed")
            }
        }
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input -> session.openWrite("forget-it.apk", 0, apk.length()).use { out -> input.copyTo(out); session.fsync(out) } }
            val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pending.intentSender)
        }
    }

    fun onInstallResult(success: Boolean, message: String?) {
        state.value = if (success) UpdateState.Idle else UpdateState.Failed(message ?: "Android could not install the update")
        if (success) dir.deleteRecursively()
    }
}
