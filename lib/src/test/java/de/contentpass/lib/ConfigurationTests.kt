package de.contentpass.lib

import android.net.Uri
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConfigurationTests {
    @Before
    fun setUp() {
        mockkStatic(Uri::class)

        every { Uri.parse(any()) } answers {
            val value = firstArg<String>()
            val uri: Uri = mockk()
            every { uri.toString() } returns value
            uri
        }
    }

    @Test
    fun `UriAdapter parses string into Uri`() {
        val uri = UriAdapter.fromJson("https://example.com/path")

        assertEquals("https://example.com/path", uri.toString())
    }

    @Test
    fun `UriAdapter serializes Uri to string`() {
        val uri: Uri = mockk()
        every { uri.toString() } returns "https://example.com/path"

        val result = UriAdapter.toJson(uri)

        assertEquals("https://example.com/path", result)
    }

    @Test
    fun `configuration json parses required fields`() {
        val adapter = Moshi.Builder()
            .add(UriAdapter)
            .addLast(KotlinJsonAdapterFactory())
            .build()
            .adapter(Configuration::class.java)
        val json = """
            {
              "schema_version": 2,
              "api_url": "https://example.com/api",
              "oidc_url": "https://example.com/oidc",
              "redirect_uri": "https://example.com/redirect",
              "property_id": "example-property"
            }
        """.trimIndent()

        val configuration = adapter.fromJson(json)!!

        assertEquals(2, configuration.schemaVersion)
        assertEquals("https://example.com/api", configuration.apiUrl.toString())
        assertEquals("https://example.com/oidc", configuration.oidcUrl.toString())
        assertEquals("https://example.com/redirect", configuration.redirectUri.toString())
        assertEquals("example-property", configuration.propertyId)
    }
}
