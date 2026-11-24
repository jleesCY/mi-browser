// The scripts mi runs inside pages, kept as plain JavaScript in assets/js/ and read once.
package com.jlees.mibrowser.web

import android.content.Context

class PageScripts(ctx: Context) {
    private val assets = ctx.applicationContext.assets
    private fun load(name: String) = assets.open("js/$name.js").bufferedReader().use { it.readText() }

    /** Every page, at document start: page Print and Share buttons call into mi. */
    val bridge by lazy { load("bridge") }
    /** Every frame, at document start: reports audio and video playing, and plays/pauses it (media/BackgroundMedia). */
    val media by lazy { load("media") }
    /** Every frame, at document start: element hiding for ad, tracker and cookie-banner blocking. */
    val cosmeticFilter by lazy { load("cosmetic-filter") }
    val scriptlets by lazy { load("scriptlets") }
    /** Incognito frames, at document start: creating passkeys is refused (signing in still works). */
    val noPasskeyCreation by lazy { load("no-passkey-creation") }
    /** After load, when reader mode is on: a reading layout without navigation, sidebars or ad boxes. */
    val reader by lazy { load("reader") }
    /** With the status bar hidden: space at the top of the page for the camera cutout (see TopInset). */
    val topInset by lazy { load("top-inset") }
    /** After load: the page's largest touch icon, for its site icon. */
    val touchIcon by lazy { load("touch-icon") }
    /** After load: the page's own colour, for the status bar in app mode (launcher/AppMode). */
    val appColor by lazy { load("app-color") }
    /** After load, with Darken on: darkens a bright page that opts out of WebView's own darkening. */
    val darkFallback by lazy { load("dark-fallback") }
    /** Every page, at document start: hands a file the page made itself (blob:/data:) over to mi when it's downloaded. */
    val blobDownload by lazy { load("blob-download") }
    /** The WebExtensions API (chrome.* / browser.*) as an expression, for the extension engine (extensions/). */
    val extApi by lazy { load("ext-api") }
    val extAddress by lazy { load("ext-address") }
    /** The body of the document-start script that runs extensions' content scripts in a page. */
    val extContent by lazy { load("ext-content") }
    /** The body of the document-start script for an extension's own pages (background, popup, options). */
    val extPage by lazy { load("ext-page") }
}
