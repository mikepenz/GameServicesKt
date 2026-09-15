package com.mikepenz.gameservices

import androidx.activity.ComponentActivity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.games.GamesClientStatusCodes
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

public fun createGameServices(activity: ComponentActivity): GameServices {
    PlayGamesSdk.initialize(activity.applicationContext)
    return AndroidGameServices(activity)
}

private class AndroidGameServices(
    private val activity: ComponentActivity,
) : GameServices {
    override val support: GameServicesSupport = GameServicesSupport(
        target = GameServicesPlatform.Android,
        provider = GameServicesProvider.GooglePlayGames,
        isSupported = true,
    )
    private val mutableAuthenticationState: MutableStateFlow<AuthenticationState> = MutableStateFlow(
        AuthenticationState.Unauthenticated,
    )
    override val authenticationState: StateFlow<AuthenticationState> = mutableAuthenticationState

    override suspend fun authenticate(): Result<PlayerIdentity> {
        mutableAuthenticationState.value = AuthenticationState.Authenticating
        val signInClient = PlayGames.getGamesSignInClient(activity)
        val authentication = signInClient.isAuthenticated().awaitResult()
            .getOrElse { return authenticationFailed(it) }
        if (!authentication.isAuthenticated) {
            signInClient.signIn().awaitResult()
                .getOrElse { return authenticationFailed(it) }
        }
        val player = PlayGames.getPlayersClient(activity).currentPlayer.awaitResult()
            .getOrElse { return authenticationFailed(it) }
        return PlayerIdentity(PlayerId(player.playerId), player.displayName)
            .also { mutableAuthenticationState.value = AuthenticationState.Authenticated(it) }
            .let(Result.Companion::success)
    }

    private fun authenticationFailed(throwable: Throwable): Result<PlayerIdentity> {
        mutableAuthenticationState.value = AuthenticationState.Unauthenticated
        return Result.failure(throwable.toGameServicesException())
    }
}

private suspend fun <T> Task<T>.awaitResult(): Result<T> = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            continuation.resume(task.toResult())
        }
    }
}

private fun <T> Task<T>.toResult(): Result<T> = if (isSuccessful) {
    Result.success(result)
} else {
    Result.failure(exception ?: IllegalStateException("Play Games task failed without an exception"))
}

private fun Throwable.toGameServicesException(): GameServicesException = when (this) {
    is ApiException if statusCode == CommonStatusCodes.CANCELED -> GameServicesException.UserCancelled
    is ApiException if statusCode == GamesClientStatusCodes.SIGN_IN_REQUIRED -> GameServicesException.AuthenticationRequired
    is ApiException -> GameServicesException.ProviderFailure(
        provider = GameServicesProvider.GooglePlayGames,
        code = statusCode.toString(),
    )
    else -> GameServicesException.ProviderFailure(
        provider = GameServicesProvider.GooglePlayGames,
        code = javaClass.name,
    )
}
