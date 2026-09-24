package com.mikepenz.gameservices

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Confined to the provider's main thread. Native observation outlives individual waiters. */
internal class AuthenticationSession {
    private val state = MutableStateFlow<AuthenticationState>(AuthenticationState.Unauthenticated)
    val authenticationState: StateFlow<AuthenticationState> = state
    private var installed = false
    private var pending: CompletableDeferred<Result<PlayerIdentity?>>? = null

    suspend fun refresh(install: () -> Unit, current: () -> PlayerIdentity?): Result<PlayerIdentity?> {
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

    fun complete(result: Result<PlayerIdentity?>) {
        state.value = result.getOrNull()?.let(AuthenticationState::Authenticated) ?: AuthenticationState.Unauthenticated
        pending?.complete(result)
        pending = null
    }
}
