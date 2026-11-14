package kr.co.reevl.ontheway.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where the car token lives. One token per car; `clear()` on logout and on a definitive 401 only (never on a network
 * error, a 5xx or a Keystore hiccup — 2026-11-10 "stay linked once linked").
 *
 * [session] identifies one link: it changes on [set] (a new link) and [clear] (logout / 401), but NOT on [rotate] — a token
 * the server replaced (`x-car-token`, every 30 d) is the same link, so answers in flight across a rotation are still valid.
 */
interface TokenStore {
    fun get(): String?
    fun set(token: String)
    fun clear()
    /** Compare-and-swap: replace [old] with [new] only if [old] is still the stored token. Same session. */
    fun rotate(old: String, new: String): Boolean
    fun session(): Long
}

/** For tests and the mock build's previews only. */
class InMemoryTokenStore(private var token: String? = null) : TokenStore {
    private var session = 0L
    @Synchronized override fun get(): String? = token
    @Synchronized override fun set(token: String) { this.token = token; session++ }
    @Synchronized override fun clear() { token = null; session++ }
    @Synchronized override fun rotate(old: String, new: String): Boolean {
        if (token != old) return false
        token = new
        return true
    }
    @Synchronized override fun session(): Long = session
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
 * If the key is gone or the blob fails authentication (Keystore reset, corrupted file), the token is treated as absent and
 * the file is removed — the user links again. Any OTHER Keystore error (KeyStoreException, ProviderException, … — the
 * Keystore daemon can be briefly unavailable right after boot) is transient: the file is kept and the next [get] tries again,
 * so a hiccup never logs the car out (2026-11-10).
 */
class KeystoreTokenStore(dir: File, private val alias: String = KEY_ALIAS) : TokenStore {

    private val file = File(dir, FILE_NAME)
    @Volatile private var cached: String? = null
    @Volatile private var loaded = false

    @Volatile private var session = 0L

    override fun get(): String? {
        if (loaded) return cached
        synchronized(this) {
            if (!loaded) {
                try {
                    cached = read()
                    loaded = true
                } catch (e: Gone) {
                    file.delete(); cached = null; loaded = true
                } catch (e: AEADBadTagException) {
                    file.delete(); cached = null; loaded = true
                } catch (e: Exception) {
                    // Transient: keep the file, answer "no token" for now, try again on the next get().
                    cached = null
                }
            }
            return cached
        }
    }

    override fun set(token: String) {
        synchronized(this) {
            write(token)
            session++
        }
    }

    /** Atomic swap (tmp file + rename) — a crash mid-way leaves either the old or the new token, never none. */
    override fun rotate(old: String, new: String): Boolean = synchronized(this) {
        if (get() != old) return false
        write(new)
        true
    }

    override fun session(): Long = session

    override fun clear() {
        synchronized(this) {
            file.delete()
            cached = null
            loaded = true
            session++
        }
    }

    private fun write(token: String) {
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

    /** The key entry is missing: the blob can never be decrypted again. */
    private class Gone : Exception()

    /** Bytes on disk (for the evidence log: proves the token is not stored in plain text). Never the token itself. */
    fun sizeOnDisk(): Long = if (file.exists()) file.length() else 0L

    private fun read(): String? {
        if (!file.exists()) return null
        val blob = file.readBytes()
        if (blob.isEmpty()) throw Gone()
        val ivLen = blob[0].toInt()
        if (ivLen <= 0 || 1 + ivLen >= blob.size) throw Gone()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ct = blob.copyOfRange(1 + ivLen, blob.size)
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val key = (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: throw Gone()
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
