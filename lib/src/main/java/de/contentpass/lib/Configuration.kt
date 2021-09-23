package de.contentpass.lib

import android.net.Uri
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson

@JsonClass(generateAdapter = true)
internal data class Configuration(
    @Json(name = "schema_version")
    val schemaVersion: Int,
    @Json(name = "base_url")
    val baseUrl: Uri,
    @Json(name = "redirect_uri")
    val redirectUri: Uri,
    @Json(name = "property_id")
    val propertyId: String
)

internal object UriAdapter {
    @FromJson
    fun fromJson(string: String) = Uri.parse(string)

    @ToJson
    fun toJson(value: Uri) = value.toString()
}