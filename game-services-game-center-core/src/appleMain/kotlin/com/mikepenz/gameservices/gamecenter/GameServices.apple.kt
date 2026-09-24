@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.GameKit.GKLocalPlayer
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** One explicit Game Center session. Construct through the platform factory. */
public class GameCenterBackend internal constructor(services: GameServices) : GameServices by services

internal fun currentGameCenterPlayer(): PlayerIdentity? = GKLocalPlayer.localPlayer().let { player ->
    if (player.authenticated) PlayerIdentity(PlayerId(player.gameCenterPlayerId()), player.displayName ?: player.gameCenterPlayerId()) else null
}

internal class NativeGameServices<C>(
    private val present: (C) -> Unit,
    private val installHandler: ((C?, NSError?) -> Unit) -> Unit,
    private val currentPlayer: () -> PlayerIdentity?,
    private val target: GameServicesPlatform,
    private val onMain: (() -> Unit) -> Unit = { dispatch_async(dispatch_get_main_queue(), it) },
) : GameServices {
    override val support = GameServicesSupport(target, GameServicesProvider.GameCenter, true)
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
