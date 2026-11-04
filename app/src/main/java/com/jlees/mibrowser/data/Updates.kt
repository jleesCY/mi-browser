// In-app update check against GitHub Releases (latest non-prerelease, tag "vX.Y.Z", one .apk asset).
package com.jlees.mibrowser.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val apkUrl: String)

object Updates {
    private const val LATEST = "https://api.github.com/repos/jleescy/mi-browser/releases/latest"

    /** True when [candidate] ("v1.2.3", "1.2.3-beta") is a higher x.y.z than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The latest release if it is newer than [current] and ships an APK, else null; a failure (offline, GitHub
     *  unreachable) is reported, not mistaken for "up to date". */
    suspend fun check(current: String): Result<Release?> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val json = JSONObject(conn.inputStream.use { it.readBytes().decodeToString() })
            val tag = json.getString("tag_name")
            val assets = json.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it).getString("browser_download_url") }
                // only accept APKs served from GitHub itself
                .firstOrNull { it.endsWith(".apk") && URL(it).host.equals("github.com", ignoreCase = true) }
            if (apk != null && isNewer(tag, current)) Release(tag.removePrefix("v"), apk) else null
        }
    }

    /**
     * Downloads the APK into the app cache and hands it to the system package installer.
     * Android shows its own confirmation (and, the first time, asks to allow installs from mi). The installer
     * only accepts the update if it is signed with the same key as the installed app.
     */
    suspend fun downloadAndInstall(ctx: Context, release: Release) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
            File(dir, "mi-${release.version}.apk").also { f ->
                URL(release.apkUrl).openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
            }
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
