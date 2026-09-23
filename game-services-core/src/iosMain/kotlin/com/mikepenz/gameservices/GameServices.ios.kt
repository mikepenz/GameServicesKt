@file:OptIn(InternalGameServicesApi::class)

package com.mikepenz.gameservices

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.GameKit.GKLocalPlayer
import platform.GameKit.setAuthenticateHandler
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Create one client for the app; GameKit owns one authentication handler. */
public fun createGameServices(presentingViewController: () -> UIViewController): GameServices =
    IosGameServices(presentingViewController)

private class IosGameServices(private val presentingViewController: () -> UIViewController) : GameServices {
    override val support = GameServicesSupport(GameServicesPlatform.IOS, GameServicesProvider.GameCenter, true)
    private val localPlayer = GKLocalPlayer.localPlayer()
    private val session = AuthenticationSession()
    override val authenticationState = session.authenticationState

    override suspend fun refreshAuthentication(): Result<PlayerIdentity?> = gameServicesResult {
        withContext(Dispatchers.Main.immediate) {
            session.refresh(
                install = {
                    localPlayer.setAuthenticateHandler { controller, error ->
                        dispatch_async(dispatch_get_main_queue()) {
                            when {
                                controller != null -> {
                                    try {
                                        presentingViewController().presentViewController(controller, true, null)
                                    } catch (error: Exception) {
                                        session.complete(Result.failure(GameServicesException.ProviderFailure(
                                            GameServicesProvider.GameCenter, error::class.simpleName.orEmpty(), error,
                                        )))
                                    }
                                }
                                localPlayer.authenticated -> session.complete(Result.success(localPlayer.toIdentity()))
                                error != null -> session.complete(Result.failure(error.toGameServicesException()))
                                else -> session.complete(Result.success(null))
                            }
                        }
                    }
                },
                current = { if (localPlayer.authenticated) localPlayer.toIdentity() else null },
            ).getOrThrow()
        }
    }

    override suspend fun authenticate(): Result<PlayerIdentity> = refreshAuthentication().fold(
        onSuccess = { player -> player?.let(Result.Companion::success)
            ?: Result.failure(GameServicesException.AuthenticationRequired) },
        onFailure = Result.Companion::failure,
    )

    private fun GKLocalPlayer.toIdentity() = PlayerIdentity(PlayerId(gamePlayerID), displayName ?: gamePlayerID)
}
