package com.mikepenz.gameservices

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.games.GamesClientStatusCodes
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

@InternalGameServicesApi
public suspend fun <T> Task<T>.awaitGameServices(onDiscard: (T) -> Unit = {}): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result) { _, value, _ -> onDiscard(value) }
            } else {
                continuation.resumeWithException(task.exception ?: IllegalStateException("Play Games task failed"))
            }
        }
    }

@InternalGameServicesApi
public suspend fun <T> gameServicesResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: Exception) {
    Result.failure(exception.toGameServicesException())
}

@InternalGameServicesApi
public fun Throwable.toGameServicesException(): GameServicesException = when (this) {
    is GameServicesException -> this
    is ApiException if statusCode == CommonStatusCodes.CANCELED -> GameServicesException.UserCancelled
    is ApiException if statusCode == GamesClientStatusCodes.SIGN_IN_REQUIRED -> GameServicesException.AuthenticationRequired
    is ApiException if statusCode == GamesClientStatusCodes.CONSENT_REQUIRED -> GameServicesException.PermissionRequired
    is ApiException if statusCode == GamesClientStatusCodes.APP_MISCONFIGURED || statusCode == GamesClientStatusCodes.GAME_NOT_FOUND || statusCode == CommonStatusCodes.DEVELOPER_ERROR -> GameServicesException.ConfigurationMissing
    is ApiException -> GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, statusCode.toString(), this)
    else -> GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, javaClass.name, this)
}
