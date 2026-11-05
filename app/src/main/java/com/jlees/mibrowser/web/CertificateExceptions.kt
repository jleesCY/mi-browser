package com.jlees.mibrowser.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Sites the user chose to continue to despite a certificate problem. Session only (never saved), and kept per profile
 * ([profile] null: normal tabs), so none cross between normal and incognito tabs.
 */
class CertificateExceptions {
    private var entries by mutableStateOf(setOf<String>()) // "<profile>|<host>"

    fun trust(profile: String?, host: String) { entries = entries + "${profile.orEmpty()}|$host" }
    fun has(profile: String?, host: String) = "${profile.orEmpty()}|$host" in entries
    fun hostsFor(profile: String?) = entries.filter { it.substringBefore('|') == profile.orEmpty() }.map { it.substringAfter('|') }.toSet()
    /** An incognito profile ended: its exceptions go with it. */
    fun forget(profile: String) { entries = entries.filterNot { it.startsWith("$profile|") }.toSet() }
    fun clear() { entries = emptySet() }
}
