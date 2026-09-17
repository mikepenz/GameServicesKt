package com.mikepenz.gameservices

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.GameKit.GKErrorCancelled
import platform.GameKit.GKErrorDomain
import platform.GameKit.GKErrorNotAuthenticated
import platform.GameKit.GKLocalPlayer
import platform.GameKit.setAuthenticateHandler
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

public fun createGameServices(
    presentingViewController: () -> UIViewController,
): GameServices = IosGameServices(presentingViewController)

private class IosGameServices(
    private val presentingViewController: () -> UIViewController,
) : GameServices {
    override val support: GameServicesSupport = GameServicesSupport(
        target = GameServicesPlatform.IOS,
        provider = GameServicesProvider.GameCenter,
        isSupported = true,
    )
    private val localPlayer: GKLocalPlayer = GKLocalPlayer.localPlayer()
    private val mutableAuthenticationState: MutableStateFlow<AuthenticationState> = MutableStateFlow(
        AuthenticationState.Unauthenticated,
    )
    override val authenticationState: StateFlow<AuthenticationState> = mutableAuthenticationState

    override suspend fun refreshAuthentication(): Result<PlayerIdentity?> = suspendCancellableCoroutine { continuation ->
        mutableAuthenticationState.value = AuthenticationState.Authenticating
        continuation.invokeOnCancellation {
            mutableAuthenticationState.value = AuthenticationState.Unauthenticated
        }
        localPlayer.setAuthenticateHandler { viewController, error ->
            when {
                error != null -> complete(continuation, Result.failure(error.toGameServicesException()))
                viewController != null -> present(viewController)
                localPlayer.authenticated -> complete(continuation, Result.success(localPlayer.toIdentity()))
                else -> complete(
                    continuation,
                    Result.failure(GameServicesException.AuthenticationRequired),
                )
            }
        }
    }

    private fun present(viewController: UIViewController) {
        dispatch_async(dispatch_get_main_queue()) {
            presentingViewController().presentViewController(
                viewControllerToPresent = viewController,
                animated = true,
                completion = null,
            )
        }
    }

    override suspend fun authenticate(): Result<PlayerIdentity> = refreshAuthentication().fold(
        onSuccess = { player -> player?.let(Result.Companion::success) ?: Result.failure(GameServicesException.AuthenticationRequired) },
        onFailure = Result.Companion::failure,
    )

    private fun complete(
        continuation: CancellableContinuation<Result<PlayerIdentity?>>,
        result: Result<PlayerIdentity>,
    ) {
        if (continuation.isActive) {
            mutableAuthenticationState.value = result.fold(
                onSuccess = AuthenticationState::Authenticated,
                onFailure = { AuthenticationState.Unauthenticated },
            )
            continuation.resume(result)
        }
    }

    private fun GKLocalPlayer.toIdentity(): PlayerIdentity = PlayerIdentity(
        id = PlayerId(gamePlayerID),
        displayName = displayName ?: gamePlayerID,
    )
}

private fun NSError.toGameServicesException(): GameServicesException = when {
    domain == GKErrorDomain && code == GKErrorCancelled -> GameServicesException.UserCancelled
    domain == GKErrorDomain && code == GKErrorNotAuthenticated -> GameServicesException.AuthenticationRequired
    else -> GameServicesException.ProviderFailure(
        provider = GameServicesProvider.GameCenter,
        code = "$domain:$code",
    )
}
