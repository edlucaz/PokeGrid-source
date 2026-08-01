package online.idleworld.pokegrid.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import online.idleworld.pokegrid.config.GameConfig
import online.idleworld.pokegrid.model.Account
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Equivalent to the Electron app's `safeStorage`-backed accounts.enc: a JSON blob of the 4
 * accounts, encrypted with an AES-256-GCM key that lives in AndroidKeyStore (hardware-backed
 * when the device supports it) and never leaves it. Nothing is ever sent off-device.
 */
class CredentialStore(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "accounts.enc")

    private val secretKey: SecretKey by lazy { getOrCreateKey() }

    fun load(): List<Account> {
        val bytes = try {
            file.readBytes()
        } catch (e: Exception) {
            return List(GameConfig.PANEL_COUNT) { Account() }
        }
        return try {
            val json = decrypt(bytes)
            parseAccounts(json)
        } catch (e: Exception) {
            Log.w(TAG, "accounts.enc unreadable (key changed?), preserving a backup copy", e)
            try {
                file.copyTo(File(appContext.filesDir, "accounts.enc.bak-${System.currentTimeMillis()}"))
            } catch (_: Exception) {
            }
            List(GameConfig.PANEL_COUNT) { Account() }
        }
    }

    fun save(accounts: List<Account>) {
        val json = toJson(accounts)
        val encrypted = encrypt(json)
        val tmp = File(appContext.filesDir, "accounts.enc.tmp")
        tmp.writeBytes(encrypted)
        // Atomic swap: killing the app mid-write can't corrupt the previously saved copy.
        tmp.renameTo(file)
    }

    private fun parseAccounts(json: String): List<Account> {
        val arr = JSONArray(json)
        return List(GameConfig.PANEL_COUNT) { i ->
            if (i < arr.length()) {
                val o = arr.optJSONObject(i) ?: JSONObject()
                Account(
                    name = o.optString("name", ""),
                    email = o.optString("email", ""),
                    senha = o.optString("senha", "")
                )
            } else Account()
        }
    }

    private fun toJson(accounts: List<Account>): String {
        val arr = JSONArray()
        accounts.forEach { a ->
            arr.put(
                JSONObject()
                    .put("name", a.name)
                    .put("email", a.email)
                    .put("senha", a.senha)
            )
        }
        return arr.toString()
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plainJson: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    private fun decrypt(blob: ByteArray): String {
        val ivLen = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ciphertext = blob.copyOfRange(1 + ivLen, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    companion object {
        private const val TAG = "CredentialStore"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "pokegrid_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
