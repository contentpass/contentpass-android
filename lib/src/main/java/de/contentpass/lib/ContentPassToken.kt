package de.contentpass.lib

import android.util.Base64
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal data class ContentPassToken(private val tokenString: String) {
    @JsonClass(generateAdapter = true)
    internal data class Header(val alg: String)

    @JsonClass(generateAdapter = true)
    internal data class Body(
        val auth: Boolean,
        val plans: List<String>,
        val aud: String,
        val iat: Long,
        val exp: Long
    )

    private val header: Header
    private val body: Body
    val isSubscriptionValid: Boolean
        get() = body.auth && body.plans.isNotEmpty()

    init {
        val split = tokenString.split(".")

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val headerString = String(Base64.decode(split[0], Base64.DEFAULT))
        val headerAdapter = moshi.adapter(Header::class.java)
        header = headerAdapter.fromJson(headerString)!!

        val bodyString = String(Base64.decode(split[1], Base64.DEFAULT))
        val bodyAdapter = moshi.adapter(Body::class.java)
        body = bodyAdapter.fromJson(bodyString)!!
    }
}

@JsonClass(generateAdapter = true)
internal data class ContentPassTokenResponse(
    @Json(name = "contentpass_token")
    val contentPassToken: String
) {
    @Transient
    private var token: ContentPassToken? = null

    fun getToken(): ContentPassToken {
        token?.let {
            return it
        } ?: run {
            token = ContentPassToken(contentPassToken)
            return token!!
        }
    }
}
