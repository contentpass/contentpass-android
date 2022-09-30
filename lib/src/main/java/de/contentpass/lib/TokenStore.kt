package de.contentpass.lib

import android.content.Context
import net.openid.appauth.AuthState
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

internal class TokenStore(context: Context, private val keyStore: KeyStore) : TokenStoring {
    private val tokenKey = "de.contentpass.AuthState"
    private val ivKey = "de.contentpass.IV"
    private val cipher = Cipher.getInstance("AES/GCM/NOPADDING")
    private val key by lazy { keyStore.key }

    private val sharedPreferences by lazy {
        context.getSharedPreferences("de.contentpass", Context.MODE_PRIVATE)
    }

    override fun retrieveAuthState(): AuthState? {
        return sharedPreferences.getString(tokenKey, null)?.let { token ->
            return retrieveIV()?.let { iv ->
                try {
                    val decrypted = decrypt(token.decoded(), iv)
                    AuthState.jsonDeserialize(decrypted.encoded())
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }

    private fun retrieveIV(): ByteArray? {
        return sharedPreferences.getString(ivKey, null)
            ?.toByteArray(Charsets.ISO_8859_1)
    }

    private fun decrypt(encrypted: ByteArray, iv: ByteArray): ByteArray {
        val spec = createSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(encrypted)
    }

    override fun storeAuthState(authState: AuthState) {
        val json = authState.jsonSerializeString()

        val iv = createIV()
        storeIV(iv)
        val encoded = json.decoded()
        val encrypted = encrypt(encoded, iv)

        sharedPreferences.edit()
            .putString(tokenKey, encrypted.encoded())
            .apply()
    }

    private fun createIV(): ByteArray {
        val array = ByteArray(12)
        val random = SecureRandom()
        random.nextBytes(array)
        return array
    }

    private fun storeIV(iv: ByteArray) {
        val encoded = String(iv, Charsets.ISO_8859_1)

        sharedPreferences.edit()
            .putString(ivKey, encoded)
            .apply()
    }

    private fun createSpec(iv: ByteArray): GCMParameterSpec {
        return GCMParameterSpec(16 * 8, iv)
    }

    private fun encrypt(plain: ByteArray, iv: ByteArray): ByteArray {
        val spec = createSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        return cipher.doFinal(plain)
    }

    override fun deleteAuthState() {
        sharedPreferences.edit()
            .remove(tokenKey)
            .apply()
    }
}
