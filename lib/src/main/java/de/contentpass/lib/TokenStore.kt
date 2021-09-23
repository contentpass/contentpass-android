package de.contentpass.lib

import android.content.Context
import android.security.KeyPairGeneratorSpec
import android.util.Log
import net.openid.appauth.AuthState
import java.math.BigInteger
import java.nio.charset.Charset
import java.security.*
import java.security.spec.MGF1ParameterSpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

internal class TokenStore(context: Context): TokenStoring {
    private val tokenKey = "de.contentpass.AuthState"
    private val ivKey = "de.contentpass.IV"
    private val cipher = Cipher.getInstance("AES/GCM/NOPADDING")
    private val keyStore = KeyStore(context)
    private val key by lazy { keyStore.key }

    private val sharedPreferences by lazy {
        context.getSharedPreferences("de.contentpass", Context.MODE_PRIVATE)
    }

    override fun retrieveAuthState(): AuthState? {
        return sharedPreferences.getString(tokenKey, null)?.let { key ->
            return retrieveIV()?.let { iv ->
                val decrypted = decrypt(key.decoded(), iv)
                AuthState.jsonDeserialize(decrypted.encoded())
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
        val bytes = encrypted
        return cipher.doFinal(bytes)
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