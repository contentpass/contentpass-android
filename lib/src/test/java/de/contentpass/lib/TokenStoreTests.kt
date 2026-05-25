package de.contentpass.lib

import android.content.Context
import android.content.SharedPreferences
import io.mockk.*
import net.openid.appauth.AuthState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.crypto.KeyGenerator

class TokenStoreTests {
    private val tokenKey = "de.contentpass.AuthState"
    private val ivKey = "de.contentpass.IV"

    private var map = mutableMapOf<String, String>()
    private val context: Context = mockk()

    private val captureSlot = slot<String>()

    private val store = TokenStore(context, object : SecretKeyProviding {
        override val key = createSecretKey()
    })

    init {
        val sharedPrefs: SharedPreferences = mockk()
        every { sharedPrefs.getString(tokenKey, any()) }.answers { map[tokenKey] }
        every { sharedPrefs.getString(ivKey, any()) }.answers { map[ivKey] }
        val editor: SharedPreferences.Editor = mockk()
        every { sharedPrefs.edit() }.returns(editor)

        every { editor.putString(tokenKey, capture(captureSlot)) }
            .answers {
                map[tokenKey] = captureSlot.captured
                editor
            }
        every { editor.putString(ivKey, capture(captureSlot)) }
            .answers {
                map[ivKey] = captureSlot.captured
                editor
            }
        every { editor.remove(tokenKey) }
            .answers {
                map.remove(tokenKey)
                editor
            }

        every { editor.apply() }.answers {}

        every { context.getSharedPreferences(any(), any()) }.returns(sharedPrefs)
    }

    @Before
    fun `clear map`() {
        map.clear()
    }

    @Test
    fun `storing and retrieving an auth state works`() {
        val authState: AuthState = mockk()
        every { authState.idToken }.returns("here be dragons")
        every { authState.jsonSerializeString() }.returns("here be dragons")
        mockkStatic(AuthState::class)
        every { AuthState.jsonDeserialize("here be dragons") }.returns(authState)

        store.storeAuthState(authState)

        assertEquals(2, map.size)

        val result = store.retrieveAuthState()

        assertNotNull(result)
        assertEquals("here be dragons", result!!.idToken)
    }

    @Test
    fun `retrieveAuthState returns null when token is missing`() {
        assertNull(store.retrieveAuthState())
    }

    @Test
    fun `retrieveAuthState returns null when iv is missing`() {
        map[tokenKey] = "stored-token"

        assertNull(store.retrieveAuthState())
    }

    @Test
    fun `retrieveAuthState returns null when stored token cannot be decrypted`() {
        map[tokenKey] = "stored-token"
        map[ivKey] = String(ByteArray(12), Charsets.ISO_8859_1)

        assertNull(store.retrieveAuthState())
    }

    @Test
    fun `storeAuthState writes encrypted token and iv`() {
        val authState: AuthState = mockk()
        every { authState.jsonSerializeString() }.returns("here be dragons")

        store.storeAuthState(authState)

        assertTrue(map.containsKey(tokenKey))
        assertTrue(map.containsKey(ivKey))
    }

    @Test
    fun `deleting an auth state works`() {
        val authState: AuthState = mockk()
        every { authState.jsonSerializeString() }.returns("here be dragons")
        mockkStatic(AuthState::class)
        every { AuthState.jsonDeserialize("here be dragons") }.returns(authState)

        store.storeAuthState(authState)
        assertNotNull(store.retrieveAuthState())

        store.deleteAuthState()
        assertNull(store.retrieveAuthState())
    }

    @Test
    fun `deleteAuthState removes token and keeps iv`() {
        val authState: AuthState = mockk()
        every { authState.jsonSerializeString() }.returns("here be dragons")

        store.storeAuthState(authState)
        store.deleteAuthState()

        assertFalse(map.containsKey(tokenKey))
        assertTrue(map.containsKey(ivKey))
    }

    private fun createSecretKey(): javax.crypto.SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256)
        return generator.generateKey()
    }
}