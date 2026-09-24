// GameKit authorization enums use NSInteger, including 32-bit watchOS.
@file:OptIn(kotlinx.cinterop.UnsafeNumber::class, com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.social.*

import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.PlayerIdentity
import com.mikepenz.gameservices.gamecenter.gameServicesResult
import com.mikepenz.gameservices.gamecenter.toGameServicesException
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
import platform.GameKit.GKLocalPlayer
import platform.GameKit.GKPlayer
import platform.GameKit.loadFriends
import platform.GameKit.loadFriendsAuthorizationStatus
import platform.GameKit.loadPlayersForIdentifiers
import platform.posix.memcpy

public fun GameCenterBackend.createSocialClient(
): SocialClient = AppleSocialClient(support.target, GKLocalPlayer.localPlayer())

@OptIn(ExperimentalForeignApi::class)
private class AppleSocialClient(
    private val target: com.mikepenz.gameservices.GameServicesPlatform,
    private val player: GKLocalPlayer,
) : SocialClient {
    private val mutableFriendsAccessState = MutableStateFlow(FriendsAccessState.Unknown)
    override val supportedOperations: Set<com.mikepenz.gameservices.GameServicesOperation>
        get() = gameCenterSupportedOperations(target, gameCenterOsMajor(), super.supportedOperations)
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
        requireGameCenterOperation(supportedOperations, com.mikepenz.gameservices.GameServicesOperation.LoadAvatar)
        val profile = playerFor(playerId) ?: return@gameServicesResult null
        profile.loadAvatarBytes()?.let(AvatarBytes::of)
    }

    override suspend fun showPlayerProfile(playerId: PlayerId): Result<Unit> = gameServicesResult {
        requireGameCenterOperation(supportedOperations, com.mikepenz.gameservices.GameServicesOperation.ShowPlayerProfile)
        val profile = playerFor(playerId)
            ?: throw IllegalArgumentException("Unknown player ${playerId.value}")
        presentPlayerProfile(profile)
    }

    private suspend fun playerFor(playerId: PlayerId): GKPlayer? {
        if (playerId.value == player.gameCenterPlayerId()) return player
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

    private fun GKPlayer.toProfile(): PlayerProfile = PlayerProfile(
        PlayerIdentity(PlayerId(gameCenterPlayerId()), displayName ?: gameCenterPlayerId()),
    )
}

private fun platform.GameKit.GKFriendsAuthorizationStatus.toFriendsAccessState(): FriendsAccessState = when (this) {
    GKFriendsAuthorizationStatusAuthorized -> FriendsAccessState.Granted
    GKFriendsAuthorizationStatusNotDetermined -> FriendsAccessState.ConsentRequired
    GKFriendsAuthorizationStatusDenied -> FriendsAccessState.Denied
    GKFriendsAuthorizationStatusRestricted -> FriendsAccessState.Restricted
    else -> FriendsAccessState.Unknown
}

internal expect suspend fun presentPlayerProfile(player: GKPlayer)
internal expect suspend fun GKPlayer.loadAvatarBytes(): ByteArray?
