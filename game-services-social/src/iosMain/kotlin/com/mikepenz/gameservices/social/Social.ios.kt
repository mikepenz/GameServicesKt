@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.social

import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.PlayerIdentity
import com.mikepenz.gameservices.gameServicesResult
import com.mikepenz.gameservices.toGameServicesException
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.GameKit.GKFriendsAuthorizationStatusAuthorized
import platform.GameKit.GKFriendsAuthorizationStatusDenied
import platform.GameKit.GKFriendsAuthorizationStatusNotDetermined
import platform.GameKit.GKFriendsAuthorizationStatusRestricted
import platform.GameKit.GKGameCenterControllerDelegateProtocol
import platform.GameKit.GKGameCenterViewController
import platform.GameKit.GKLocalPlayer
import platform.GameKit.GKPhotoSizeNormal
import platform.GameKit.GKPlayer
import platform.GameKit.loadFriends
import platform.GameKit.loadFriendsAuthorizationStatus
import platform.GameKit.loadPhotoForSize
import platform.GameKit.loadPlayersForIdentifiers
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.posix.memcpy

public fun createSocialClient(
    presentingViewController: () -> UIViewController,
): SocialClient = IosSocialClient(presentingViewController, GKLocalPlayer.localPlayer())

@OptIn(ExperimentalForeignApi::class)
private class IosSocialClient(
    private val presentingViewController: () -> UIViewController,
    private val player: GKLocalPlayer,
) : SocialClient {
    private val mutableFriendsAccessState = MutableStateFlow(FriendsAccessState.Unknown)
    private val gameCenterDelegate = GameCenterDelegate()
    override val isSupported: Boolean = true
    override val friendsAccessState: StateFlow<FriendsAccessState> = mutableFriendsAccessState

    override suspend fun requestFriendsAccess(): Result<FriendsAccessState> = gameServicesResult {
        when (authorizationStatus()) {
            FriendsAccessState.Granted -> FriendsAccessState.Granted
            FriendsAccessState.ConsentRequired -> loadFriendsInternal().let { FriendsAccessState.Granted }
            FriendsAccessState.Denied -> FriendsAccessState.Denied
            FriendsAccessState.Restricted -> FriendsAccessState.Restricted
            FriendsAccessState.Unknown -> FriendsAccessState.Unknown
        }.also { mutableFriendsAccessState.value = it }
    }

    override suspend fun loadFriends(): Result<List<PlayerProfile>> = gameServicesResult {
        val state = authorizationStatus()
        mutableFriendsAccessState.value = state
        when (state) {
            FriendsAccessState.Granted -> loadFriendsInternal().map { it.toProfile() }
            FriendsAccessState.ConsentRequired -> throw GameServicesException.PermissionRequired
            FriendsAccessState.Denied,
            FriendsAccessState.Restricted,
            FriendsAccessState.Unknown,
            -> throw GameServicesException.PermissionRequired
        }
    }

    override suspend fun loadAvatar(playerId: PlayerId): Result<AvatarBytes?> = gameServicesResult {
        val profile = playerFor(playerId) ?: return@gameServicesResult null
        val photo = profile.loadPhotoForSize(GKPhotoSizeNormal) ?: return@gameServicesResult null
        withContext(Dispatchers.Default) { AvatarBytes.of(requireNotNull(UIImagePNGRepresentation(photo)).toByteArray()) }
    }

    override suspend fun showPlayerProfile(playerId: PlayerId): Result<Unit> = gameServicesResult {
        val profile = playerFor(playerId)
            ?: throw IllegalArgumentException("Unknown player ${playerId.value}")
        presentPlayerProfile(presentingViewController, gameCenterDelegate, profile)
    }

    private suspend fun playerFor(playerId: PlayerId): GKPlayer? {
        if (playerId.value == player.gamePlayerID) return player
        return suspendCancellableCoroutine { continuation ->
            GKPlayer.loadPlayersForIdentifiers(listOf(playerId.value)) { players, error ->
                if (continuation.isActive) {
                    if (error != null) continuation.resumeWith(Result.failure(error.toGameServicesException()))
                    else continuation.resume(players.orEmpty().filterIsInstance<GKPlayer>().firstOrNull())
                }
            }
        }
    }

    private suspend fun authorizationStatus(): FriendsAccessState = suspendCancellableCoroutine { continuation ->
        player.loadFriendsAuthorizationStatus { status, error ->
            if (continuation.isActive) {
                if (error == null) continuation.resume(status.toFriendsAccessState())
                else continuation.resumeWith(Result.failure(error.toGameServicesException()))
            }
        }
    }

    private suspend fun loadFriendsInternal(): List<GKPlayer> = suspendCancellableCoroutine { continuation ->
        player.loadFriends { friends, error ->
            if (continuation.isActive) {
                if (error == null) continuation.resume(friends.orEmpty().filterIsInstance<GKPlayer>())
                else continuation.resumeWith(Result.failure(error.toGameServicesException()))
            }
        }
    }

    private suspend fun GKPlayer.loadPhotoForSize(size: Long): platform.UIKit.UIImage? = suspendCancellableCoroutine { continuation ->
        loadPhotoForSize(size) { image, error ->
            if (continuation.isActive) {
                if (error == null) continuation.resume(image)
                else continuation.resumeWith(Result.failure(error.toGameServicesException()))
            }
        }
    }

    private fun GKPlayer.toProfile(): PlayerProfile = PlayerProfile(
        PlayerIdentity(PlayerId(gamePlayerID), displayName ?: gamePlayerID),
    )
}

private fun Long.toFriendsAccessState(): FriendsAccessState = when (this) {
    GKFriendsAuthorizationStatusAuthorized -> FriendsAccessState.Granted
    GKFriendsAuthorizationStatusNotDetermined -> FriendsAccessState.ConsentRequired
    GKFriendsAuthorizationStatusDenied -> FriendsAccessState.Denied
    GKFriendsAuthorizationStatusRestricted -> FriendsAccessState.Restricted
    else -> FriendsAccessState.Unknown
}

private class GameCenterDelegate : NSObject(), GKGameCenterControllerDelegateProtocol {
    override fun gameCenterViewControllerDidFinish(gameCenterViewController: GKGameCenterViewController) {
        gameCenterViewController.dismissViewControllerAnimated(true, null)
    }
}

internal expect suspend fun presentPlayerProfile(
    presentingViewController: () -> UIViewController,
    delegate: GKGameCenterControllerDelegateProtocol,
    player: GKPlayer,
)

@OptIn(ExperimentalForeignApi::class)
private fun platform.Foundation.NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { output ->
    if (output.isNotEmpty()) output.usePinned { memcpy(it.addressOf(0), bytes, length) }
}
