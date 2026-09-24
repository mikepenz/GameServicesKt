@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.leaderboards

import com.mikepenz.gameservices.GameServicesOperation
import com.mikepenz.gameservices.InternalGameServicesApi

import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesPlatform
import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.PlayerIdentity
import kotlin.jvm.JvmInline

@JvmInline
public value class LeaderboardId public constructor(
    public val value: String,
)

public data class LeaderboardIdMapping public constructor(
    public val id: LeaderboardId,
    public val googlePlayGamesId: LeaderboardId,
    public val gameCenterId: LeaderboardId,
)

public class LeaderboardIdMappings public constructor(
    mappings: List<LeaderboardIdMapping>,
) {
    private val mappings: List<LeaderboardIdMapping> = mappings.toList()
    private val byId: Map<LeaderboardId, LeaderboardIdMapping> = this.mappings.associateBy(LeaderboardIdMapping::id)
    private val byGooglePlayGamesId: Map<LeaderboardId, LeaderboardIdMapping> =
        this.mappings.associateBy(LeaderboardIdMapping::googlePlayGamesId)
    private val byGameCenterId: Map<LeaderboardId, LeaderboardIdMapping> =
        this.mappings.associateBy(LeaderboardIdMapping::gameCenterId)

    init {
        require(byId.size == this.mappings.size) { "Leaderboard IDs must be unique" }
        require(byGooglePlayGamesId.size == this.mappings.size) { "Google Play Games leaderboard IDs must be unique" }
        require(byGameCenterId.size == this.mappings.size) { "Game Center leaderboard IDs must be unique" }
    }

    @InternalGameServicesApi
    public fun providerId(provider: GameServicesProvider, id: LeaderboardId): LeaderboardId = when (provider) {
        GameServicesProvider.GooglePlayGames -> byId[id]?.googlePlayGamesId ?: id
        GameServicesProvider.GameCenter -> byId[id]?.gameCenterId ?: id
        GameServicesProvider.None -> id
    }

    @InternalGameServicesApi
    public fun commonId(provider: GameServicesProvider, id: LeaderboardId): LeaderboardId = when (provider) {
        GameServicesProvider.GooglePlayGames -> byGooglePlayGamesId[id]?.id ?: id
        GameServicesProvider.GameCenter -> byGameCenterId[id]?.id ?: id
        GameServicesProvider.None -> id
    }

    public companion object {
        public val Empty: LeaderboardIdMappings = LeaderboardIdMappings(emptyList())

        public fun of(vararg mappings: LeaderboardIdMapping): LeaderboardIdMappings = LeaderboardIdMappings(mappings.toList())
    }
}

public data class Leaderboard public constructor(
    public val id: LeaderboardId,
    public val title: String,
)

public data class LeaderboardScore public constructor(
    public val player: PlayerIdentity?,
    public val value: Long,
    public val formattedValue: String,
    public val rank: Long?,
    public val displayName: String = player?.displayName.orEmpty(),
) {
    init {
        require(rank == null || rank > 0)
    }
}

public enum class LeaderboardScope {
    Global,
    Friends,
}

public enum class LeaderboardPeriod {
    Today,
    Week,
    AllTime,
}

/** Starts at a rank and returns at most [limit] entries, including ties. Deep random access is bounded to rank 1000. */
public data class LeaderboardQuery public constructor(
    public val scope: LeaderboardScope,
    public val period: LeaderboardPeriod,
    public val startRank: Int,
    public val limit: Int,
) {
    init {
        require(startRank in 1..1000) { "Start rank must be between 1 and 1000" }
        require(limit in 1..25)
    }
}

public interface LeaderboardsClient {
    public val supportedOperations: Set<GameServicesOperation>
        get() = if (isSupported) setOf(GameServicesOperation.LoadLeaderboards, GameServicesOperation.SubmitScore, GameServicesOperation.LoadCurrentScore, GameServicesOperation.LoadScores, GameServicesOperation.ShowLeaderboards, GameServicesOperation.ShowLeaderboard) else emptySet()

    public val isSupported: Boolean

    public suspend fun loadLeaderboards(): Result<List<Leaderboard>>

    /** Completes after provider acknowledgement, not merely local enqueueing. */
    public suspend fun submitScore(
        id: LeaderboardId,
        score: Long,
    ): Result<Unit>

    public suspend fun loadCurrentPlayerScore(
        id: LeaderboardId,
        scope: LeaderboardScope,
        period: LeaderboardPeriod,
    ): Result<LeaderboardScore?>

    public suspend fun loadScores(
        id: LeaderboardId,
        query: LeaderboardQuery,
    ): Result<List<LeaderboardScore>>

    public suspend fun showLeaderboards(): Result<Unit>

    public suspend fun showLeaderboard(id: LeaderboardId): Result<Unit>
}

internal class UnsupportedLeaderboardsClient(
    target: GameServicesPlatform,
) : LeaderboardsClient {
    override val isSupported: Boolean = false

    private val unsupported: GameServicesException.UnsupportedTarget =
        GameServicesException.UnsupportedTarget(target)

    override suspend fun loadLeaderboards(): Result<List<Leaderboard>> = Result.failure(unsupported)

    override suspend fun submitScore(id: LeaderboardId, score: Long): Result<Unit> = Result.failure(unsupported)

    override suspend fun loadCurrentPlayerScore(
        id: LeaderboardId,
        scope: LeaderboardScope,
        period: LeaderboardPeriod,
    ): Result<LeaderboardScore?> = Result.failure(unsupported)

    override suspend fun loadScores(
        id: LeaderboardId,
        query: LeaderboardQuery,
    ): Result<List<LeaderboardScore>> = Result.failure(unsupported)

    override suspend fun showLeaderboards(): Result<Unit> = Result.failure(unsupported)

    override suspend fun showLeaderboard(id: LeaderboardId): Result<Unit> = Result.failure(unsupported)
}

/** The SDK replaces the previous buffer with an expanded snapshot. */
@InternalGameServicesApi
public data class ScorePage(public val scores: List<LeaderboardScore>, public val hasMore: Boolean)

@InternalGameServicesApi
public suspend fun collectLeaderboardScores(query: LeaderboardQuery, loadPage: suspend () -> ScorePage): List<LeaderboardScore> {
    // ponytail: bound sequential Android SDK work; use provider UI for deeper or heavily tied boards.
    repeat(41) {
        val page = loadPage()
        val result = page.scores.filter { score -> score.rank?.let { it >= query.startRank } == true }
            .take(query.limit)
        if (result.size == query.limit || !page.hasMore) return result
    }
    error("Leaderboard query exceeds 41 provider pages; use the provider leaderboard screen")
}

public fun createUnsupportedLeaderboardsClient(target: GameServicesPlatform): LeaderboardsClient = UnsupportedLeaderboardsClient(target)
