package dev.gabrie.brainwave.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.first

private val Context.secretsDataStore by preferencesDataStore(name = "secrets")

/**
 * Stores the SMTP password and the API keys.
 *
 * Values are sealed with an AES-GCM key generated inside the Android Keystore,
 * so the key material never enters the app process and a copied
 * `secrets.preferences_pb` is useless on another device. That is also why the
 * backup rules exclude that file.
 */
class SecretStore(private val context: Context) {

    suspend fun get(name: String): String? {
        val stored = context.secretsDataStore.data.first()[stringPreferencesKey(name)] ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    suspend fun put(name: String, value: String) {
        val key = stringPreferencesKey(name)
        context.secretsDataStore.edit { prefs ->
            if (value.isEmpty()) prefs.remove(key) else prefs[key] = encrypt(value)
        }
    }

    suspend fun isSet(name: String): Boolean = !get(name).isNullOrEmpty()

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(1 + iv.size + body.size)
        packed[0] = iv.size.toByte()
        System.arraycopy(iv, 0, packed, 1, iv.size)
        System.arraycopy(body, 0, packed, 1 + iv.size, body.size)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        val ivSize = packed[0].toInt()
        val iv = packed.copyOfRange(1, 1 + ivSize)
        val body = packed.copyOfRange(1 + ivSize, packed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        const val SMTP_PASSWORD = "smtp_password"
        const val TRANSCRIPTION_API_KEY = "transcription_api_key"
        const val CLAUDE_API_KEY = "claude_api_key"

        private const val PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "brainwave_secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
    }
}
