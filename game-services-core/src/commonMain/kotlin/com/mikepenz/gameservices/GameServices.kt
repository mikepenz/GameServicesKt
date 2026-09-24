package com.mikepenz.gameservices

import kotlin.jvm.JvmInline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

public enum class GameServicesPlatform {
    Android,
    IOS,
    JVM,
    Wasm,
}

public enum class GameServicesProvider {
    GooglePlayGames,
    GameCenter,
    None,
}

public data class GameServicesSupport public constructor(
    public val target: GameServicesPlatform,
    public val provider: GameServicesProvider,
    public val isSupported: Boolean,
)

@JvmInline
public value class PlayerId public constructor(
    public val value: String,
)

public data class PlayerIdentity public constructor(
    public val id: PlayerId,
    public val displayName: String,
)

public sealed interface AuthenticationState {
    public data object Unsupported : AuthenticationState

    public data object Unauthenticated : AuthenticationState

    public data object Authenticating : AuthenticationState

    public data class Authenticated public constructor(
        public val player: PlayerIdentity,
    ) : AuthenticationState
}

public interface GameServices {
    public val support: GameServicesSupport

    public val authenticationState: StateFlow<AuthenticationState>

    public suspend fun refreshAuthentication(): Result<PlayerIdentity?>

    public suspend fun authenticate(): Result<PlayerIdentity>
}

internal class UnsupportedGameServices(
    target: GameServicesPlatform,
) : GameServices {
    override val support: GameServicesSupport = GameServicesSupport(
        target = target,
        provider = GameServicesProvider.None,
        isSupported = false,
    )
    override val authenticationState: StateFlow<AuthenticationState> = MutableStateFlow(
        AuthenticationState.Unsupported,
    )

    override suspend fun refreshAuthentication(): Result<PlayerIdentity?> = Result.failure(
        GameServicesException.UnsupportedTarget(support.target),
    )

    override suspend fun authenticate(): Result<PlayerIdentity> = Result.failure(
        GameServicesException.UnsupportedTarget(support.target),
    )
}

public sealed class GameServicesException protected constructor(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    public class UnsupportedTarget public constructor(
        public val target: GameServicesPlatform,
    ) : GameServicesException("Game services are unsupported on $target")

    public data object AuthenticationRequired : GameServicesException("Authentication is required")

    public data object ConfigurationMissing : GameServicesException("Game services configuration is missing")

    public data object PermissionRequired : GameServicesException("Game services permission is required")

    public data object UserCancelled : GameServicesException("The user cancelled game services")

    public class ProviderFailure public constructor(
        public val provider: GameServicesProvider,
        public val code: String,
        cause: Throwable?,
    ) : GameServicesException("$provider failed with code $code", cause) {
        public constructor(provider: GameServicesProvider, code: String) : this(provider, code, null)
    }
}
