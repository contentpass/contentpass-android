package de.contentpass.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.contentpass.lib.ContentPass

class ExampleViewModel(context: Context) : ViewModel() {
    private val _isAuthenticated = MutableLiveData(false)
    val isAuthenticated: LiveData<Boolean> = _isAuthenticated
    private val _hasValidSubscription = MutableLiveData(false)
    val hasValidSubscription: LiveData<Boolean> = _hasValidSubscription

    private val config = context.resources
        .openRawResource(R.raw.contentpass_configuration)

    private val contentPass = ContentPass.Builder()
        .context(context)
        .configurationFile(config)
        .build()

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

    fun logout() {
        contentPass.logout()
    }
}
