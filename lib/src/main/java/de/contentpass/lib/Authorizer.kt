package de.contentpass.lib

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import net.openid.appauth.*
import okhttp3.*
import okio.IOException
import java.util.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


internal interface Authorizing {
    suspend fun authenticate(
        activity: Context,
        activityResultLauncher: ActivityResultLauncher<Intent>,
    ): AuthState

    suspend fun validateSubscription(idToken: String): Boolean

    suspend fun refreshToken(authState: AuthState): AuthState

    fun onAuthorizationRequestResult(intent: Intent?)

    suspend fun countImpression(authState: AuthState, activity: Context)

    suspend fun grabOneTimeToken(authState: AuthState, activity: Context): String
}

internal class Authorizer(
    configuration: Configuration,
    private val context: Context,
) : Authorizing {
    private val apiUrl = configuration.apiUrl
    private val oidcUrl = configuration.oidcUrl
    private val redirectUri = configuration.redirectUri
    private val propertyId = configuration.propertyId

    private lateinit var configuration: AuthorizationServiceConfiguration
    private var authService: AuthorizationService? = null
    private var authenticationContinuation: Continuation<AuthState>? = null
    private var authState: AuthState? = null

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    init {
        val context = Dispatchers.Default + Job()
        CoroutineScope(context).launch {
            fetchConfig()
        }
    }

    private suspend fun fetchConfig(): AuthorizationServiceConfiguration {
        return if (this::configuration.isInitialized) {
            configuration
        } else {
            doFetchConfig()
        }
    }

    private suspend fun doFetchConfig(): AuthorizationServiceConfiguration =
        suspendCoroutine { cont ->
            AuthorizationServiceConfiguration.fetchFromIssuer(oidcUrl) { config, ex ->
                if (ex != null) {
                    cont.resumeWith(Result.failure(ex))
                } else {
                    config?.let { it ->
                        configuration = it
                        cont.resumeWith(Result.success(it))
                    }
                        ?: cont.resumeWith(Result.failure(NullPointerException("Service discovery failed")))
                }
            }
        }

    override suspend fun authenticate(
        activity: Context,
        activityResultLauncher: ActivityResultLauncher<Intent>,
    ): AuthState {
        val request = buildRequest()
        val result: AuthState = suspendCoroutine { cont ->
            try {
                authService = AuthorizationService(activity)
                val authIntent = authService?.getAuthorizationRequestIntent(request)
                authenticationContinuation = cont
                activityResultLauncher.launch(authIntent)
            } catch (exception: Exception) {
                cont.resumeWith(Result.failure(exception))
            }
        }
        return result
    }

    override fun onAuthorizationRequestResult(intent: Intent?) {
        intent?.let {
            val resp = AuthorizationResponse.fromIntent(it)
            val exception = AuthorizationException.fromIntent(it)

            resp?.let { response ->
                authState = AuthState(configuration)
                authState?.update(response, exception)
                val tokenRequest = response.createTokenExchangeRequest()
                fireTokenRequest(tokenRequest)
            } ?: run {
                val resultingException =
                    exception ?: NullPointerException("intent data is empty")
                authenticationContinuation?.resumeWith(Result.failure(resultingException))
            }
        } ?: run {
            authenticationContinuation?.resumeWith(Result.failure(NullPointerException("intent data is empty")))
        }
    }

    private fun fireTokenRequest(request: TokenRequest) {
        authService?.performTokenRequest(request) { response, exception ->
            val result = response?.let {
                authState?.let {
                    it.update(response, exception)
                    Result.success(it)
                } ?: run {
                    val message =
                        "authState should have just been created in onAuthorizationRequestResult"
                    Result.failure(NullPointerException(message))
                }
            } ?: run {
                val resultingException =
                    exception ?: NullPointerException("Should have exception since no response")
                Result.failure(resultingException)
            }
            authenticationContinuation?.resumeWith(result)
            authState = null
            authenticationContinuation = null
        }
    }

    override suspend fun countImpression(authState: AuthState, activity: Context) {
        val impressionId = UUID.randomUUID()
        val path = "pass/hit?pid=$propertyId&iid=$impressionId&t=pageview"

        val response = fireApiRequestWithFreshTokens(path, authState, activity)

        if (response.code == 200) {
            return
        } else {
            throw ContentPass.CountImpressionException(response.code)
        }
    }

    override suspend fun grabOneTimeToken(authState: AuthState, activity: Context): String {
        val path = "auth/login/ott?propertyId=$propertyId"
        val response = fireApiRequestWithFreshTokens(path, authState, activity)
        return parseOneTimeToken(response)
    }

    private suspend fun fireApiRequestWithFreshTokens(
        path: String,
        authState: AuthState,
        context: Context,
    ): Response {
        val client = OkHttpClient.Builder()
            .build()

        return suspendCoroutine { continuation ->
            try {
                val authService = AuthorizationService(context)
                authState.performActionWithFreshTokens(authService) { accessToken, _, ex ->
                    ex?.let {
                        continuation.resumeWithException(it)
                        return@performActionWithFreshTokens
                    }

                    accessToken?.let { strongToken ->
                        val authorizedRequest = Request.Builder()
                            .url("$apiUrl/$path")
                            .header("Authorization", "Bearer $strongToken")
                            .build()

                        val response = client.newCall(authorizedRequest).execute()
                        continuation.resumeWith(Result.success(response))
                    } ?: run {
                        val message =
                            "Although no AuthenticationException was thrown, there's no accessToken - please report this via GitHub issues"
                        continuation.resumeWithException(NullPointerException(message))
                    }
                }
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
            }
        }
    }

    private suspend fun buildRequest(): AuthorizationRequest {
        return AuthorizationRequest.Builder(
            fetchConfig(),
            propertyId,
            ResponseTypeValues.CODE,
            redirectUri
        )
            .setScopes("openid", "offline_access", "contentpass")
            .setPrompt("consent")
            .setAdditionalParameters(
                mutableMapOf(
                    Pair("cp_route", "login")
                )
            ).build()
    }

    private fun createValidationBody(idToken: String): FormBody {
        return FormBody.Builder()
            .add("grant_type", "contentpass_token")
            .add("client_id", propertyId)
            .add("subject_token", idToken)
            .build()
    }

    private suspend fun createValidationRequest(idToken: String): Request {
        val body = createValidationBody(idToken)

        return Request.Builder()
            .url(fetchConfig().tokenEndpoint.toString())
            .post(body)
            .build()
    }

    override suspend fun validateSubscription(idToken: String): Boolean {
        val client = OkHttpClient.Builder()
            .build()
        val request = createValidationRequest(idToken)

        return suspendCoroutine { cont ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result: Result<Boolean> = if (response.isSuccessful) {
                        try {
                            val contentPassToken = parseToken(response)
                            Result.success(contentPassToken.isSubscriptionValid)
                        } catch (exc: Throwable) {
                            Result.failure(exc)
                        }
                    } else {
                        Result.failure(IOException("Unexpected status code $response"))
                    }
                    cont.resumeWith(result)
                }
            })
        }
    }

    override suspend fun refreshToken(authState: AuthState): AuthState {
        authService = AuthorizationService(context)
        authState.needsTokenRefresh = true
        return suspendCoroutine { cont ->
            authState.performActionWithFreshTokens(authService!!) { _, _, exception ->
                if (exception != null) {
                    cont.resumeWith(Result.failure(exception))
                } else {
                    cont.resumeWith(Result.success(authState))
                }
            }
        }
    }

    private fun parseToken(response: Response): ContentPassToken {
        val string = response.body!!.string()
        val adapter = moshi.adapter(ContentPassTokenResponse::class.java)
        val contentPassTokenResponse = adapter.fromJson(string)
        return contentPassTokenResponse!!.getToken()
    }

    private fun parseOneTimeToken(response: Response): String {
        val responseString = response.body!!.string()
        val adapter = moshi.adapter(OneTimeTokenResponse::class.java)
        val oneTimeTokenResponse = adapter.fromJson(responseString)
        return oneTimeTokenResponse!!.oneTimeToken
    }
}
