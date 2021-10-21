package de.contentpass.lib

import android.util.Base64
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.slot
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
            Base64.decode(capture(stringSlot), Base64.DEFAULT)
        } answers {
            java.util.Base64.getDecoder().decode(stringSlot.captured)
        }
    }

    @Test
    fun `isSubscriptionValid returns true when authorized and plans are set`() {
        val contentPassToken = ContentPassToken(validToken)

        assert(contentPassToken.isSubscriptionValid)
    }

    @Test
    fun `isSubscriptionValid returns false when unauthorized`() {
        val contentPassToken = ContentPassToken(noAuthToken)

        assert(!contentPassToken.isSubscriptionValid)
    }

    @Test
    fun `isSubscriptionValid returns false when plans are missing`() {
        val contentPassToken = ContentPassToken(missingPlansToken)

        assert(!contentPassToken.isSubscriptionValid)
    }
}