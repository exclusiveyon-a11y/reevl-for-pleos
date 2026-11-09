package kr.co.reevl.ontheway.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where the car token lives. One token per car; `clear()` on logout and on 401. */
interface TokenStore {
    fun get(): String?
    fun set(token: String)
    fun clear()
}

/** For tests and the mock build's previews only. */
class InMemoryTokenStore(private var token: String? = null) : TokenStore {
    override fun get(): String? = token
    override fun set(token: String) { this.token = token }
    override fun clear() { token = null }
}

/**
 * Car token at rest, encrypted with a key that never leaves the Android Keystore.
 *
 * Method (2026-10-26, non-deprecated): an AES-256 key generated in `AndroidKeyStore` with
 * [KeyGenParameterSpec] (GCM, no padding, random IV per encryption) + `javax.crypto.Cipher("AES/GCM/NoPadding")`.
 * The file holds `[1 byte IV length][IV][ciphertext+tag]` under `noBackupFilesDir` (and the manifest has
 * `allowBackup=false`). Not `EncryptedSharedPreferences` — androidx.security-crypto deprecated all of its APIs
 * (1.1.0-alpha07, 2025).
 *
 * If the key is gone or the blob does not decrypt (e.g. Keystore reset), the token is treated as absent and the
 * file is removed — the user links again with a new code.
 */
class KeystoreTokenStore(dir: File, private val alias: String = KEY_ALIAS) : TokenStore {

    private val file = File(dir, FILE_NAME)
    @Volatile private var cached: String? = null
    @Volatile private var loaded = false

    override fun get(): String? {
        if (loaded) return cached
        synchronized(this) {
            if (!loaded) {
                cached = runCatching { read() }.getOrElse { file.delete(); null }
                loaded = true
            }
            return cached
        }
    }

    override fun set(token: String) = synchronized(this) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ct = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        tmp.writeBytes(byteArrayOf(iv.size.toByte()) + iv + ct)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        cached = token
        loaded = true
    }

    override fun clear() = synchronized(this) {
        file.delete()
        cached = null
        loaded = true
    }

    /** Bytes on disk (for the evidence log: proves the token is not stored in plain text). Never the token itself. */
    fun sizeOnDisk(): Long = if (file.exists()) file.length() else 0L

    private fun read(): String? {
        if (!file.exists()) return null
        val blob = file.readBytes()
        val ivLen = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ct = blob.copyOfRange(1 + ivLen, blob.size)
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val key = (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return gen.generateKey()
    }

    companion object {
        const val KEY_ALIAS = "otw_car_token_aes"
        const val FILE_NAME = "car_token.bin"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
