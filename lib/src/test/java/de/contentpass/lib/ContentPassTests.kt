package de.contentpass.lib

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import net.openid.appauth.AuthState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.NullPointerException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ContentPassTests {
    private lateinit var mockUri: Uri
    private lateinit var exampleConfiguration: Configuration

    @Before
    fun setUp() {
        mockkStatic(Uri::class)

        mockUri = mockk()
        every { mockUri.toString() } returns "https://example.com"
        every { mockUri.scheme } returns "https"
        every { mockUri.host } returns "example.com"
        every { mockUri.path } returns "/"
        every { Uri.parse(any()) } returns mockUri


        exampleConfiguration = Configuration(
            2,
            Uri.parse("https://example.com/api"),
            Uri.parse("https://example.com/oidc"),
            Uri.parse("https://example.com/redirect"),
            "example"
        )
    }

    @Test
    fun `initialization without stored state results in Unauthenticated`() {
        val store = MockedTokenStore()
        assertNull(store.retrieveAuthState())
        val contentPass = ContentPass(mockk(relaxed = true), store, exampleConfiguration)

        assertEquals(ContentPass.State.Unauthenticated, contentPass.state)
    }

    @Test
    fun `initialization with unauthenticated state results in Unauthenticated`() {
        val state: AuthState = mockk()
        every { state.isAuthorized }.returns(false)
        val store = MockedTokenStore()
        store.storeAuthState(state)

        val contentPass = ContentPass(mockk(relaxed = true), store, exampleConfiguration)

        Thread.sleep(10)

        assertEquals(ContentPass.State.Unauthenticated, contentPass.state)
    }

    @Test
    fun `initialization with authorized state and valid subscription results in Authenticated`() {
        val state: AuthState = mockk()
        every { state.isAuthorized }.returns(true)
        every { state.idToken }.returns("id-token")
        every { state.accessTokenExpirationTime }.returns(System.currentTimeMillis() + 5000)
        val store = MockedTokenStore()
        store.storeAuthState(state)
        val authorizer: Authorizing = mockk()
        coEvery { authorizer.validateSubscription("id-token") }.returns(true)

        val contentPass = ContentPass(authorizer, store, exampleConfiguration)

        waitUntil { contentPass.state is ContentPass.State.Authenticated }

        assertTrue(contentPass.state is ContentPass.State.Authenticated)
        assertTrue((contentPass.state as ContentPass.State.Authenticated).hasValidSubscription)
        cancelRefreshTimer(contentPass)
    }

    @Test
    fun `initialization with authorized state and invalid subscription results in Authenticated without subscription`() {
        val state: AuthState = mockk()
        every { state.isAuthorized }.returns(true)
        every { state.idToken }.returns("id-token")
        every { state.accessTokenExpirationTime }.returns(System.currentTimeMillis() + 5000)
        val store = MockedTokenStore()
        store.storeAuthState(state)
        val authorizer: Authorizing = mockk()
        coEvery { authorizer.validateSubscription("id-token") }.returns(false)

        val contentPass = ContentPass(authorizer, store, exampleConfiguration)

        waitUntil { contentPass.state is ContentPass.State.Authenticated }

        assertTrue(contentPass.state is ContentPass.State.Authenticated)
        assertFalse((contentPass.state as ContentPass.State.Authenticated).hasValidSubscription)
        cancelRefreshTimer(contentPass)
    }

    @Test
    fun `initialization with authorized state and subscription validation failure results in Error`() {
        val state: AuthState = mockk()
        every { state.isAuthorized }.returns(true)
        every { state.idToken }.returns("id-token")
        every { state.accessTokenExpirationTime }.returns(System.currentTimeMillis() + 5000)
        val store = MockedTokenStore()
        store.storeAuthState(state)
        val authorizer: Authorizing = mockk()
        coEvery { authorizer.validateSubscription("id-token") }.throws(IllegalStateException("validation failed"))

        val contentPass = ContentPass(authorizer, store, exampleConfiguration)

        waitUntil { contentPass.state is ContentPass.State.Error }

        assertTrue(contentPass.state is ContentPass.State.Error)
        cancelRefreshTimer(contentPass)
    }

    @Test
    fun `authenticateSuspending sets state to authenticated on successful authentication`() =
        runBlocking {
            val state: AuthState = mockk()
            coEvery { state.isAuthorized }.returns(true)
            coEvery { state.idToken }.returns("")
            coEvery { state.accessTokenExpirationTime }.returns(System.currentTimeMillis() + 5000)
            val authorizer: Authorizer = mockk()
            coEvery { authorizer.validateSubscription(any()) }.returns(true)
            coEvery { authorizer.authenticate(any(), any()) }.returns(state)

            val contentPass = ContentPass(authorizer, mockk(relaxed = true), exampleConfiguration)
            val activity: ComponentActivity = mockk(relaxed = true)
            contentPass.registerActivityResultLauncher(activity)

            val result = contentPass.authenticateSuspending(mockk())

            assertTrue(result is ContentPass.State.Authenticated)
            assertTrue(contentPass.state is ContentPass.State.Authenticated)
            cancelRefreshTimer(contentPass)
        }

    @Test
    fun `calling authenticate before registerActivityResultLauncher results in NullPointerException`() =
        runBlocking {
            val contentPass = ContentPass(mockk(relaxed = true), mockk(relaxed = true), exampleConfiguration)

            try {
                contentPass.authenticateSuspending(mockk())
                fail("Expected authentication without activityResultLauncher to throw")
            } catch (e: NullPointerException) {
                assertTrue(true)
            } catch (e: Throwable) {
                fail("Expected NullPointerException, got ${e::class.java.name}")
            }
        }

    @Test
    fun `authenticate callback calls onFailure when activity result launcher is missing`() {
        val contentPass = ContentPass(mockk(relaxed = true), mockk(relaxed = true), exampleConfiguration)
        val latch = CountDownLatch(1)
        var failure: Throwable? = null

        contentPass.authenticate(mockk(), object : ContentPass.AuthenticationCallback {
            override fun onSuccess(state: ContentPass.State) {
                latch.countDown()
            }

            override fun onFailure(exception: Throwable) {
                failure = exception
                latch.countDown()
            }
        })

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertTrue(failure is NullPointerException)
    }

    @Test
    fun `registerActivityResultLauncher for activity registers for activity result`() {
        val contentPass = ContentPass(mockk(relaxed = true), mockk(relaxed = true), exampleConfiguration)
        val activity: ComponentActivity = mockk(relaxed = true)

        contentPass.registerActivityResultLauncher(activity)

        verify {
            activity.registerForActivityResult(
                any<ActivityResultContract<Intent, ActivityResult>>(),
                any()
            )
        }
    }

    @Test
    fun `registerActivityResultLauncher for fragment registers for activity result`() {
        val contentPass = ContentPass(mockk(relaxed = true), mockk(relaxed = true), exampleConfiguration)
        val fragment: Fragment = mockk(relaxed = true)

        contentPass.registerActivityResultLauncher(fragment)

        verify {
            fragment.registerForActivityResult(
                any<ActivityResultContract<Intent, ActivityResult>>(),
                any()
            )
        }
    }

    @Test
    fun `logout sets state to Unauthenticated`() = runBlocking {
        val authState: AuthState = mockk(relaxed = true)
        every { authState.isAuthorized }.returns(true)
        every { authState.accessTokenExpirationTime }.returns(System.currentTimeMillis() + 5000)
        val authorizer: Authorizing = mockk()
        coEvery { authorizer.validateSubscription(any()) }.returns(true)
        coEvery { authorizer.authenticate(any(), any()) }.returns(authState)
        val contentPass = ContentPass(authorizer, mockk(relaxed = true), exampleConfiguration)

        contentPass.registerActivityResultLauncher(mockk<Fragment>(relaxed = true))
        contentPass.authenticateSuspending(mockk())
        assertNotEquals(ContentPass.State.Unauthenticated, contentPass.state)

        contentPass.logout()

        assertEquals(ContentPass.State.Unauthenticated, contentPass.state)
        cancelRefreshTimer(contentPass)
    }

    @Test
    fun `logout removes stored auth state information`() {
        val store = MockedTokenStore()
        store.storeAuthState(mockk())
        val contentPass = ContentPass(mockk(relaxed = true), store, exampleConfiguration)

        assertNotNull(store.retrieveAuthState())

        contentPass.logout()

        assertNull(store.retrieveAuthState())
    }

    @Test
    fun `registerObserver adds an observer that gets called on state changes`() {
        val contentPass = ContentPass(mockk(relaxed = true), mockk(relaxed = true), exampleConfiguration)

        var stateResult: ContentPass.State = ContentPass.State.Initializing

        contentPass.registerObserver(object : ContentPass.Observer {
            override fun onNewState(state: ContentPass.State) {
                stateResult = state
            }
        })

        contentPass.logout()

        assertEquals(ContentPass.State.Unauthenticated, stateResult)
    }

    @Test
    fun `registerObserver does not add the same observer twice`() {
        val contentPass = ContentPass(mockk(relaxed = true), mockk(relaxed = true), exampleConfiguration)
        var callCount = 0
        val observer = object : ContentPass.Observer {
            override fun onNewState(state: ContentPass.State) {
                callCount += 1
            }
        }

        contentPass.registerObserver(observer)
        contentPass.registerObserver(observer)

        contentPass.logout()

        assertEquals(1, callCount)
    }

    @Test
    fun `unregisterObserver removes an observer`() {
        val contentPass = ContentPass(mockk(relaxed = true), mockk(relaxed = true), exampleConfiguration)

        var stateResult: ContentPass.State = ContentPass.State.Initializing
        val observer = object : ContentPass.Observer {
            override fun onNewState(state: ContentPass.State) {
                stateResult = state
            }
        }

        contentPass.registerObserver(observer)
        contentPass.unregisterObserver(observer)

        contentPass.logout()

        assertEquals(ContentPass.State.Initializing, stateResult)
    }

    @Test
    fun `recoverFromError without stored state results in Unauthenticated`() {
        val store = MockedTokenStore()
        val contentPass = ContentPass(mockk(relaxed = true), store, exampleConfiguration)

        contentPass.recoverFromError()

        assertEquals(ContentPass.State.Unauthenticated, contentPass.state)
    }

    @Test
    fun `recoverFromError with unauthenticated stored state results in Unauthenticated`() {
        val state: AuthState = mockk()
        every { state.isAuthorized }.returns(false)
        val store = MockedTokenStore()
        store.storeAuthState(state)
        val contentPass = ContentPass(mockk(relaxed = true), store, exampleConfiguration)

        contentPass.recoverFromError()
        waitUntil { contentPass.state == ContentPass.State.Unauthenticated }

        assertEquals(ContentPass.State.Unauthenticated, contentPass.state)
    }

    private fun waitUntil(assertion: () -> Boolean) {
        val timeoutAt = System.currentTimeMillis() + 1000
        while (!assertion() && System.currentTimeMillis() < timeoutAt) {
            Thread.sleep(10)
        }
    }

    private fun cancelRefreshTimer(contentPass: ContentPass) {
        val field = ContentPass::class.java.getDeclaredField("refreshTimer")
        field.isAccessible = true
        (field.get(contentPass) as? java.util.Timer)?.cancel()
    }
}