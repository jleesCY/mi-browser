// Replaces AsyncStorage (legacy/src/utils.ts saveStorage/loadStorage) and expo-secure-store (legacy/src/utils/secureStorage.ts)
package com.jlees.mibrowser.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true; isLenient = true }

/** One JSON file per AsyncStorage key, written atomically. */
class JsonStore(private val dir: File) {
    init { dir.mkdirs() }

    fun <T> read(key: String, s: KSerializer<T>): T? = try {
        File(dir, "$key.json").takeIf { it.exists() }?.readText()?.let { json.decodeFromString(s, it) }
    } catch (_: Exception) { null }

    @Synchronized
    fun <T> write(key: String, s: KSerializer<T>, value: T) {
        val tmp = File(dir, "$key.json.tmp")
        tmp.writeText(json.encodeToString(s, value))
        tmp.renameTo(File(dir, "$key.json"))
    }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }
}

/** Android Keystore AES-GCM encrypted values (SecureStore equivalent). */
class SecureStore(private val dir: File) {
    private val alias = "mi_secure_store"
    init { dir.mkdirs() }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }

    fun <T> read(name: String, s: KSerializer<T>): T? = try {
        val bytes = File(dir, name).takeIf { it.exists() }?.readBytes() ?: return null
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        json.decodeFromString(s, String(c.doFinal(bytes.copyOfRange(12, bytes.size))))
    } catch (_: Exception) { null }

    @Synchronized
    fun <T> write(name: String, s: KSerializer<T>, value: T) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        File(dir, name).writeBytes(c.iv + c.doFinal(json.encodeToString(s, value).toByteArray()))
    }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }
}
