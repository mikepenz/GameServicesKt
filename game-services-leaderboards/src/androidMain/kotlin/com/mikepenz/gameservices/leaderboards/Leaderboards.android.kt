package com.mikepenz.gameservices.leaderboards

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.games.LeaderboardsClient as GoogleLeaderboardsClient
import com.google.android.gms.games.PageDirection
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.leaderboard.LeaderboardScore as GoogleLeaderboardScore
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.google.android.gms.tasks.Task
import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.PlayerIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

public fun createLeaderboardsClient(activity: ComponentActivity): LeaderboardsClient = AndroidLeaderboardsClient(
    leaderboards = PlayGames.getLeaderboardsClient(activity),
    launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {},
)

private class AndroidLeaderboardsClient(
    private val leaderboards: GoogleLeaderboardsClient,
    private val launcher: ActivityResultLauncher<android.content.Intent>,
) : LeaderboardsClient {
    override suspend fun submitScore(id: LeaderboardId, score: Long): Result<Unit> = providerResult {
        leaderboards.submitScoreImmediate(id.value, score).await()
    }

    override suspend fun loadCurrentPlayerScore(
        id: LeaderboardId,
        scope: LeaderboardScope,
        period: LeaderboardPeriod,
    ): Result<LeaderboardScore?> = providerResult {
        leaderboards.loadCurrentPlayerLeaderboardScore(id.value, period.googleValue(), scope.googleValue())
            .await().get()?.toLeaderboardScore()
    }

    override suspend fun loadScores(
        id: LeaderboardId,
        query: LeaderboardQuery,
    ): Result<List<LeaderboardScore>> = providerResult {
        var scores = requireNotNull(leaderboards.loadTopScores(
            id.value,
            query.period.googleValue(),
            query.scope.googleValue(),
            query.limit,
        ).await().get())
        try {
            while (scores.scores.lastRank() < query.startRank) {
                val next = requireNotNull(
                    leaderboards.loadMoreScores(scores.scores, query.limit, PageDirection.NEXT).await().get(),
                )
                if (next.scores.lastRank() <= scores.scores.lastRank()) {
                    next.release()
                    break
                }
                scores.release()
                scores = next
            }
            (0 until scores.scores.count)
                .map { scores.scores.get(it).toLeaderboardScore() }
                .filter { it.rank in query.startRank until (query.startRank.toLong() + query.limit).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        } finally {
            scores.release()
        }
    }

    override suspend fun showLeaderboards(): Result<Unit> = providerResult {
        launcher.launch(leaderboards.allLeaderboardsIntent.await())
    }

    override suspend fun showLeaderboard(id: LeaderboardId): Result<Unit> = providerResult {
        launcher.launch(leaderboards.getLeaderboardIntent(id.value).await())
    }
}

private fun com.google.android.gms.games.leaderboard.LeaderboardScoreBuffer.lastRank(): Int =
    if (count == 0) Int.MAX_VALUE else get(count - 1).rank.toInt()

private fun LeaderboardScope.googleValue(): Int = when (this) {
    LeaderboardScope.Global -> LeaderboardVariant.COLLECTION_PUBLIC
    LeaderboardScope.Friends -> LeaderboardVariant.COLLECTION_FRIENDS
}

private fun LeaderboardPeriod.googleValue(): Int = when (this) {
    LeaderboardPeriod.Today -> LeaderboardVariant.TIME_SPAN_DAILY
    LeaderboardPeriod.Week -> LeaderboardVariant.TIME_SPAN_WEEKLY
    LeaderboardPeriod.AllTime -> LeaderboardVariant.TIME_SPAN_ALL_TIME
}

private fun GoogleLeaderboardScore.toLeaderboardScore(): LeaderboardScore = LeaderboardScore(
    player = PlayerIdentity(PlayerId(requireNotNull(scoreHolder).playerId), scoreHolderDisplayName),
    value = rawScore,
    formattedValue = displayScore,
    rank = rank.toInt(),
)

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWith(Result.failure(task.exception ?: IllegalStateException("Play Games task failed")))
        }
    }
}

private suspend fun <T> providerResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: GameServicesException) {
    Result.failure(exception)
} catch (exception: Throwable) {
    Result.failure(GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, exception.javaClass.name))
}
