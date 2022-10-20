![contentpass](https://www.contentpass.de/img/logo.svg)



## Compatibility


> contentpass supports Android 5.0 (Lollipop, sdk api version 21) and above



## Installation

The SDK is available on a private maven repository. Add this to your `repositories` configuration:

```gro
maven {
    url 'https://repsy.io/mvn/0x7f/contentpass-android-tol'
    credentials {
        username YOUR_REPSY_USERNAME
        password YOUR_REPSY_PASSWORD
    }
}
```

Then add this to your app's `build.gradle` file's `dependencies` element.

```groovy
implementation 'de.contentpass:contentpass-android-tol:2.3.0'
```



## Configuration

You will be provided a `contentpass_configuration.json`. You will need this later to instantiate the contentpass object.

You will also need your `redirect_uri` that is specified in this json file to setup the capturing of the authentication redirect.
Since we use [AppAuth](https://github.com/openid/AppAuth-Android) for the OAuth process, the easiest way to properly set this up is in your app's `build.gradle` file.

An example `contentpass_configuration.json`:

```json
{
  "schema_version": 2,
  "api_url": "https://api.example.com",
  "oidc_url": "https://pur.example.com",
  "redirect_uri": "com.example.app://oauth",
  "property_id": "YOUR_PROPERTY_ID"
}
```

Extract the `redirect_uri` and place the scheme in your `build.gradle`:

```groovy
android.defaultConfig.manifestPlaceholders = [
  'appAuthRedirectScheme': 'com.example.app'
]
```

If that's not to your liking, please refer to this [documentation](https://github.com/openid/AppAuth-Android#capturing-the-authorization-redirect) for more options on how to register for capturing the redirect.

## Usage

We provide an example application in the `/app` app module. If you're unclear about the usage of some of our features, have a look and tinker around with the code. Refer to the example's [README](https://github.com/contentpass/contentpass-android/blob/main/app/README.md) for more information.

### The contentpass_configuration.json

We recommend to put this somewhere in your `res` folder, e.g. `res/raw/` or `res/json`.

### The contentpass object and its delegate

You should instantiate and hold one instance of the `ContentPass` class in one of your top level state holding objects. 
Even better would be providing the same instance to your view models via dependency injection.

The only way to instantiate the `ContentPass` class is via its `Builder`. Currently there's two configuration functions which are both required:


```kotlin
val config = context.resources
	.openRawResource(R.raw.contentpass_configuration)

val contentPass = ContentPass.Builder()
  .context(context)
  .configurationFile(config)
  .build()
```

If you want to be notified of changes in the authentication state, you should register a `ContentPass.Observer`:

```kotlin
contentPass.registerObserver(object : ContentPass.Observer {
  override fun onNewState(state: ContentPass.State) {
    // handle the new ContentPass.State
  }
})
```

To avoid memory leaks, don't forget to call `unregisterObserver` once you're done observing.

### Authentication

We use [AppAuth](https://github.com/openid/AppAuth-Android) for the OAuth 2.0 process. AppAuth uses web browsers to present the authentication flow to the user.

That means, that the user will - in most cases - temporarily leave your app, authenticate with the contentpass servers and finally return to your app. We then use the OAuth result to validate whether the user has any active contentpass subscriptions that are applicable.

Since you have setup the redirection scheme, your app will open again after the authentication due to an intent. Since this intent contains data that is necessary to validate the contentpasse subscriptions and authentication, the sdk needs to be registered as a callback for this intent data.

Therefore, you will need to register the sdk in every Activity or Fragment that you plan to start the authentication from. This needs to happen in the Activity's or Fragment's `onCreate` method since that's the only time and place where this is possible.

You just pass the activity or fragment via on of the two `registerActivityResultLauncher` functions:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
	contentPass.registerActivityResultLauncher(this)
}
```

Then at any time during the fragment's or activity's lifecycle you can call `authenticate`.

We supply two authentication methods: 

* `authenticateSuspending` for everyone that is able to use Kotlin coroutines
* `authenticate` for compatibility with Java or if coroutines are possible with your project

Both functions will need a `Context` instance from which to start the authentication flow from.

```kotlin
val state: ContentPass.State? = null
coroutineScope.launch {
	state = contentPass.authenticateSuspending(context)
}
```

or

```kotlin
contentPass.authenticate(fromActivity, object : ContentPass.AuthenticationCallback {
  override fun onSuccess(state: ContentPass.State) {
    // handle new state
  }

  override fun onFailure(exception: Throwable) {
    // handle exception
  }
})
```

If the authentication was a success, we will poll our servers for subscription plans in the background.

Any registered `Observer` will be called with the final authentication and subscription state. 

**Be aware that a successfully authenticated user may have no active subscription plans** and act accordingly!

### A few words on persistence

* We store tokens that anonymously identify the logged in user's session to our servers in the app's SharedPreferences. 
* This token data is encrypted and the keys are stored securely in the hardware backed (if available) KeyStore.
* We refresh these tokens automatically in the background before they're invalidated.
* The subscription information gets validated as well on every token refresh.

### Recovering from network errors

Sometimes we encounter an error state while refreshing the tokens in the background due to bad or no internet connection.

You will notice this because the `state` switched to `Error`. This state object contains a reference to the original exception that was thrown. 

Since we don't monitor the device's connection state you need to tell the SDK that the network connection has been reestablished / improved. We will then refresh and revalidate the user's authentication tokens.

```swift
contentPass.recoverFromError()
```

### Counting an impression

To count an impression, call either the suspending function `countImpressionSuspending(context: Context)` or the compatibility function `countImpression(context: Context, callback: CountImpressionCallback)`.

In both cases you'll need to supply a `Context` (i.e. an `Activity` or `Fragment`) so the login process can be started again in case the user needs to login again.

For an impression to be able to be counted, a user has to be authenticated and have an active subscription applicable to your scope.

```kotlin
try {
  contentPass.countImpressionSuspending(context)
  // handle success
} catch (impressionException: ContentPass.CountImpressionException) {
  // impressionException.message contains the http error code as a string
  // if this is "404", this most likely means that the user has no applicable subscription
} catch (ex: Throwable) {
  // Handle the exception. 
  // This might be network related but could also happen because the ContentPass object wasn't initialized properly.
}
```

or for compatibility reasons:

```kotlin
contentPass.countImpression(context, object : ContentPass.CountImpressionCallback {
  override fun onSuccess() {
    // handle success
  }

  override fun onFailure(exception: Throwable) {
    // handle the exception. 
    // If this is a ContentPass.CountImpressionException, parse its message for the http error code.
    // if this is "404", this most likely means that the user has no applicable subscription
  }
})
```



### Logging out a user

Since we persist the user's session, you need a way to log the user out. Simply call `logout` and we remove all stored token data.

```kotlin
contentPass.logout()
```

The user will of course have to log in again afterwards.
You can also call `authenticate` again and all previous user information will get overwritten. 
We only store *one* user session at any one time.

### Dashboard

If you need to display the user's dashboard where they can configure their subscription plans, you can do so by displaying the `ContentPassDashboardView`. The ContentPass object offers the function:

```kotlin
contentPass.provideDashboardView(context)
```

This `ContentPassDashboardView` is a `View` that you can present as a the content of an activity, fragment or in any other way you want.

## License

[MIT licensed](https://github.com/contentpass/contentpass-android/blob/main/LICENSE)

## Open Source

We use the following open source packages in this SDK:

* [AppAuth](https://github.com/openid/AppAuth-Android) for everything related to the OAuth 2.0 flow
* [Moshi](https://github.com/square/moshi) for JSON parsing
* [OkHttp](https://square.github.io/okhttp/) for HTTP requests

