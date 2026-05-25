package de.contentpass.lib

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ContentPassTokenTests {
    private val validToken =
        "eyJhbGciOiJSUzI1NiJ9.eyJhdXRoIjp0cnVlLCJwbGFucyI6WyJjYTQ5MmFmNy0zMjBjLTQyYzktOWJhMC1iMmEzM2NmY2EzMDciXSwiYXVkIjoiNjliMjg5ODUiLCJpYXQiOjE2Mjg3NjYyOTIsImV4cCI6MTYyODk0MjY5Mn0"
    private val missingPlansToken =
        "ewogICJhbGciOiAiUlMyNTYiCn0.ewogICJhdXRoIjogdHJ1ZSwKICAicGxhbnMiOiBbXSwKICAiYXVkIjogIjY5YjI4OTg1IiwKICAiaWF0IjogMTYyODc2NjI5MiwKICAiZXhwIjogMTYyODk0MjY5Mgp9"
    private val noAuthToken =
        "ewogICJhbGciOiAiUlMyNTYiCn0.ewogICJhdXRoIjogZmFsc2UsCiAgInBsYW5zIjogWwogICAgImNhNDkyYWY3LTMyMGMtNDJjOS05YmEwLWIyYTMzY2ZjYTMwNyIKICBdLAogICJhdWQiOiAiNjliMjg5ODUiLAogICJpYXQiOiAxNjI4NzY2MjkyLAogICJleHAiOiAxNjI4OTQyNjkyCn0"

    @Before
    fun `bypass android_util_Base64 to java_util_Base64`() {
        mockkStatic(Base64::class)

        val stringSlot = slot<String>()
        every {
            Base64.decode(capture(stringSlot), any())
        } answers {
            java.util.Base64.getUrlDecoder().decode(stringSlot.captured)
        }
    }

    @Test
    fun `isSubscriptionValid returns true when authorized and plans are set`() {
        val contentPassToken = ContentPassToken(validToken)

        assertTrue(contentPassToken.isSubscriptionValid)
    }

    @Test
    fun `isSubscriptionValid returns true when authorized and multiple plans are set`() {
        val token = tokenWithBody(
            """
            {
              "auth": true,
              "plans": ["first-plan", "second-plan"],
              "aud": "69b28985",
              "iat": 1628766292,
              "exp": 1628942692
            }
            """.trimIndent()
        )

        val contentPassToken = ContentPassToken(token)

        assertTrue(contentPassToken.isSubscriptionValid)
    }

    @Test
    fun `token segments are decoded as Base64URL without padding`() {
        val token = tokenWithBody(
            """
            {
              "auth": true,
              "plans": ["first-plan"],
              "aud": "69b28985",
              "iat": 1628766292,
              "exp": 1628942692
            }
            """.trimIndent()
        )

        ContentPassToken(token)

        verify(exactly = 2) {
            Base64.decode(any<String>(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }
    }

    @Test
    fun `isSubscriptionValid returns false when unauthorized`() {
        val contentPassToken = ContentPassToken(noAuthToken)

        assertFalse(contentPassToken.isSubscriptionValid)
    }

    @Test
    fun `isSubscriptionValid returns false when plans are missing`() {
        val contentPassToken = ContentPassToken(missingPlansToken)

        assertFalse(contentPassToken.isSubscriptionValid)
    }

    @Test
    fun `invalid token format throws`() {
        try {
            ContentPassToken("invalid-token")
            fail("Expected malformed token to throw")
        } catch (throwable: Throwable) {
            assertTrue(throwable is IndexOutOfBoundsException || throwable is IllegalArgumentException)
        }
    }

    @Test
    fun `missing auth field throws`() {
        val token = tokenWithBody(
            """
            {
              "plans": ["first-plan"],
              "aud": "69b28985",
              "iat": 1628766292,
              "exp": 1628942692
            }
            """.trimIndent()
        )

        try {
            ContentPassToken(token)
            fail("Expected token without auth field to throw")
        } catch (throwable: Throwable) {
            assertTrue(throwable.message?.contains("auth") == true)
        }
    }

    @Test
    fun `missing plans field throws`() {
        val token = tokenWithBody(
            """
            {
              "auth": true,
              "aud": "69b28985",
              "iat": 1628766292,
              "exp": 1628942692
            }
            """.trimIndent()
        )

        try {
            ContentPassToken(token)
            fail("Expected token without plans field to throw")
        } catch (throwable: Throwable) {
            assertTrue(throwable.message?.contains("plans") == true)
        }
    }

    private fun tokenWithBody(body: String): String {
        val header = """{"alg":"RS256"}""".encoded()
        return "$header.${body.encoded()}"
    }

    private fun String.encoded(): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray())
}