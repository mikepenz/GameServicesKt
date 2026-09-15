package com.mikepenz.gameservices.leaderboards

import com.mikepenz.gameservices.PlayerIdentity
import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesPlatform
import kotlin.jvm.JvmInline

@JvmInline
public value class LeaderboardId public constructor(
    public val value: String,
)

public data class Leaderboard public constructor(
    public val id: LeaderboardId,
    public val title: String,
)

public data class LeaderboardScore public constructor(
    public val player: PlayerIdentity,
    public val value: Long,
    public val formattedValue: String,
    public val rank: Int,
) {
    init {
        require(rank > 0)
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

public data class LeaderboardQuery public constructor(
    public val scope: LeaderboardScope,
    public val period: LeaderboardPeriod,
    public val startRank: Int,
    public val limit: Int,
) {
    init {
        require(startRank > 0)
        require(limit in 1..25)
    }
}

public interface LeaderboardsClient {
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
    private val unsupported: GameServicesException.UnsupportedTarget =
        GameServicesException.UnsupportedTarget(target)

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
