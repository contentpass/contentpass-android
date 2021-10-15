package de.contentpass.lib

import net.openid.appauth.AuthState

class MockedTokenStore: TokenStoring {
    private var authState: AuthState? = null

    override fun storeAuthState(authState: AuthState) {
        this.authState = authState
    }

    override fun retrieveAuthState(): AuthState? {
        return authState
    }

    override fun deleteAuthState() {
        authState = null
    }
}