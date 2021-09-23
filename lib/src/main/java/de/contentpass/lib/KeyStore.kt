package de.contentpass.lib

import android.content.Context
import android.security.KeyPairGeneratorSpec
import java.math.BigInteger
import java.security.*
import java.security.spec.MGF1ParameterSpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal
import java.security.KeyStore as VendorKeyStore

internal class KeyStore(private val context: Context) {
    private val keystoreName = "AndroidKeyStore"
    private val keyPairAlias = "de.contentpass.KeyPair"
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
        val spec = buildKeyPairGeneratorSpec()
        val generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
        generator.initialize(spec)

        return generator.generateKeyPair()
    }

    private fun buildKeyPairGeneratorSpec(): KeyPairGeneratorSpec {
        val start = Calendar.getInstance()
        val end = Calendar.getInstance()
        end.add(Calendar.YEAR, 1)

        return KeyPairGeneratorSpec.Builder(context)
            .setAlias(keyPairAlias)
            .setSubject(X500Principal("CN=Sample Name, O=Android Authority"))
            .setSerialNumber(BigInteger.ONE)
            .setStartDate(start.time)
            .setEndDate(end.time)
            .build()
    }

    private fun retrieveKey(): SecretKey? {
        return sharedPreferences.getString(sharedPreferencesKey, null)?.let {
            val encrypted = it.decoded()
            return decryptKey(encrypted)
        }
    }

    private fun decryptKey(encryptedKey: ByteArray): SecretKey {
        cipher.init(Cipher.DECRYPT_MODE, privateKey, paddingSpec)
        val decrypted = cipher.doFinal(encryptedKey)
        return SecretKeySpec(decrypted, "AES")
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
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, paddingSpec)
        return cipher.doFinal(key.encoded)
    }
}

internal fun ByteArray.encoded(): String {
    return String(this, Charsets.ISO_8859_1)
}

internal fun String.decoded(): ByteArray {
    return toByteArray(Charsets.ISO_8859_1)
}