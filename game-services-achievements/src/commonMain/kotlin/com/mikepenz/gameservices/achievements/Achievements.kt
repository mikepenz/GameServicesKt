package com.mikepenz.gameservices.achievements

import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesPlatform
import com.mikepenz.gameservices.GameServicesProvider
import kotlin.jvm.JvmInline

@JvmInline
public value class AchievementId public constructor(
    public val value: String,
)

public data class AchievementIdMapping public constructor(
    public val id: AchievementId,
    public val googlePlayGamesId: AchievementId,
    public val gameCenterId: AchievementId,
)

public class AchievementIdMappings public constructor(
    mappings: List<AchievementIdMapping>,
) {
    private val mappings: List<AchievementIdMapping> = mappings.toList()
    private val byId: Map<AchievementId, AchievementIdMapping> = this.mappings.associateBy(AchievementIdMapping::id)
    private val byGooglePlayGamesId: Map<AchievementId, AchievementIdMapping> =
        this.mappings.associateBy(AchievementIdMapping::googlePlayGamesId)
    private val byGameCenterId: Map<AchievementId, AchievementIdMapping> =
        this.mappings.associateBy(AchievementIdMapping::gameCenterId)

    init {
        require(byId.size == this.mappings.size) { "Achievement IDs must be unique" }
        require(byGooglePlayGamesId.size == this.mappings.size) { "Google Play Games achievement IDs must be unique" }
        require(byGameCenterId.size == this.mappings.size) { "Game Center achievement IDs must be unique" }
    }

    internal fun providerId(provider: GameServicesProvider, id: AchievementId): AchievementId = when (provider) {
        GameServicesProvider.GooglePlayGames -> byId[id]?.googlePlayGamesId ?: id
        GameServicesProvider.GameCenter -> byId[id]?.gameCenterId ?: id
        GameServicesProvider.None -> id
    }

    internal fun commonId(provider: GameServicesProvider, id: AchievementId): AchievementId = when (provider) {
        GameServicesProvider.GooglePlayGames -> byGooglePlayGamesId[id]?.id ?: id
        GameServicesProvider.GameCenter -> byGameCenterId[id]?.id ?: id
        GameServicesProvider.None -> id
    }

    public companion object {
        public val Empty: AchievementIdMappings = AchievementIdMappings(emptyList())

        public fun of(vararg mappings: AchievementIdMapping): AchievementIdMappings = AchievementIdMappings(mappings.toList())
    }
}

public data class Achievement public constructor(
    public val id: AchievementId,
    public val title: String,
    public val description: String,
    public val points: Long,
    public val isHidden: Boolean,
    public val state: AchievementState,
    public val completionPercent: Int,
    public val steps: AchievementSteps? = null,
) {
    init {
        require(completionPercent in 0..100)
    }
}

public enum class AchievementState {
    Locked,
    Unlocked,
}

public data class AchievementSteps public constructor(
    public val current: Int,
    public val total: Int,
) {
    init {
        require(total > 0)
        require(current in 0..total)
    }
}

public sealed interface AchievementProgress {
    public data object Unlocked : AchievementProgress

    public data class Percent public constructor(
        public val value: Int,
    ) : AchievementProgress {
        init {
            require(value in 0..100)
        }
    }

    public data class Steps public constructor(
        public val current: Int,
        public val total: Int,
    ) : AchievementProgress {
        init {
            require(total > 0)
            require(current in 0..total)
        }
    }
}

internal fun AchievementProgress.percent(): Int = when (this) {
    AchievementProgress.Unlocked -> 100
    is AchievementProgress.Percent -> value
    is AchievementProgress.Steps -> (current.toLong() * 100 / total).toInt()
}

internal fun AchievementProgress.stepsFor(configuredTotal: Int): Int {
    require(configuredTotal > 0)
    return when (this) {
        AchievementProgress.Unlocked -> configuredTotal
        is AchievementProgress.Percent -> (configuredTotal.toLong() * value / 100).toInt()
        is AchievementProgress.Steps -> (configuredTotal.toLong() * current / total).toInt()
    }
}

public interface AchievementsClient {
    public val isSupported: Boolean

    public suspend fun loadAchievements(forceReload: Boolean = false): Result<List<Achievement>>

    /** Completes when the provider acknowledges the update; failures remain observable. */
    public suspend fun reportProgress(
        id: AchievementId,
        progress: AchievementProgress,
    ): Result<Unit>

    public suspend fun showAchievements(): Result<Unit>
}

internal class UnsupportedAchievementsClient(
    target: GameServicesPlatform,
) : AchievementsClient {
    override val isSupported: Boolean = false

    private val unsupported: GameServicesException.UnsupportedTarget =
        GameServicesException.UnsupportedTarget(target)

    override suspend fun loadAchievements(forceReload: Boolean): Result<List<Achievement>> = Result.failure(unsupported)

    override suspend fun reportProgress(
        id: AchievementId,
        progress: AchievementProgress,
    ): Result<Unit> = Result.failure(unsupported)

    override suspend fun showAchievements(): Result<Unit> = Result.failure(unsupported)
}

internal inline fun configuredSteps(incremental: Boolean, readSteps: () -> Int): Int? =
    if (incremental) readSteps() else null
