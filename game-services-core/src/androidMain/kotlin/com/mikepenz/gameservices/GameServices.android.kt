@file:OptIn(InternalGameServicesApi::class)

package com.mikepenz.gameservices

import androidx.activity.ComponentActivity
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public fun createGameServices(activity: ComponentActivity): GameServices {
    PlayGamesSdk.initialize(activity.applicationContext)
    return AndroidGameServices(activity)
}

private class AndroidGameServices(private val activity: ComponentActivity) : GameServices {
    override val support = GameServicesSupport(GameServicesPlatform.Android, GameServicesProvider.GooglePlayGames, true)
    private val state = MutableStateFlow<AuthenticationState>(AuthenticationState.Unauthenticated)
    override val authenticationState: StateFlow<AuthenticationState> = state
    private val authentication = Mutex()

    override suspend fun refreshAuthentication(): Result<PlayerIdentity?> = authenticate(explicit = false)

    override suspend fun authenticate(): Result<PlayerIdentity> = authenticate(explicit = true).fold(
        onSuccess = { it?.let(Result.Companion::success) ?: Result.failure(GameServicesException.AuthenticationRequired) },
        onFailure = Result.Companion::failure,
    )

    private suspend fun authenticate(explicit: Boolean): Result<PlayerIdentity?> = withContext(Dispatchers.Main.immediate) {
        authentication.withLock {
            state.value = AuthenticationState.Authenticating
            try {
                gameServicesResult {
                    val client = PlayGames.getGamesSignInClient(activity)
                    var signedIn = client.isAuthenticated().awaitGameServices().isAuthenticated
                    if (!signedIn && explicit) signedIn = client.signIn().awaitGameServices().isAuthenticated
                    if (!signedIn) null else {
                        val player = PlayGames.getPlayersClient(activity).currentPlayer.awaitGameServices()
                        PlayerIdentity(PlayerId(player.playerId), player.displayName)
                    }
                }.also { result ->
                    state.value = result.getOrNull()?.let(AuthenticationState::Authenticated) ?: AuthenticationState.Unauthenticated
                }
            } catch (cancellation: CancellationException) {
                state.value = AuthenticationState.Unauthenticated
                throw cancellation
            }
        }
    }
}
