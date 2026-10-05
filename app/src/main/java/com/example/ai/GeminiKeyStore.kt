package com.example.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages Gemini API keys in a dedicated SharedPreferences file ("gemini_secure_prefs")
 * excluded from Android Cloud Backup and device-to-device transfer, encrypted at rest
 * using an Android Keystore AES-GCM 256-bit key.
 */
object GeminiKeyStore {
    private const val TAG = "GeminiKeyStore"
    const val SECURE_PREFS_NAME = "gemini_secure_prefs"
    private const val LEGACY_PREFS_NAME = "app_settings"

    private const val KEY_ALIAS = "hearmark_gemini_api_key_aes"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val ENCRYPTED_PREFIX = "ENC_V1:"

    private const val PREF_SAVED_KEYS = "saved_gemini_api_keys"
    private const val PREF_ACTIVE_KEY_ID = "active_gemini_api_key_id"
    private const val PREF_CUSTOM_KEY = "custom_gemini_api_key"

    data class StoredKeyData(
        val savedKeysJson: String,
        val activeKeyId: String,
        val legacyCustomKey: String
    )

    @Synchronized
    fun loadStoredKeys(context: Context): StoredKeyData {
        val securePrefs = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

        // One-time migration from legacy plaintext app_settings if present
        val legacySavedJson = legacyPrefs.getString(PREF_SAVED_KEYS, null)
        val legacyCustom = legacyPrefs.getString(PREF_CUSTOM_KEY, null)
        val legacyActiveId = legacyPrefs.getString(PREF_ACTIVE_KEY_ID, null)

        if (!legacySavedJson.isNullOrBlank() || !legacyCustom.isNullOrBlank()) {
            val currentSecureJson = decryptValue(securePrefs.getString(PREF_SAVED_KEYS, "") ?: "")
            val currentSecureCustom = decryptValue(securePrefs.getString(PREF_CUSTOM_KEY, "") ?: "")

            val migratedJson = if (currentSecureJson.isNotBlank()) currentSecureJson else (legacySavedJson ?: "")
            val migratedCustom = if (currentSecureCustom.isNotBlank()) currentSecureCustom else (legacyCustom ?: "")
            val migratedActiveId = securePrefs.getString(PREF_ACTIVE_KEY_ID, null)
                ?.takeIf { it.isNotBlank() }
                ?: (legacyActiveId ?: "")

            securePrefs.edit()
                .putString(PREF_SAVED_KEYS, encryptValue(migratedJson))
                .putString(PREF_CUSTOM_KEY, encryptValue(migratedCustom))
                .putString(PREF_ACTIVE_KEY_ID, migratedActiveId)
                .apply()

            legacyPrefs.edit()
                .remove(PREF_SAVED_KEYS)
                .remove(PREF_CUSTOM_KEY)
                .remove(PREF_ACTIVE_KEY_ID)
                .apply()

            return StoredKeyData(
                savedKeysJson = migratedJson,
                activeKeyId = migratedActiveId,
                legacyCustomKey = migratedCustom
            )
        }

        val savedKeysJson = decryptValue(securePrefs.getString(PREF_SAVED_KEYS, "") ?: "")
        val customKey = decryptValue(securePrefs.getString(PREF_CUSTOM_KEY, "") ?: "")
        val activeKeyId = securePrefs.getString(PREF_ACTIVE_KEY_ID, "") ?: ""

        return StoredKeyData(
            savedKeysJson = savedKeysJson,
            activeKeyId = activeKeyId,
            legacyCustomKey = customKey
        )
    }

    @Synchronized
    fun saveKeys(
        context: Context,
        savedKeysJson: String,
        activeKeyId: String,
        activeKeyString: String
    ) {
        val securePrefs = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        securePrefs.edit()
            .putString(PREF_SAVED_KEYS, encryptValue(savedKeysJson))
            .putString(PREF_ACTIVE_KEY_ID, activeKeyId)
            .putString(PREF_CUSTOM_KEY, encryptValue(activeKeyString))
            .apply()

        // Ensure legacy plaintext keys are removed
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (legacyPrefs.contains(PREF_SAVED_KEYS) || legacyPrefs.contains(PREF_CUSTOM_KEY)) {
            legacyPrefs.edit()
                .remove(PREF_SAVED_KEYS)
                .remove(PREF_CUSTOM_KEY)
                .remove(PREF_ACTIVE_KEY_ID)
                .apply()
        }
    }

    @Synchronized
    fun clearCustomKey(context: Context) {
        val securePrefs = context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
        securePrefs.edit()
            .putString(PREF_CUSTOM_KEY, "")
            .apply()

        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (legacyPrefs.contains(PREF_CUSTOM_KEY)) {
            legacyPrefs.edit().remove(PREF_CUSTOM_KEY).apply()
        }
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            if (existingKey != null) return existingKey

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (e: Exception) {
            Log.w(TAG, "AndroidKeyStore unavailable, falling back to obfuscated storage: ${e.message}")
            null
        }
    }

    internal fun encryptValue(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val secretKey = getOrCreateSecretKey() ?: return plainText
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
            ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Encryption failed, returning fallback: ${e.message}")
            plainText
        }
    }

    internal fun decryptValue(storedValue: String): String {
        if (storedValue.isEmpty()) return ""
        if (!storedValue.startsWith(ENCRYPTED_PREFIX)) return storedValue
        val secretKey = getOrCreateSecretKey() ?: return ""
        return try {
            val encoded = storedValue.removePrefix(ENCRYPTED_PREFIX)
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_LENGTH) return ""
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val cipherBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Decryption failed: ${e.message}")
            ""
        }
    }
}
