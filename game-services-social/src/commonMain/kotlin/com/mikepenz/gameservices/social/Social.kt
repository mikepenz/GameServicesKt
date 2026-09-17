package com.mikepenz.gameservices.social

import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.PlayerIdentity
import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

public data class PlayerProfile public constructor(
    public val identity: PlayerIdentity,
)

public class AvatarBytes private constructor(
    private val bytes: ByteArray,
) {
    public fun copyBytes(): ByteArray = bytes.copyOf()

    public companion object {
        public fun of(bytes: ByteArray): AvatarBytes = AvatarBytes(bytes.copyOf())
    }
}

public enum class FriendsAccessState {
    Unknown,
    Granted,
    ConsentRequired,
    Denied,
    Restricted,
}

public interface SocialClient {
    public val isSupported: Boolean

    public val friendsAccessState: StateFlow<FriendsAccessState>

    public suspend fun requestFriendsAccess(): Result<FriendsAccessState>

    public suspend fun loadFriends(): Result<List<PlayerProfile>>

    public suspend fun loadAvatar(playerId: PlayerId): Result<AvatarBytes?>

    public suspend fun showPlayerProfile(playerId: PlayerId): Result<Unit>
}

internal class UnsupportedSocialClient(
    target: GameServicesPlatform,
) : SocialClient {
    override val isSupported: Boolean = false

    override val friendsAccessState: StateFlow<FriendsAccessState> = MutableStateFlow(FriendsAccessState.Unknown)

    private val unsupported: GameServicesException.UnsupportedTarget =
        GameServicesException.UnsupportedTarget(target)

    override suspend fun requestFriendsAccess(): Result<FriendsAccessState> = Result.failure(unsupported)

    override suspend fun loadFriends(): Result<List<PlayerProfile>> = Result.failure(unsupported)

    override suspend fun loadAvatar(playerId: PlayerId): Result<AvatarBytes?> = Result.failure(unsupported)

    override suspend fun showPlayerProfile(playerId: PlayerId): Result<Unit> = Result.failure(unsupported)
}
