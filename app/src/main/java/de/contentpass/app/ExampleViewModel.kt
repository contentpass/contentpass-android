package de.contentpass.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.contentpass.lib.ContentPass
import de.contentpass.lib.ContentPassDashboardView

class ExampleViewModel(context: Context) : ViewModel() {
    private val _isAuthenticated = MutableLiveData(false)
    val isAuthenticated: LiveData<Boolean> = _isAuthenticated
    private val _hasValidSubscription = MutableLiveData(false)
    val hasValidSubscription: LiveData<Boolean> = _hasValidSubscription

    private val _impressionTries = MutableLiveData(0)
    val impressionTries: LiveData<Int> = _impressionTries
    private val _impressionSuccesses = MutableLiveData(0)
    val impressionSuccesses: LiveData<Int> = _impressionSuccesses

    private val config = context.resources
        .openRawResource(R.raw.contentpass_configuration)

    private val contentPass = ContentPass.Builder()
        .context(context)
        .configurationFile(config)
        .build()

    fun openDashboard(context: Context): ContentPassDashboardView {
        return contentPass.provideDashboardView(context)
    }

    fun onActivityCreate(activity: ComponentActivity) {
        contentPass.registerActivityResultLauncher(activity)
        contentPass.registerObserver(object : ContentPass.Observer {
            override fun onNewState(state: ContentPass.State) {
                onNewContentPassState(state)
            }
        })
    }

    private fun onNewContentPassState(state: ContentPass.State) {
        when (state) {
            is ContentPass.State.Unauthenticated -> {
                _isAuthenticated.postValue(false)
                _hasValidSubscription.postValue(false)
            }
            is ContentPass.State.Authenticated -> {
                _isAuthenticated.postValue(true)
                _hasValidSubscription.postValue(state.hasValidSubscription)
            }
            ContentPass.State.Initializing -> Unit
        }
    }

    suspend fun login(fromActivity: ComponentActivity) {
        val state = contentPass.authenticateSuspending(fromActivity)

        onNewContentPassState(state)
    }

    suspend fun countImpression(fromActivity: ComponentActivity) {
        _impressionTries.postValue(_impressionTries.value?.plus(1) ?: 1)

        try {
            contentPass.countImpressionSuspending(fromActivity)
            _impressionSuccesses.postValue(_impressionSuccesses.value?.plus(1) ?: 1)
        } catch (impressionException: ContentPass.CountImpressionException) {
            println("Counting impression error code: ${impressionException.message}")
        } catch (ex: Throwable) {
            println("Counting impression exception:")
            ex.printStackTrace()
        }
    }

    fun logout() {
        contentPass.logout()
    }
}
