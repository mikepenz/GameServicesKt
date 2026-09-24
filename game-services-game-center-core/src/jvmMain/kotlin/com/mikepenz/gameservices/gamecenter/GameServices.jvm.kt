@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)
package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path

/** Own one session per application and close it before unloading the host. */
public class GameCenterBackend public constructor(nativeLibrary: Path? = null) : GameServices, AutoCloseable {
    @InternalGameServicesApi public val transport: GameCenterTransport = GameCenterTransport(nativeLibrary)
    override val support: GameServicesSupport = GameServicesSupport(GameServicesPlatform.JVM, GameServicesProvider.GameCenter, true)
    private val state = MutableStateFlow<AuthenticationState>(AuthenticationState.Unauthenticated)
    override val authenticationState: StateFlow<AuthenticationState> = state
    private val authentication = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Native StateFlow supplies account changes without polling GameKit.
        scope.launch {
            var previous = ""
            while (isActive) {
                val rows = transport.call("state", previous).getOrElse { state.value = AuthenticationState.Unauthenticated; break }
                previous = encodeBridgeRows(rows)
                state.value = when {
                    rows.isEmpty() -> AuthenticationState.Unauthenticated
                    rows.single().size == 1 -> AuthenticationState.Authenticating
                    else -> AuthenticationState.Authenticated(PlayerIdentity(PlayerId(rows[0][0]), rows[0][1]))
                }
            }
        }
    }

    override suspend fun refreshAuthentication(): Result<PlayerIdentity?> = authentication.withLock {
        state.value = AuthenticationState.Authenticating
        try {
            transport.call("authenticate").mapCatching { rows ->
                rows.singleOrNull()?.let { PlayerIdentity(PlayerId(it[0]), it[1]) }
            }.also { result ->
                state.value = result.getOrNull()?.let(AuthenticationState::Authenticated) ?: AuthenticationState.Unauthenticated
            }
        } finally {
            if (state.value == AuthenticationState.Authenticating) state.value = AuthenticationState.Unauthenticated
        }
    }

    override suspend fun authenticate(): Result<PlayerIdentity> = refreshAuthentication().mapCatching {
        it ?: throw GameServicesException.AuthenticationRequired
    }

    override fun close() {
        scope.cancel()
        transport.close()
        state.value = AuthenticationState.Unauthenticated
    }
}
