package com.jlees.mibrowser.web

import com.jlees.mibrowser.ButtonStyle
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.jlees.mibrowser.AlertButton
import com.jlees.mibrowser.BrowserViewModel

/**
 * Camera, microphone and location for sites: asked per site, then the Android runtime permission. Answers last for the
 * session only, kept per [scope] (an incognito profile's name, or "" for normal tabs) so they never cross profiles.
 */
class SitePermissions(
    private val activity: Activity,
    private val vm: BrowserViewModel,
    private val requestPermissions: (Array<String>, (Map<String, Boolean>) -> Unit) -> Unit,
) {
    /** "scope|host|resource" allowed this session. */
    private val allowed = HashSet<String>()

    /** What [host] was allowed in [scope] this session (Site settings). */
    fun allowedFor(scope: String, host: String) = SiteAccess.entries.filter { "$scope|$host|${it.key}" in allowed }

    /** Takes back what [host] was allowed: it's asked again next time. */
    fun revoke(scope: String, host: String, access: SiteAccess) { allowed -= "$scope|$host|${access.key}" }

    /** Forgets what was allowed in [scope] ("" and null: everything). */
    fun forget(scope: String? = null) { if (scope.isNullOrEmpty()) allowed.clear() else allowed.removeAll { it.startsWith("$scope|") } }

    /** Camera/mic: ask the user per site (remembered for this session), then the Android runtime permission. */
    fun media(scope: String, request: PermissionRequest) {
        val host = request.origin?.host ?: run { request.deny(); return }
        val wanted = request.resources.filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }
        if (wanted.isEmpty()) { request.deny(); return }
        if (wanted.all { "$scope|$host|$it" in allowed }) { grantMedia(request); return }
        val what = wanted.joinToString(" and ") { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) "camera" else "microphone" }
        vm.showAlert(
            "Allow $what?", "$host wants to use your $what.",
            listOf(
                AlertButton("Block", ButtonStyle.CANCEL) { runCatching { request.deny() } },
                AlertButton("Allow") { wanted.forEach { allowed += "$scope|$host|$it" }; grantMedia(request) },
            ),
            onCancel = { runCatching { request.deny() } },
        )
    }

    private fun grantMedia(request: PermissionRequest) {
        val map = mapOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE to Manifest.permission.RECORD_AUDIO, PermissionRequest.RESOURCE_VIDEO_CAPTURE to Manifest.permission.CAMERA)
        val granted = mutableListOf<String>()
        val needed = mutableListOf<String>()
        for (r in request.resources) {
            val perm = map[r] ?: continue
            if (ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED) granted += r else needed += perm
        }
        val grant = { g: List<String> -> runCatching { if (g.isEmpty()) request.deny() else request.grant(g.distinct().toTypedArray()) } }
        if (needed.isEmpty()) { grant(granted); return }
        requestPermissions(needed.toTypedArray()) { result ->
            for ((res, perm) in map) if (res in request.resources && result[perm] == true) granted += res
            grant(granted)
        }
    }

    /** Location: ask the user per site (remembered for this session), then the Android runtime permission. */
    fun location(scope: String, origin: String, callback: GeolocationPermissions.Callback) {
        val host = Uri.parse(origin).host ?: origin
        val grantLocation = {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) callback.invoke(origin, true, false)
            else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { r -> callback.invoke(origin, r.values.any { it }, false) }
        }
        if ("$scope|$host|location" in allowed) { grantLocation(); return }
        vm.showAlert(
            "Allow location?", "$host wants to know your location.",
            listOf(
                AlertButton("Block", ButtonStyle.CANCEL) { callback.invoke(origin, false, false) },
                AlertButton("Allow") { allowed += "$scope|$host|location"; grantLocation() },
            ),
            onCancel = { callback.invoke(origin, false, false) },
        )
    }
}

/** What a site can be allowed; [key] is how it's remembered (WebView's resource names, and "location"). */
enum class SiteAccess(val label: String, val icon: String, val key: String) {
    CAMERA("Camera", "photo_camera", PermissionRequest.RESOURCE_VIDEO_CAPTURE),
    MICROPHONE("Microphone", "mic", PermissionRequest.RESOURCE_AUDIO_CAPTURE),
    LOCATION("Location", "location_on", "location"),
}
