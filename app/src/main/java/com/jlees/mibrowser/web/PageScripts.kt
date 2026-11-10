// The scripts mi runs inside pages, kept as plain JavaScript in assets/js/ and read once.
package com.jlees.mibrowser.web

import android.content.Context

class PageScripts(ctx: Context) {
    private val assets = ctx.applicationContext.assets
    private fun load(name: String) = assets.open("js/$name.js").bufferedReader().use { it.readText() }

    /** Every page, at document start: page Print and Share buttons call into mi. */
    val bridge by lazy { load("bridge") }
    /** Every frame, at document start: element hiding for ad, tracker and cookie-banner blocking. */
    val cosmeticFilter by lazy { load("cosmetic-filter") }
    /** Incognito frames, at document start: creating passkeys is refused (signing in still works). */
    val noPasskeyCreation by lazy { load("no-passkey-creation") }
    /** After load, when reader mode is on: a reading layout without navigation, sidebars or ad boxes. */
    val reader by lazy { load("reader") }
    /** With the status bar hidden: space at the top of the page for the camera cutout (see TopInset). */
    val topInset by lazy { load("top-inset") }
    /** After load: the page's largest touch icon, for its site icon. */
    val touchIcon by lazy { load("touch-icon") }
}
