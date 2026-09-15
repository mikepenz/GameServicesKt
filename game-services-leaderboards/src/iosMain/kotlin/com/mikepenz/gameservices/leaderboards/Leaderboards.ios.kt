package com.mikepenz.gameservices.leaderboards

import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.PlayerIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMakeRange
import platform.Foundation.NSError
import platform.GameKit.GKAccessPoint
import platform.GameKit.GKLeaderboard
import platform.GameKit.GKLeaderboardEntry
import platform.GameKit.GKLeaderboardPlayerScopeFriendsOnly
import platform.GameKit.GKLeaderboardPlayerScopeGlobal
import platform.GameKit.GKLeaderboardTimeScopeAllTime
import platform.GameKit.GKLeaderboardTimeScopeToday
import platform.GameKit.GKLeaderboardTimeScopeWeek
import platform.GameKit.GKLocalPlayer
import platform.GameKit.GKGameCenterViewControllerStateLeaderboards
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

public fun createLeaderboardsClient(
    presentingViewController: () -> UIViewController,
): LeaderboardsClient = IosLeaderboardsClient(presentingViewController)

@OptIn(ExperimentalForeignApi::class)
private class IosLeaderboardsClient(
    private val presentingViewController: () -> UIViewController,
) : LeaderboardsClient {
    override suspend fun submitScore(id: LeaderboardId, score: Long): Result<Unit> = providerResult {
        suspendCancellableCoroutine { continuation ->
            GKLeaderboard.submitScore(
                score = score,
                context = 0u,
                player = GKLocalPlayer.localPlayer(),
                leaderboardIDs = listOf(id.value),
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
    ): Result<LeaderboardScore?> = providerResult {
        loadEntries(id, scope, period, startRank = 1, limit = 1).first
    }

    override suspend fun loadScores(
        id: LeaderboardId,
        query: LeaderboardQuery,
    ): Result<List<LeaderboardScore>> = providerResult {
        loadEntries(id, query.scope, query.period, query.startRank, query.limit).second
    }

    override suspend fun showLeaderboards(): Result<Unit> = providerResult {
        dispatch_async(dispatch_get_main_queue()) {
            GKAccessPoint.shared().triggerAccessPointWithState(GKGameCenterViewControllerStateLeaderboards) {}
        }
    }

    override suspend fun showLeaderboard(id: LeaderboardId): Result<Unit> = providerResult {
        dispatch_async(dispatch_get_main_queue()) {
            GKAccessPoint.shared().triggerAccessPointWithLeaderboardID(
                leaderboardID = id.value,
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
        GKLeaderboard.loadLeaderboardsWithIDs(listOf(id.value)) { leaderboards, error ->
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
                        if (entryError == null) continuation.resume(
                            local?.toLeaderboardScore() to
                                entries.orEmpty().filterIsInstance<GKLeaderboardEntry>().map { it.toLeaderboardScore() },
                        ) else continuation.resumeWith(Result.failure(entryError.toGameServicesException()))
                    }
                }
            }
        }
    }
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
    rank = rank.toInt(),
)

private suspend fun <T> providerResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: GameServicesException) {
    Result.failure(exception)
} catch (exception: Throwable) {
    Result.failure(GameServicesException.ProviderFailure(GameServicesProvider.GameCenter, exception.toString()))
}

private fun NSError.toGameServicesException(): GameServicesException = GameServicesException.ProviderFailure(
    provider = GameServicesProvider.GameCenter,
    code = "$domain:$code",
)
