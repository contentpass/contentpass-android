package de.contentpass.lib

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class OneTimeTokenResponse(val oneTimeToken: String)