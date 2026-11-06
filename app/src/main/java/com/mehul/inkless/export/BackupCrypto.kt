package com.mehul.inkless.export

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPassword : Exception("Wrong password, or the file is damaged.")

/**
 * Locks a backup file with a password, using only what Java ships with:
 * the password is stretched into a 256-bit key with PBKDF2 (many rounds, so guessing passwords is
 * slow), and the file is encrypted with AES-GCM, which also notices any change to it.
 *
 * File layout: `INKLESS1` · salt (16 bytes) · rounds (4 bytes) · IV (12 bytes) · ciphertext.
 * Nothing about the contents is readable without the password, and the password is never stored.
 */
object BackupCrypto {

    private val MAGIC = "INKLESS1".toByteArray()
    private const val ROUNDS = 210_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun encrypt(plain: ByteArray, password: CharArray, rounds: Int = ROUNDS): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key(password, salt, rounds), GCMParameterSpec(TAG_BITS, iv))
            // The header is part of what's checked, so it can't be swapped either.
            updateAAD(MAGIC)
        }
        val sealed = cipher.doFinal(plain)
        return ByteBuffer.allocate(MAGIC.size + SALT_BYTES + 4 + IV_BYTES + sealed.size)
            .put(MAGIC).put(salt).putInt(rounds).put(iv).put(sealed)
            .array()
    }

    fun decrypt(file: ByteArray, password: CharArray): ByteArray {
        if (!isEncrypted(file)) throw BadBackupFile("This isn't an Inkless backup file.")
        val buffer = ByteBuffer.wrap(file, MAGIC.size, file.size - MAGIC.size)
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val rounds = buffer.int
        if (rounds !in 1..10_000_000) throw BadBackupFile("The backup file is damaged.")
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        val sealed = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(password, salt, rounds), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(MAGIC)
        }
        return try {
            cipher.doFinal(sealed)
        } catch (e: AEADBadTagException) {
            throw WrongPassword()
        }
    }

    fun isEncrypted(file: ByteArray): Boolean =
        file.size > MAGIC.size + SALT_BYTES + 4 + IV_BYTES && file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun key(password: CharArray, salt: ByteArray, rounds: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, rounds, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
