package com.jlees.mibrowser.logic

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads at most [max] bytes of [input]: the bytes, and whether there was more (cut off at [max]). For fetches that
 * shouldn't take in a whole huge response (a site icon, a page's head). Works on every Android version (readNBytes
 * needs 13).
 */
fun readAtMost(input: InputStream, max: Int): Pair<ByteArray, Boolean> {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < max) {
        val n = input.read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) return out.toByteArray() to false
        out.write(buf, 0, n)
    }
    return out.toByteArray() to (input.read() >= 0)
}
