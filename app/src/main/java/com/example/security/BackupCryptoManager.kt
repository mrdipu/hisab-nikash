package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Versioned portable encrypted backup envelope:
 * [0..3]   Magic: "HNKB"
 * [4..5]   Envelope version: Short (1)
 * [6]      KDF ID: Byte (1 = PBKDF2WithHmacSHA256)
 * [7..10]  KDF Iterations: Int (120_000)
 * [11]     Salt length: Byte (16)
 * [12..27] Salt bytes (16)
 * [28]     Nonce length: Byte (12)
 * [29..40] GCM Nonce bytes (12)
 * [41]     Compression: Byte (1 = GZIP, 0 = NONE)
 * [42..]   AES-256-GCM ciphertext + 16-byte authentication tag (header bytes [0..41] passed as AAD)
 */
object BackupCryptoManager {
    private val MAGIC = byteArrayOf('H'.code.toByte(), 'N'.code.toByte(), 'K'.code.toByte(), 'B'.code.toByte())
    private const val ENVELOPE_VERSION: Short = 1
    private const val KDF_PBKDF2_SHA256: Byte = 1
    private const val DEFAULT_ITERATIONS = 120_000
    private const val SALT_LEN = 16
    private const val NONCE_LEN = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val COMPRESSION_GZIP: Byte = 1
    private const val COMPRESSION_NONE: Byte = 0
    private const val HEADER_SIZE = 4 + 2 + 1 + 4 + 1 + SALT_LEN + 1 + NONCE_LEN + 1

    const val MAX_ENCRYPTED_BYTES = 25 * 1024 * 1024 // 25 MB cap
    const val MAX_DECOMPRESSED_BYTES = 50 * 1024 * 1024 // 50 MB zip-bomb cap

    private const val KEYSTORE_ALIAS = "hisabnikash_backup_wrap_key"
    private const val PREF_NAME = "hisabnikash_key_wrap"
    private const val PREF_WRAPPED_KEY = "wrapped_key_b64"
    private const val PREF_WRAP_IV = "wrapped_iv_b64"
    private const val PREF_KEY_SALT = "derived_salt_b64"

    private val csprng = SecureRandom()

    data class EnvelopeMetadata(
        val envelopeVersion: Int,
        val kdfId: Int,
        val iterations: Int,
        val isCompressed: Boolean
    )

    fun inspectHeader(bytes: ByteArray): Result<EnvelopeMetadata> {
        if (bytes.size > MAX_ENCRYPTED_BYTES) {
            return Result.failure(IllegalArgumentException("Backup file exceeds maximum safe size (25 MB)"))
        }
        if (bytes.size < HEADER_SIZE + 16) {
            return Result.failure(IllegalArgumentException("File is too small or corrupted to be a valid backup"))
        }
        for (i in MAGIC.indices) {
            if (bytes[i] != MAGIC[i]) {
                return Result.failure(IllegalArgumentException("Invalid backup format header (expected HNKB)"))
            }
        }
        val buf = ByteBuffer.wrap(bytes, 0, HEADER_SIZE)
        buf.position(4)
        val version = buf.short.toInt()
        if (version != ENVELOPE_VERSION.toInt()) {
            return Result.failure(IllegalArgumentException("Unsupported backup envelope version: $version"))
        }
        val kdfId = buf.get().toInt()
        if (kdfId != KDF_PBKDF2_SHA256.toInt()) {
            return Result.failure(IllegalArgumentException("Unsupported KDF algorithm: $kdfId"))
        }
        val iterations = buf.int
        if (iterations !in 10_000..2_000_000) {
            return Result.failure(IllegalArgumentException("Unsafe or invalid KDF iteration count"))
        }
        val saltLen = buf.get().toInt() and 0xFF
        if (saltLen != SALT_LEN) {
            return Result.failure(IllegalArgumentException("Invalid salt length"))
        }
        buf.position(buf.position() + saltLen)
        val nonceLen = buf.get().toInt() and 0xFF
        if (nonceLen != NONCE_LEN) {
            return Result.failure(IllegalArgumentException("Invalid nonce length"))
        }
        buf.position(buf.position() + nonceLen)
        val compression = buf.get()
        if (compression != COMPRESSION_GZIP && compression != COMPRESSION_NONE) {
            return Result.failure(IllegalArgumentException("Invalid compression flag"))
        }
        return Result.success(
            EnvelopeMetadata(
                envelopeVersion = version,
                kdfId = kdfId,
                iterations = iterations,
                isCompressed = compression == COMPRESSION_GZIP
            )
        )
    }

    fun encryptWithPassphrase(
        plaintextUtf8: ByteArray,
        passphrase: CharArray,
        compress: Boolean = true,
        iterations: Int = DEFAULT_ITERATIONS
    ): ByteArray {
        require(passphrase.isNotEmpty()) { "Passphrase cannot be empty" }
        val salt = ByteArray(SALT_LEN).also { csprng.nextBytes(it) }
        val keyBytes = deriveKeyBytes(passphrase, salt, iterations)
        return try {
            encryptWithRawKey(plaintextUtf8, keyBytes, salt, iterations, compress)
        } finally {
            Arrays.fill(keyBytes, 0.toByte())
        }
    }

    fun encryptWithRawKey(
        plaintextUtf8: ByteArray,
        keyBytes: ByteArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_ITERATIONS,
        compress: Boolean = true
    ): ByteArray {
        require(keyBytes.size == 32) { "Key must be 256 bits" }
        require(salt.size == SALT_LEN) { "Salt must be 16 bytes" }

        val payload = if (compress) gzip(plaintextUtf8) else plaintextUtf8
        val nonce = ByteArray(NONCE_LEN).also { csprng.nextBytes(it) }

        val header = ByteBuffer.allocate(HEADER_SIZE).apply {
            put(MAGIC)
            putShort(ENVELOPE_VERSION)
            put(KDF_PBKDF2_SHA256)
            putInt(iterations)
            put(SALT_LEN.toByte())
            put(salt)
            put(NONCE_LEN.toByte())
            put(nonce)
            put(if (compress) COMPRESSION_GZIP else COMPRESSION_NONE)
        }.array()

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(keyBytes, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(header)
        val ciphertext = cipher.doFinal(payload)

        return ByteArray(header.size + ciphertext.size).also { out ->
            System.arraycopy(header, 0, out, 0, header.size)
            System.arraycopy(ciphertext, 0, out, header.size, ciphertext.size)
        }
    }

    fun decryptWithPassphrase(envelopeBytes: ByteArray, passphrase: CharArray): Result<ByteArray> {
        val metaResult = inspectHeader(envelopeBytes)
        if (metaResult.isFailure) {
            return Result.failure(metaResult.exceptionOrNull()!!)
        }
        val buf = ByteBuffer.wrap(envelopeBytes, 0, HEADER_SIZE)
        buf.position(4 + 2 + 1)
        val iterations = buf.int
        val saltLen = buf.get().toInt() and 0xFF
        val salt = ByteArray(saltLen).also { buf.get(it) }
        val nonceLen = buf.get().toInt() and 0xFF
        val nonce = ByteArray(nonceLen).also { buf.get(it) }
        val compression = buf.get()

        val keyBytes = deriveKeyBytes(passphrase, salt, iterations)
        return try {
            val header = envelopeBytes.copyOfRange(0, HEADER_SIZE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val secretKey = SecretKeySpec(keyBytes, "AES")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(header)
            val decrypted = cipher.doFinal(envelopeBytes, HEADER_SIZE, envelopeBytes.size - HEADER_SIZE)
            val plain = if (compression == COMPRESSION_GZIP) gunzipBounded(decrypted) else decrypted
            Result.success(plain)
        } catch (_: AEADBadTagException) {
            Result.failure(SecurityException("Incorrect passphrase or tampered/corrupted backup"))
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            Arrays.fill(keyBytes, 0.toByte())
        }
    }

    fun deriveKeyBytes(passphrase: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Wraps a derived 256-bit backup key using Android Keystore for opt-in unattended Drive backups.
     * Never stores the plaintext passphrase.
     */
    fun saveRememberedBackupKey(context: Context, passphrase: CharArray): Result<Unit> {
        return try {
            val salt = ByteArray(SALT_LEN).also { csprng.nextBytes(it) }
            val derivedKey = deriveKeyBytes(passphrase, salt, DEFAULT_ITERATIONS)
            try {
                val wrapKey = getOrCreateKeystoreWrapKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, wrapKey)
                val wrapped = cipher.doFinal(derivedKey)
                val iv = cipher.iv
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putString(PREF_WRAPPED_KEY, Base64.encodeToString(wrapped, Base64.NO_WRAP))
                    .putString(PREF_WRAP_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .putString(PREF_KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .apply()
                Result.success(Unit)
            } finally {
                Arrays.fill(derivedKey, 0.toByte())
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun loadRememberedBackupKey(context: Context): Pair<ByteArray, ByteArray>? {
        return try {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val wrappedB64 = prefs.getString(PREF_WRAPPED_KEY, null) ?: return null
            val ivB64 = prefs.getString(PREF_WRAP_IV, null) ?: return null
            val saltB64 = prefs.getString(PREF_KEY_SALT, null) ?: return null
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val wrapKey = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(GCM_TAG_BITS, Base64.decode(ivB64, Base64.NO_WRAP)))
            val keyBytes = cipher.doFinal(Base64.decode(wrappedB64, Base64.NO_WRAP))
            val salt = Base64.decode(saltB64, Base64.NO_WRAP)
            keyBytes to salt
        } catch (_: Throwable) {
            null
        }
    }

    fun clearRememberedBackupKey(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                keyStore.deleteEntry(KEYSTORE_ALIAS)
            }
        }
    }

    private fun getOrCreateKeystoreWrapKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun gzip(input: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(input) }
        return bos.toByteArray()
    }

    private fun gunzipBounded(compressed: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gis ->
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val read = gis.read(buf)
                if (read == -1) break
                total += read
                if (total > MAX_DECOMPRESSED_BYTES) {
                    throw SecurityException("Decompressed backup exceeds safe memory bound")
                }
                bos.write(buf, 0, read)
            }
        }
        return bos.toByteArray()
    }
}
