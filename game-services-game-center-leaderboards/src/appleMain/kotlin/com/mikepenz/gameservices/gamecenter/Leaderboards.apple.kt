@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD") // watchOS arm64 uses 32-bit NSInteger.
@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.leaderboards.*

import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.PlayerIdentity
import com.mikepenz.gameservices.gamecenter.gameServicesResult
import com.mikepenz.gameservices.gamecenter.toGameServicesException
import kotlin.coroutines.resume
import kotlinx.cinterop.convert
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSMakeRange
import platform.GameKit.GKLeaderboard
import platform.GameKit.GKLeaderboardEntry
import platform.GameKit.GKLeaderboardPlayerScopeFriendsOnly
import platform.GameKit.GKLeaderboardPlayerScopeGlobal
import platform.GameKit.GKLeaderboardTimeScopeAllTime
import platform.GameKit.GKLeaderboardTimeScopeToday
import platform.GameKit.GKLeaderboardTimeScopeWeek
import platform.GameKit.GKLocalPlayer

public fun GameCenterBackend.createLeaderboardsClient(
    ids: LeaderboardIdMappings = LeaderboardIdMappings.Empty,
): LeaderboardsClient = AppleLeaderboardsClient(support.target, ids)

@OptIn(ExperimentalForeignApi::class)
private class AppleLeaderboardsClient(
    private val target: com.mikepenz.gameservices.GameServicesPlatform,
    private val ids: LeaderboardIdMappings,
) : LeaderboardsClient {
    override val isSupported: Boolean = true

    override suspend fun loadLeaderboards(): Result<List<Leaderboard>> = gameServicesResult {
        suspendCancellableCoroutine { continuation ->
            GKLeaderboard.loadLeaderboardsWithIDs(null) { leaderboards, error ->
                if (continuation.isActive) {
                    if (error == null) {
                        continuation.resumeWith(runCatching {
                            leaderboards.orEmpty().filterIsInstance<GKLeaderboard>().map(::toLeaderboard)
                        })
                    } else {
                        continuation.resumeWith(Result.failure(error.toGameServicesException()))
                    }
                }
            }
        }
    }

    override suspend fun submitScore(id: LeaderboardId, score: Long): Result<Unit> = gameServicesResult {
        val nativeScore = score.convert<platform.darwin.NSInteger>()
        require(nativeScore.toLong() == score) { "Score exceeds this platform's native integer range" }
        suspendCancellableCoroutine { continuation ->
            GKLeaderboard.submitScore(
                score = nativeScore,
                context = 0u,
                player = GKLocalPlayer.localPlayer(),
                leaderboardIDs = listOf(ids.providerId(GameServicesProvider.GameCenter, id).value),
            ) { error ->
                if (continuation.isActive) {
                    if (error == null) continuation.resume(Unit)
                    else continuation.resumeWith(Result.failure(error.toGameServicesException()))
                }
            }
        }
    }

    override suspend fun loadCurrentPlayerScore(
        id: LeaderboardId,
        scope: LeaderboardScope,
        period: LeaderboardPeriod,
    ): Result<LeaderboardScore?> = gameServicesResult {
        loadEntries(id, scope, period, startRank = 1, limit = 1).first
    }

    override suspend fun loadScores(
        id: LeaderboardId,
        query: LeaderboardQuery,
    ): Result<List<LeaderboardScore>> = gameServicesResult {
        loadEntries(id, query.scope, query.period, query.startRank, query.limit).second
    }

    override val supportedOperations: Set<com.mikepenz.gameservices.GameServicesOperation>
        get() = gameCenterSupportedOperations(target, gameCenterOsMajor(), super.supportedOperations)

    override suspend fun showLeaderboards(): Result<Unit> = gameServicesResult {
        requireGameCenterOperation(supportedOperations, com.mikepenz.gameservices.GameServicesOperation.ShowLeaderboards)
        presentLeaderboards(null)
    }

    override suspend fun showLeaderboard(id: LeaderboardId): Result<Unit> = gameServicesResult {
        requireGameCenterOperation(supportedOperations, com.mikepenz.gameservices.GameServicesOperation.ShowLeaderboard)
        presentLeaderboards(ids.providerId(GameServicesProvider.GameCenter, id).value)
    }

    private suspend fun loadEntries(
        id: LeaderboardId,
        scope: LeaderboardScope,
        period: LeaderboardPeriod,
        startRank: Int,
        limit: Int,
    ): Pair<LeaderboardScore?, List<LeaderboardScore>> = suspendCancellableCoroutine { continuation ->
        val providerId = ids.providerId(GameServicesProvider.GameCenter, id)
        GKLeaderboard.loadLeaderboardsWithIDs(listOf(providerId.value)) { leaderboards, error ->
            if (!continuation.isActive) return@loadLeaderboardsWithIDs
            val leaderboard = leaderboards?.firstOrNull() as? GKLeaderboard
            when {
                error != null -> continuation.resumeWith(Result.failure(error.toGameServicesException()))
                leaderboard == null -> continuation.resumeWith(Result.failure(IllegalArgumentException("Unknown leaderboard ${id.value}")))
                else -> leaderboard.loadEntriesForPlayerScope(
                    playerScope = scope.gameKitValue(),
                    timeScope = period.gameKitValue(),
                    range = NSMakeRange(startRank.convert(), limit.convert()),
                ) { local, entries, _, entryError ->
                    if (continuation.isActive) {
                        if (entryError == null) continuation.resumeWith(runCatching {
                            local?.toLeaderboardScore() to
                                entries.orEmpty().filterIsInstance<GKLeaderboardEntry>().map { it.toLeaderboardScore() }
                        }) else continuation.resumeWith(Result.failure(entryError.toGameServicesException()))
                    }
                }
            }
        }
    }

    private fun toLeaderboard(leaderboard: GKLeaderboard): Leaderboard = Leaderboard(
        id = ids.commonId(GameServicesProvider.GameCenter, LeaderboardId(leaderboard.baseLeaderboardID)),
        title = leaderboard.title ?: leaderboard.baseLeaderboardID,
    )
}

private fun LeaderboardScope.gameKitValue(): platform.GameKit.GKLeaderboardPlayerScope = when (this) {
    LeaderboardScope.Global -> GKLeaderboardPlayerScopeGlobal
    LeaderboardScope.Friends -> GKLeaderboardPlayerScopeFriendsOnly
}

private fun LeaderboardPeriod.gameKitValue(): platform.GameKit.GKLeaderboardTimeScope = when (this) {
    LeaderboardPeriod.Today -> GKLeaderboardTimeScopeToday
    LeaderboardPeriod.Week -> GKLeaderboardTimeScopeWeek
    LeaderboardPeriod.AllTime -> GKLeaderboardTimeScopeAllTime
}

private fun GKLeaderboardEntry.toLeaderboardScore(): LeaderboardScore = LeaderboardScore(
    player = PlayerIdentity(PlayerId(player.gameCenterPlayerId()), player.displayName ?: player.gameCenterPlayerId()),
    value = score.toLong(),
    formattedValue = formattedScore,
    rank = rank.toLong().takeIf { it > 0 },
)

internal expect suspend fun presentLeaderboards(id: String?)
