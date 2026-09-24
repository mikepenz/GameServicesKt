// Apple NSInteger is 32-bit on watchosArm64; conversions stay explicit.
@file:OptIn(kotlinx.cinterop.UnsafeNumber::class)
package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import kotlinx.coroutines.CancellationException
import platform.Foundation.NSError
import platform.GameKit.GKErrorCancelled
import platform.GameKit.GKErrorDomain
import platform.GameKit.GKErrorGameUnrecognized
import platform.GameKit.GKErrorNotAuthenticated
import platform.GameKit.GKErrorNotAuthorized

@InternalGameServicesApi
public suspend fun <T> gameServicesResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: Exception) {
    Result.failure(exception as? GameServicesException
        ?: GameServicesException.ProviderFailure(GameServicesProvider.GameCenter, exception::class.simpleName.orEmpty(), exception))
}

@InternalGameServicesApi
public fun NSError.toGameServicesException(): GameServicesException = when {
    domain == GKErrorDomain && code == GKErrorCancelled -> GameServicesException.UserCancelled
    domain == GKErrorDomain && code == GKErrorNotAuthenticated -> GameServicesException.AuthenticationRequired
    domain == GKErrorDomain && code == GKErrorNotAuthorized -> GameServicesException.PermissionRequired
    domain == GKErrorDomain && code == GKErrorGameUnrecognized -> GameServicesException.ConfigurationMissing
    else -> GameServicesException.ProviderFailure(GameServicesProvider.GameCenter, "$domain:$code")
}
