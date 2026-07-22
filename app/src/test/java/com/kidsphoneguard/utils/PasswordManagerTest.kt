package com.kidsphoneguard.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PasswordManagerTest {

    @Test
    fun setPassword_storesVersionedSaltedHashAndRemovesLegacy() {
        val storage = FakePasswordStorage(legacyPassword = "legacy-secret")
        val salt = ByteArray(16) { it.toByte() }
        val manager = PasswordManager(
            storage = storage,
            saltGenerator = { salt }
        )

        manager.setPassword("parent-secret")

        assertNull(storage.legacyPassword)
        assertNotNull(storage.hashedPassword.encodedHash)
        assertNotEquals("parent-secret", storage.hashedPassword.encodedHash)
        assertEquals(Base64.getEncoder().encodeToString(salt), storage.hashedPassword.encodedSalt)
        assertEquals(1, storage.hashedPassword.version)
        assertTrue(manager.hasPasswordConfigured())
    }

    @Test
    fun verifyPassword_acceptsOnlyCorrectNonBlankPasswordAndRunsSuccessCallback() {
        val storage = FakePasswordStorage()
        var verifiedCount = 0
        val manager = PasswordManager(
            storage = storage,
            saltGenerator = { ByteArray(16) { 7 } },
            onPasswordVerified = { verifiedCount++ }
        )
        manager.setPassword("parent-secret")

        assertFalse(manager.verifyPassword(""))
        assertFalse(manager.verifyPassword("wrong-secret"))
        assertEquals(0, verifiedCount)
        assertTrue(manager.verifyPassword("parent-secret"))
        assertEquals(1, verifiedCount)
    }

    @Test
    fun setPassword_differentSaltsProduceDifferentHashes() {
        val firstStorage = FakePasswordStorage()
        val secondStorage = FakePasswordStorage()
        PasswordManager(
            storage = firstStorage,
            saltGenerator = { ByteArray(16) { 1 } }
        ).setPassword("same-password")
        PasswordManager(
            storage = secondStorage,
            saltGenerator = { ByteArray(16) { 2 } }
        ).setPassword("same-password")

        assertNotEquals(
            firstStorage.hashedPassword.encodedHash,
            secondStorage.hashedPassword.encodedHash
        )
    }

    @Test
    fun verifyPassword_rejectsUnsupportedVersionAndCorruptBase64() {
        val logger = RecordingPasswordManagerLogger()
        val unsupportedStorage = FakePasswordStorage(
            hashedPassword = StoredPasswordHash("hash", "salt", version = 99)
        )
        val corruptStorage = FakePasswordStorage(
            hashedPassword = StoredPasswordHash("%%%", "%%%", version = 1)
        )

        assertFalse(PasswordManager(unsupportedStorage).verifyPassword("parent-secret"))
        assertFalse(
            PasswordManager(
                storage = corruptStorage,
                logger = logger
            ).verifyPassword("parent-secret")
        )
        assertEquals(listOf("stored_password_hash_invalid"), logger.errors)
    }

    @Test
    fun migrateLegacyPassword_hashesBeforeRemovingPlaintext() {
        val storage = FakePasswordStorage(legacyPassword = "legacy-secret")
        val manager = PasswordManager(
            storage = storage,
            saltGenerator = { ByteArray(16) { 3 } }
        )

        manager.migrateLegacyPasswordIfNeeded()

        assertNull(storage.legacyPassword)
        assertTrue(storage.hashedPassword.isComplete)
        assertTrue(manager.verifyPassword("legacy-secret"))
    }

    @Test
    fun migrateLegacyPassword_writeFailurePreservesPlaintextForRetry() {
        val storage = FakePasswordStorage(
            legacyPassword = "legacy-secret",
            failHashedPasswordWrite = true
        )
        val logger = RecordingPasswordManagerLogger()
        val manager = PasswordManager(
            storage = storage,
            saltGenerator = { ByteArray(16) { 4 } },
            logger = logger
        )

        manager.migrateLegacyPasswordIfNeeded()

        assertEquals("legacy-secret", storage.legacyPassword)
        assertFalse(storage.hashedPassword.isComplete)
        assertEquals(listOf("legacy_password_migration_failed"), logger.errors)
    }

    @Test
    fun migrateLegacyPassword_existingHashRemovesOnlyRedundantPlaintext() {
        val storage = FakePasswordStorage(legacyPassword = "legacy-secret")
        val manager = PasswordManager(
            storage = storage,
            saltGenerator = { ByteArray(16) { 5 } }
        )
        manager.setPassword("current-secret")
        val originalHash = storage.hashedPassword
        storage.legacyPassword = "redundant-legacy"

        manager.migrateLegacyPasswordIfNeeded()

        assertNull(storage.legacyPassword)
        assertEquals(originalHash, storage.hashedPassword)
        assertTrue(manager.verifyPassword("current-secret"))
    }

    @Test
    fun resetToDefault_clearsLegacyAndHashedPassword() {
        val storage = FakePasswordStorage(legacyPassword = "legacy-secret")
        val manager = PasswordManager(
            storage = storage,
            saltGenerator = { ByteArray(16) { 6 } }
        )
        manager.setPassword("parent-secret")
        storage.legacyPassword = "redundant-legacy"

        manager.resetToDefault()

        assertNull(storage.legacyPassword)
        assertFalse(storage.hashedPassword.isComplete)
        assertFalse(manager.hasPasswordConfigured())
    }
}

private class FakePasswordStorage(
    var legacyPassword: String? = null,
    var hashedPassword: StoredPasswordHash = StoredPasswordHash(null, null, null),
    private val failHashedPasswordWrite: Boolean = false
) : PasswordStorage {
    override fun readLegacyPassword(): String? = legacyPassword

    override fun readHashedPassword(): StoredPasswordHash = hashedPassword

    override fun storeHashedPassword(encodedHash: String, encodedSalt: String, version: Int) {
        if (failHashedPasswordWrite) {
            throw IllegalStateException("simulated password storage failure")
        }
        hashedPassword = StoredPasswordHash(encodedHash, encodedSalt, version)
        legacyPassword = null
    }

    override fun removeLegacyPassword() {
        legacyPassword = null
    }

    override fun clearPassword() {
        legacyPassword = null
        hashedPassword = StoredPasswordHash(null, null, null)
    }
}

private class RecordingPasswordManagerLogger : PasswordManagerLogger {
    val errors = mutableListOf<String>()

    override fun info(message: String) = Unit

    override fun error(message: String, exception: Exception) {
        errors += message
    }
}
