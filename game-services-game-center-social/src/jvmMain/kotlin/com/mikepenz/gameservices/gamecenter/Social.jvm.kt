@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)
package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import com.mikepenz.gameservices.social.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.io.encoding.Base64

public fun GameCenterBackend.createSocialClient(): SocialClient = object : SocialClient {
    override val isSupported = true
    private val state = MutableStateFlow(FriendsAccessState.Unknown)
    override val friendsAccessState = state
    override val supportedOperations get() = gameCenterSupportedOperations(GameServicesPlatform.MacOS, System.getProperty("os.version").substringBefore('.').toLong(), super.supportedOperations)
    override suspend fun requestFriendsAccess(): Result<FriendsAccessState> = transport.call("friendsAccess").mapCatching { FriendsAccessState.valueOf(it.single().single()).also { value -> state.value = value } }
    override suspend fun loadFriends(): Result<List<PlayerProfile>> = transport.call("friends").mapCatching { rows -> rows.map { PlayerProfile(PlayerIdentity(PlayerId(it[0]), it[1])) } }.also { if (it.isSuccess) state.value = FriendsAccessState.Granted }
    override suspend fun loadAvatar(playerId: PlayerId): Result<AvatarBytes?> = transport.call("avatar", playerId.value).mapCatching { it.singleOrNull()?.single()?.let { bytes -> AvatarBytes.of(Base64.decode(bytes)) } }
    override suspend fun showPlayerProfile(playerId: PlayerId): Result<Unit> = transport.call("profile", playerId.value).map { }
}
