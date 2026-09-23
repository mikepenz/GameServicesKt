@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.leaderboards

import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.PlayerIdentity
import com.mikepenz.gameservices.gameServicesResult
import com.mikepenz.gameservices.toGameServicesException
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSMakeRange
import platform.GameKit.GKAccessPoint
import platform.GameKit.GKGameCenterViewControllerStateLeaderboards
import platform.GameKit.GKLeaderboard
import platform.GameKit.GKLeaderboardEntry
import platform.GameKit.GKLeaderboardPlayerScopeFriendsOnly
import platform.GameKit.GKLeaderboardPlayerScopeGlobal
import platform.GameKit.GKLeaderboardTimeScopeAllTime
import platform.GameKit.GKLeaderboardTimeScopeToday
import platform.GameKit.GKLeaderboardTimeScopeWeek
import platform.GameKit.GKLocalPlayer
import platform.UIKit.UIViewController

public fun createLeaderboardsClient(
    presentingViewController: () -> UIViewController,
    ids: LeaderboardIdMappings = LeaderboardIdMappings.Empty,
): LeaderboardsClient = IosLeaderboardsClient(presentingViewController, ids)

@OptIn(ExperimentalForeignApi::class)
private class IosLeaderboardsClient(
    private val presentingViewController: () -> UIViewController,
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
        suspendCancellableCoroutine { continuation ->
            GKLeaderboard.submitScore(
                score = score,
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

    override suspend fun showLeaderboards(): Result<Unit> = gameServicesResult {
        withContext(Dispatchers.Main.immediate) {
            GKAccessPoint.shared().triggerAccessPointWithState(GKGameCenterViewControllerStateLeaderboards) {}
        }
    }

    override suspend fun showLeaderboard(id: LeaderboardId): Result<Unit> = gameServicesResult {
        withContext(Dispatchers.Main.immediate) {
            GKAccessPoint.shared().triggerAccessPointWithLeaderboardID(
                leaderboardID = ids.providerId(GameServicesProvider.GameCenter, id).value,
                playerScope = GKLeaderboardPlayerScopeGlobal,
                timeScope = GKLeaderboardTimeScopeAllTime,
                handler = {},
            )
        }
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
                    range = NSMakeRange(startRank.toULong(), limit.toULong()),
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

private fun LeaderboardScope.gameKitValue(): Long = when (this) {
    LeaderboardScope.Global -> GKLeaderboardPlayerScopeGlobal
    LeaderboardScope.Friends -> GKLeaderboardPlayerScopeFriendsOnly
}

private fun LeaderboardPeriod.gameKitValue(): Long = when (this) {
    LeaderboardPeriod.Today -> GKLeaderboardTimeScopeToday
    LeaderboardPeriod.Week -> GKLeaderboardTimeScopeWeek
    LeaderboardPeriod.AllTime -> GKLeaderboardTimeScopeAllTime
}

private fun GKLeaderboardEntry.toLeaderboardScore(): LeaderboardScore = LeaderboardScore(
    player = PlayerIdentity(PlayerId(player.gamePlayerID), player.displayName ?: player.gamePlayerID),
    value = score,
    formattedValue = formattedScore,
    rank = rank.takeIf { it > 0 },
)
