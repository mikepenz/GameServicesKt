package com.mikepenz.gameservices

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Confined to the provider's main thread. Native observation outlives individual waiters. */
@InternalGameServicesApi
public class AuthenticationSession {
    private val state = MutableStateFlow<AuthenticationState>(AuthenticationState.Unauthenticated)
    public val authenticationState: StateFlow<AuthenticationState> = state
    private var installed = false
    private var pending: CompletableDeferred<Result<PlayerIdentity?>>? = null

    public suspend fun refresh(install: () -> Unit, current: () -> PlayerIdentity?): Result<PlayerIdentity?> {
        pending?.let { return it.await() }
        if (installed) return Result.success(current()).also(::complete)
        val request = CompletableDeferred<Result<PlayerIdentity?>>()
        pending = request
        installed = true
        state.value = AuthenticationState.Authenticating
        try {
            install()
        } catch (error: Exception) {
            installed = false
            complete(Result.failure(error))
        }
        return request.await()
    }

    public fun complete(result: Result<PlayerIdentity?>) {
        state.value = result.getOrNull()?.let(AuthenticationState::Authenticated) ?: AuthenticationState.Unauthenticated
        pending?.complete(result)
        pending = null
    }
}
