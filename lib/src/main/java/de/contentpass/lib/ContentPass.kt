package de.contentpass.lib

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import okhttp3.Response
import java.io.InputStream
import java.io.IOException
import java.lang.NullPointerException
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.coroutines.suspendCoroutine

const val SAMPLING_RATE = 0.05

data class SampleImpressionData(
    val ea: String,
    val ec: String,
    val cpabid: String,
    val cppid: String,
    val cpsr: Double
)

/**
 * An object that handles all communication with the contentpass servers for you.
 *
 * Use [ContentPass.Builder] to construct the object.
 *
 * Its core functionality is authenticating a contentpass user via OAuth 2.0 and afterwards
 * validating whether the authenticated user has a valid contentpass subscription plan.
 *
 * User information gets persisted securely encrypted in your app's SharedPreferences in and the
 * relevant tokens get refreshed automatically, therefore the subscription information is always
 * up to date.
 *
 * You should only have one instance of this object at any one time.
 * We recommend dependency injection via e.g. Hilt or Koin.
 *
 * Don't forget to register a [ContentPass.Observer] to be able to react to authentication or
 * subscription state changes.
 *
 */
class ContentPass internal constructor(
    private val authorizer: Authorizing,
    private val tokenStore: TokenStoring,
    private val configuration: Configuration
) {
    /**
     * A collection of functions that allow you to react to changes in the [ContentPass] object.
     * See [ContentPass.registerObserver] and [ContentPass.unregisterObserver]
     */
    interface Observer {
        /**
         * A function that enables you to react to a change in [ContentPass.state]
         *
         * @param state the new [ContentPass.State] of the [ContentPass] object
         */
        fun onNewState(state: State)
    }

    interface AuthenticationCallback {
        fun onSuccess(state: State)
        fun onFailure(exception: Throwable)
    }

    class CountImpressionException(statusCode: Int) : Throwable("$statusCode")

    interface CountImpressionCallback {
        fun onSuccess()
        fun onFailure(exception: Throwable)
    }

    class Builder {
        private var context: Context? = null
        private var configuration: Configuration? = null
        private var configurationFile: InputStream? = null

        fun context(value: Context): Builder {
            context = value
            return this
        }

        /**
         * @param value the [InputStream] should be your *contentpass_configuration.json* file.
         */
        fun configurationFile(value: InputStream): Builder {
            configurationFile = value
            return this
        }

        /**
         * Returns the configured [ContentPass] object.
         *
         * You need to have called the [context] and [configurationFile] functions before this.
         */
        fun build(): ContentPass {
            configuration = grabConfiguration()
            val authorizer = Authorizer(configuration!!, context!!)
            val store = TokenStore(context!!, KeyStore(context!!))
            return ContentPass(authorizer, store, configuration!!)
        }

        private fun grabConfiguration(): Configuration? {
            val adapter = Moshi.Builder()
                .add(UriAdapter)
                .addLast(KotlinJsonAdapterFactory())
                .build()
                .adapter(Configuration::class.java)

            val jsonString = configurationFile!!
                .bufferedReader()
                .use { it.readText() }

            return adapter.fromJson(jsonString)
        }
    }

    /**
     * The possible contentpass authentication states.
     */
    sealed class State {
        /**
         * The contentpass object was just created or an error recovery is ongoing.
         * Will switch to another state very soon.
         *
         * After the stored contentpass token information is validated and refreshed, this will
         * switch to one of [Error], [Unauthenticated] or [Authenticated]
         */
        object Initializing : State()

        /**
         * An error was encountered during token validation.
         *
         * This is probably due to a failing internet connection. You can check the exception and
         * act accordingly. Once a stable network connection has been established, call [recoverFromError]
         * to retry the token validation.
         */
        class Error(val exception: Throwable): State()

        /**
         * No user is currently authenticated.
         */
        object Unauthenticated : State()

        /**
         * The user has authenticated themselves successfully with our services.
         *
         * Since an authenticated user might not have an active subscription,
         * you should always check the [hasValidSubscription] property.
         */
        class Authenticated(val hasValidSubscription: Boolean) : State()
    }

    /**
     * The current authentication state of the contentpass sdk.
     *
     * This is always up to date but to be notified of changes in state, be sure to register an
     * [Observer].
     */
    var state: State = State.Initializing
        private set(value) {
            field = value
            observers.map { it.onNewState(value) }
        }

    private lateinit var authState: AuthState

    private var activityResultLauncher: ActivityResultLauncher<Intent>? = null

    private var refreshTimer: Timer? = null

    private val observers = mutableListOf<Observer>()

    private val coroutineContext = Dispatchers.Default + SupervisorJob()

    init {
        initializeAuthState()
    }

    private fun initializeAuthState() {
        tokenStore.retrieveAuthState()?.let {
            authState = it
            CoroutineScope(coroutineContext).launch {
                onNewAuthState(authState)
            }
        } ?: run {
            state = State.Unauthenticated
        }
    }

    /**
     * In any [ComponentActivity] that contains a UI element that will start the authentication process, you
     * will need to call this method in the [ComponentActivity.onCreate] function.
     *
     * The sdk will then register the necessary callbacks to be able to react to the intent that
     * follows an authentication attempt.
     *
     * See [ComponentActivity.registerForActivityResult] for more information on how this works
     * under the hood.
     */
    fun registerActivityResultLauncher(forActivity: ComponentActivity) {
        val contract = ActivityResultContracts.StartActivityForResult()
        activityResultLauncher = forActivity.registerForActivityResult(contract) {
            authorizer.onAuthorizationRequestResult(it.data)
        }
    }

    /**
     * In any [Fragment] that contains a UI element that will start the authentication process, you
     * will need to call this method in the [Fragment.onCreate] function.
     *
     * The sdk will then register the necessary callbacks to be able to react to the intent that
     * follows an authentication attempt.
     *
     * See [Fragment.registerForActivityResult] for more information on how this works
     * under the hood.
     */
    fun registerActivityResultLauncher(forFragment: Fragment) {
        val contract = ActivityResultContracts.StartActivityForResult()
        activityResultLauncher = forFragment.registerForActivityResult(contract) {
            authorizer.onAuthorizationRequestResult(it.data)
        }
    }

    /**
     * Registers an [Observer] that will be notified of any changes in contentpass state.
     *
     * Be sure to [unregisterObserver] your observer once its lifecycle calls for it
     * to avoid memory leaks.
     */
    fun registerObserver(observer: Observer) {
        if (!observers.contains(observer)) {
            observers.add(observer)
        }
    }

    /**
     * Removes the [Observer] from the internal list.
     * This will enable it to be garbage collected (at least from our side).
     */
    fun unregisterObserver(observer: Observer) {
        observers.remove(observer)
    }

    /**
     * Starts the authentication flow for the user.
     *
     * Depending on the user's Android version this will use some form of web browser for the
     * OAuth process.
     *
     * **Important**: You need to have called either one of [registerActivityResultLauncher]
     * beforehand. Otherwise the sdk will not be notified of the result of the OAuth flow.
     *
     * @param context the context the authentication flow will be started from.
     * @return the resulting authentication [State]
     */
    suspend fun authenticateSuspending(context: Context): State {
        activityResultLauncher?.let {
            authState = authorizer.authenticate(context, it)
            return onNewAuthState(authState)
        } ?: run {
            val message = "activityResultLauncher is null! -- " +
                    "You need to call a version of registerActivityResultLauncher before calling authenticate"
            throw NullPointerException(message)
        }
    }

    /**
     * Starts the authentication flow for the user.
     *
     * This is the compatibility function for Java users and developers who are not yet
     * comfortable or able to use kotlin coroutines.
     *
     * Depending on the user's Android version this will use some form of web browser for the
     * OAuth process.
     *
     * **Important**: You need to have called either one of [registerActivityResultLauncher]
     * beforehand. Otherwise the sdk will not be notified of the result of the OAuth flow.
     *
     * @param context the context the authentication flow will be started from.
     * @param callback an object implementing the [AuthenticationCallback] interface that enables
     *                 you to react to the authentication flow's outcome.
     */
    fun authenticate(context: Context, callback: AuthenticationCallback) {
        CoroutineScope(coroutineContext).launch {
            try {
                val result = authenticateSuspending(context)
                callback.onSuccess(result)
            } catch (exception: Throwable) {
                callback.onFailure(exception)
            }
        }
    }

    /**
     * Removes all saved information regarding the currently logged in user.
     *
     * This also purges all persistent information from the device.
     * A user will have to login again after you call this method.
     */
    fun logout(): State {
        tokenStore.deleteAuthState()
        state = State.Unauthenticated
        return state
    }

    /**
     * Count an impression by calling this function.
     *
     * This is the compatibility function for Java users and developers who are not yet
     * comfortable or able to use kotlin coroutines.
     *
     * This function calls the callback's onSuccess on a successful impression counting.
     * In case of an error the callback's onFailure contains an exception containing more information.
     *
     * @param context the context the authentication flow can be restarted from in case a login is necessary.
     * @param callback an object implementing the [CountImpressionCallback] interface that enables
     *                 you to react to the count impression outcome.
     */
    fun countImpression(context: Context, callback: CountImpressionCallback) {
        CoroutineScope(coroutineContext).launch {
            try {
                countImpressionSuspending(context)
                callback.onSuccess()
            } catch (exception: Throwable) {
                callback.onFailure(exception)
            }
        }
    }

    /**
     * Count an impression by calling this function.
     *
     * This function simply returns on success or will throw an exception containing more information.
     *
     * @param context the context the authentication flow can be restarted from in case a login is necessary.
     */
    suspend fun countImpressionSuspending(context: Context) {
        return withContext(coroutineContext) {
            if (state is State.Authenticated && (state as State.Authenticated).hasValidSubscription) {
                authorizer.countPaidImpression(authState, context)
            }


            countSampledImpression()
        }
    }
    /**
     * Reinitializes this object's state.
     *
     * Call this function when you encountered an error during token validation, the current [state]
     * is set to [Error] and you want to try the validation again. Commonly used when network access
     * has been reestablished.
     */
    fun recoverFromError() {
        state = State.Initializing

        initializeAuthState()
    }


    private suspend fun countSampledImpression() {
        val generatedSample = Math.random()
        if (generatedSample >= SAMPLING_RATE) {
            return
        }

        val instanceId = UUID.randomUUID().toString()
        val publicId = configuration.propertyId.substring(0, 8)
        val sampleImpressionData = SampleImpressionData(
            ea = "load",
            ec = "tcf-sampled",
            cpabid = instanceId,
            cppid = publicId,
            cpsr = SAMPLING_RATE
        )
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        val jsonAdapter = moshi.adapter(SampleImpressionData::class.java)
        val jsonBody = jsonAdapter.toJson(sampleImpressionData)

        val responseCode = postRequest("${configuration.apiUrl}/stats", jsonBody);
        if (responseCode < 200 || responseCode >= 300) {
            throw CountImpressionException(responseCode)
        }
    }


    private suspend fun postRequest(url: String, jsonBody: String): Int {
        val client = OkHttpClient()
        val requestBody = jsonBody.toRequestBody("application/json; charset=UTF-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json; charset=UTF-8")
            .build()

        return suspendCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWith(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resumeWith(Result.success(response.code))
                }
            })
        }
    }

    private suspend fun onNewAuthState(authState: AuthState): State {
        tokenStore.storeAuthState(authState)

        state = if (authState.isAuthorized) {
            setupRefreshTimer(authState)?.let {
                if (it) {
                    authState.idToken?.let { idToken ->
                        try {
                            val hasSubscription = authorizer.validateSubscription(idToken)
                            State.Authenticated(hasSubscription)
                        } catch (e: Throwable) {
                            State.Error(e)
                        }
                    } ?: State.Unauthenticated
                } else {
                    state
                }
            } ?: State.Unauthenticated
        } else {
            State.Unauthenticated
        }
        return state
    }

    /**
     * @return false: Token will get refreshed instantly
     * @return true: Token refresh timer has been set
     * @return null: there's no expiration time set for current tokens
     */
    private fun setupRefreshTimer(authState: AuthState): Boolean? {
        return authState.accessTokenExpirationTime?.let { validUntil ->
            val now = System.currentTimeMillis()
            val timeDifference = validUntil - now
            return if (timeDifference <= 0) {
                refreshToken(0)
                false
            } else {
                refreshTimer?.cancel()
                refreshTimer = Timer()
                refreshTimer?.schedule(
                    object : TimerTask() {
                        override fun run() {
                            refreshToken(0)
                        }
                    },
                    timeDifference
                )
                true
            }
        }
    }

    private fun refreshToken(counter: Int) {
        CoroutineScope(coroutineContext).launch {
            try {
                authState = authorizer.refreshToken(authState)
                onNewAuthState(authState)
            } catch (authException: AuthorizationException) {
                if (authException.code == 2002) {
                    state = State.Unauthenticated
                } else {
                    onRefreshTokenException(counter, authException)
                }
            } catch (exception: Throwable) {
                onRefreshTokenException(counter, exception)
            }
        }
    }

    private suspend fun onRefreshTokenException(counter: Int, throwable: Throwable) {
        if (counter < 7) {
            val del = (counter * 10 * 1000).toLong()
            val message = "Encountered exception during token refresh. " +
                    "Will retry in ${(del / 1000)} seconds. \nEncountered Exception: $throwable"
            Log.e(null, message)
            delay(del)
            refreshToken(counter + 1)
        } else {
            val message =
                "Token retry failed ${counter - 1} times, removing login credentials"
            Log.e(null, message)
            state = State.Unauthenticated
            tokenStore.deleteAuthState()
        }
    }
}
