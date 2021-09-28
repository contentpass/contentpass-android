package de.contentpass.lib

import net.openid.appauth.AuthState

internal interface TokenStoring {
    fun storeAuthState(authState: AuthState)
    fun retrieveAuthState(): AuthState?
    fun deleteAuthState()
}
