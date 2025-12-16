package de.contentpass.lib

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import java.security.KeyStore as VendorKeyStore

internal class KeyStore(private val context: Context, private val propertyId: String) {
    private val keystoreName = "AndroidKeyStore"
    private val keyPairAlias = "de.contentpass.KeyPair.$propertyId"
    private val privateKey: PrivateKey
    private val publicKey: PublicKey
    private val keystore = VendorKeyStore.getInstance(keystoreName)
    private val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
    private val paddingSpec by lazy {
        OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA1,
            PSource.PSpecified.DEFAULT
        )
    }
    private val sharedPreferences by lazy {
        context.getSharedPreferences("de.contentpass", Context.MODE_PRIVATE)
    }
    private val sharedPreferencesKey = "AES_KEY"

    val key by lazy {
        retrieveKey()?.let {
            return@lazy it
        } ?: run {
            return@lazy createKey()
        }
    }

    init {
        keystore.load(null)

        if (!keystore.containsAlias(keyPairAlias)) {
            createKeyPair()
        }
        val pair = keystore.getEntry(keyPairAlias, null) as VendorKeyStore.PrivateKeyEntry
        privateKey = pair.privateKey
        publicKey = pair.certificate.publicKey
    }

    private fun createKeyPair(): KeyPair {
        val spec = buildKeyGenParameterSpec()
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            "AndroidKeyStore"
        )
        generator.initialize(spec as java.security.spec.AlgorithmParameterSpec)

        return generator.generateKeyPair()
    }

    private fun buildKeyGenParameterSpec(): KeyGenParameterSpec {
        return KeyGenParameterSpec.Builder(
            keyPairAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .build()
    }

    private fun retrieveKey(): SecretKey? {
        return sharedPreferences.getString(sharedPreferencesKey, null)?.let { encryptedString ->
            try {
                val encrypted = encryptedString.decoded()
                decryptKey(encrypted)
            } catch (e: Exception) {
                // If decryption fails, the stored key might be corrupted
                // Return null to trigger key regeneration
                null
            }
        }
    }

    private fun decryptKey(encryptedKey: ByteArray): SecretKey {
        try {
            cipher.init(Cipher.DECRYPT_MODE, privateKey, paddingSpec)
            val decrypted = cipher.doFinal(encryptedKey)
            return SecretKeySpec(decrypted, "AES")
        } catch (e: javax.crypto.IllegalBlockSizeException) {
            // If decryption fails due to block size issues, try to regenerate the key
            // by deleting the corrupted entry and creating a new one
            throw IllegalStateException("Failed to decrypt stored key. Key may be corrupted.", e)
        }
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        val secretKey = generator.generateKey()
        storeKey(secretKey)
        return secretKey
    }

    private fun storeKey(secretKey: SecretKey) {
        val encrypted = encryptKey(secretKey)
        val encoded = encrypted.encoded()

        sharedPreferences.edit()
            .putString(sharedPreferencesKey, encoded)
            .apply()
    }

    private fun encryptKey(key: SecretKey): ByteArray {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, paddingSpec)
            return cipher.doFinal(key.encoded)
        } catch (e: javax.crypto.IllegalBlockSizeException) {
            throw IllegalStateException("Failed to encrypt key. Key size may be incompatible.", e)
        }
    }
}

internal fun ByteArray.encoded(): String {
    return String(this, Charsets.ISO_8859_1)
}

internal fun String.decoded(): ByteArray {
    return toByteArray(Charsets.ISO_8859_1)
}
