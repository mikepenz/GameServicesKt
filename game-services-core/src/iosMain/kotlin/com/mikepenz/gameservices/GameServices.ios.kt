@file:OptIn(InternalGameServicesApi::class)

package com.mikepenz.gameservices

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.GameKit.GKLocalPlayer
import platform.GameKit.setAuthenticateHandler
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Create one client for the app; GameKit owns one authentication handler. */
public fun createGameServices(presentingViewController: () -> UIViewController): GameServices =
    IosGameServices(
        present = { presentingViewController().presentViewController(it, true, null) },
        installHandler = { GKLocalPlayer.localPlayer().setAuthenticateHandler(it) },
        currentPlayer = {
            GKLocalPlayer.localPlayer().let { player ->
                if (player.authenticated) PlayerIdentity(PlayerId(player.gamePlayerID), player.displayName ?: player.gamePlayerID) else null
            }
        },
    )

internal class IosGameServices(
    private val present: (UIViewController) -> Unit,
    private val installHandler: ((UIViewController?, NSError?) -> Unit) -> Unit,
    private val currentPlayer: () -> PlayerIdentity?,
    private val onMain: (() -> Unit) -> Unit = { dispatch_async(dispatch_get_main_queue(), it) },
) : GameServices {
    override val support = GameServicesSupport(GameServicesPlatform.IOS, GameServicesProvider.GameCenter, true)
    private val session = AuthenticationSession()
    override val authenticationState = session.authenticationState

    override suspend fun refreshAuthentication(): Result<PlayerIdentity?> = gameServicesResult {
        withContext(Dispatchers.Main.immediate) {
            session.refresh(
                install = {
                    installHandler { controller, error ->
                        onMain {
                            val player = currentPlayer()
                            when {
                                controller != null -> {
                                    try {
                                        present(controller)
                                    } catch (error: Exception) {
                                        session.complete(Result.failure(GameServicesException.ProviderFailure(
                                            GameServicesProvider.GameCenter, error::class.simpleName.orEmpty(), error,
                                        )))
                                    }
                                }
                                player != null -> session.complete(Result.success(player))
                                error != null -> session.complete(Result.failure(error.toGameServicesException()))
                                else -> session.complete(Result.success(null))
                            }
                        }
                    }
                },
                current = currentPlayer,
            ).getOrThrow()
        }
    }

    override suspend fun authenticate(): Result<PlayerIdentity> = refreshAuthentication().fold(
        onSuccess = { player -> player?.let(Result.Companion::success)
            ?: Result.failure(GameServicesException.AuthenticationRequired) },
        onFailure = Result.Companion::failure,
    )
}
