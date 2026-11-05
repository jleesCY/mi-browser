package com.jlees.mibrowser.web

import android.app.Activity
import android.security.KeyChain
import android.webkit.ClientCertRequest
import android.webkit.HttpAuthHandler
import com.jlees.mibrowser.AuthRequest
import com.jlees.mibrowser.BrowserViewModel
import com.jlees.mibrowser.logic.displayHost
import com.jlees.mibrowser.logic.isLoopback

/**
 * Sites asking who you are: HTTP authentication (Basic/Digest) and client certificates. Typed credentials last for
 * the session only, kept per [scope] (incognito profile, or "" for normal tabs), and are never written to disk.
 */
class SignInChallenges(private val activity: Activity, private val vm: BrowserViewModel) {
    /** Each tab's latest main-frame navigation (including server redirects): the scheme of a challenge. */
    private val mainNav = HashMap<String, String>()
    /** "scope|host|realm" → user, password. */
    private val credentials = HashMap<String, Pair<String, String>>()
    /** Challenges already answered with remembered credentials: if the server asks again, they were wrong. */
    private val answered = HashSet<String>()

    fun navigated(tabId: String, url: String) { mainNav[tabId] = url }
    fun forgetTab(tabId: String) { mainNav.remove(tabId) }
    /** A page loaded: a later challenge is a new one, so remembered credentials may be tried again. */
    fun pageFinished() = answered.clear()
    /** Forgets what was typed in [scope] (null: everything). */
    fun forget(scope: String? = null) {
        if (scope == null) { credentials.clear(); answered.clear() } else credentials.keys.removeAll { it.startsWith("$scope|") }
    }

    /** Asks for credentials (remembered for this session, asked again if the server rejects them). */
    fun httpAuth(id: String, scope: String, pageUrl: String?, handler: HttpAuthHandler, host: String, realm: String) {
        val key = "$scope|$host|$realm"
        val known = credentials[key]
        if (known != null && key !in answered) { answered += key; handler.proceed(known.first, known.second); return }
        credentials.remove(key)
        // the challenge doesn't say http or https: use this tab's navigation to that host (else the current page)
        val nav = mainNav[id]?.takeIf { displayHost(it).equals(host, ignoreCase = true) } ?: pageUrl
        val insecure = nav?.startsWith("http://", ignoreCase = true) == true && !isLoopback(host)
        vm.authRequest = AuthRequest(
            host, realm, insecure,
            onSubmit = { user, pass -> credentials[key] = user to pass; answered += key; handler.proceed(user, pass) },
            onCancel = { handler.cancel() },
        )
    }

    /** A site asks for a client certificate: the user picks one from Android's key chain (or none). */
    fun clientCertificate(request: ClientCertRequest) {
        KeyChain.choosePrivateKeyAlias(activity, { alias ->
            if (alias == null) { request.cancel(); return@choosePrivateKeyAlias }
            Thread {
                runCatching {
                    val key = KeyChain.getPrivateKey(activity, alias)
                    val chain = KeyChain.getCertificateChain(activity, alias)
                    if (key != null && chain != null) request.proceed(key, chain) else request.cancel()
                }.onFailure { request.cancel() }
            }.start()
        }, request.keyTypes, request.principals, request.host, request.port, null)
    }
}
