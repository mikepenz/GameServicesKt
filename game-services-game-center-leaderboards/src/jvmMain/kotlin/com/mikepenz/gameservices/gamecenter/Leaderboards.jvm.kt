@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)
package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import com.mikepenz.gameservices.leaderboards.*

public fun GameCenterBackend.createLeaderboardsClient(ids: LeaderboardIdMappings = LeaderboardIdMappings.Empty): LeaderboardsClient =
    object : LeaderboardsClient {
        override val isSupported = true
        override val supportedOperations get() = gameCenterSupportedOperations(GameServicesPlatform.MacOS, System.getProperty("os.version").substringBefore('.').toLong(), super.supportedOperations)
        private fun mapped(id: LeaderboardId): String = ids.providerId(GameServicesProvider.GameCenter, id).value
        override suspend fun loadLeaderboards(): Result<List<Leaderboard>> = transport.call("leaderboards").mapCatching { rows -> rows.map { Leaderboard(ids.commonId(GameServicesProvider.GameCenter, LeaderboardId(it[0])), it[1]) } }
        override suspend fun submitScore(id: LeaderboardId, score: Long): Result<Unit> = transport.call("score", mapped(id), score.toString()).map { }
        override suspend fun loadCurrentPlayerScore(id: LeaderboardId, scope: LeaderboardScope, period: LeaderboardPeriod): Result<LeaderboardScore?> =
            transport.call("currentScore", mapped(id), scope.name, period.name).mapCatching { it.singleOrNull()?.toScore() }
        override suspend fun loadScores(id: LeaderboardId, query: LeaderboardQuery): Result<List<LeaderboardScore>> =
            transport.call("scores", mapped(id), query.scope.name, query.period.name, query.startRank.toString(), query.limit.toString()).mapCatching { rows -> rows.map { it.toScore() } }
        override suspend fun showLeaderboards(): Result<Unit> = transport.call("showLeaderboards").map { }
        override suspend fun showLeaderboard(id: LeaderboardId): Result<Unit> = transport.call("showLeaderboard", mapped(id)).map { }
    }

private fun List<String>.toScore(): LeaderboardScore = LeaderboardScore(
    if (this[0].isEmpty()) null else PlayerIdentity(PlayerId(this[0]), this[1]), this[2].toLong(), this[3], this[4].toLongOrNull(), this[5],
)
