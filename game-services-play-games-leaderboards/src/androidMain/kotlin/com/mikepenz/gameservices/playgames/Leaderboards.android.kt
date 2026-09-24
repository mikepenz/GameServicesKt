@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.playgames

import com.mikepenz.gameservices.leaderboards.*

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.data.DataBufferUtils
import com.google.android.gms.games.LeaderboardsClient as GoogleLeaderboardsClient
import com.google.android.gms.games.PageDirection
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.leaderboard.Leaderboard as GoogleLeaderboard
import com.google.android.gms.games.leaderboard.LeaderboardScore as GoogleLeaderboardScore
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.PlayerIdentity
import com.mikepenz.gameservices.playgames.awaitGameServices
import com.mikepenz.gameservices.playgames.gameServicesResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

public fun PlayGamesBackend.createLeaderboardsClient(
    ids: LeaderboardIdMappings = LeaderboardIdMappings.Empty,
): LeaderboardsClient = AndroidLeaderboardsClient(
    leaderboards = PlayGames.getLeaderboardsClient(activity),
    launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {},
    ids = ids,
)

private class AndroidLeaderboardsClient(
    private val leaderboards: GoogleLeaderboardsClient,
    private val launcher: ActivityResultLauncher<android.content.Intent>,
    private val ids: LeaderboardIdMappings,
) : LeaderboardsClient {
    override val isSupported: Boolean = true

    override suspend fun loadLeaderboards(): Result<List<Leaderboard>> = gameServicesResult {
        val metadata = requireNotNull(leaderboards.loadLeaderboardMetadata(false).awaitGameServices { it.get()?.release() }.get())
        try {
            (0 until metadata.count).map { toLeaderboard(metadata.get(it)) }
        } finally {
            metadata.release()
        }
    }

    override suspend fun submitScore(id: LeaderboardId, score: Long): Result<Unit> = gameServicesResult {
        leaderboards.submitScoreImmediate(ids.providerId(GameServicesProvider.GooglePlayGames, id).value, score).awaitGameServices()
    }

    override suspend fun loadCurrentPlayerScore(
        id: LeaderboardId,
        scope: LeaderboardScope,
        period: LeaderboardPeriod,
    ): Result<LeaderboardScore?> = gameServicesResult {
        leaderboards.loadCurrentPlayerLeaderboardScore(
            ids.providerId(GameServicesProvider.GooglePlayGames, id).value,
            period.googleValue(),
            scope.googleValue(),
        )
            .awaitGameServices().get()?.toLeaderboardScore()
    }

    override suspend fun loadScores(
        id: LeaderboardId,
        query: LeaderboardQuery,
    ): Result<List<LeaderboardScore>> = gameServicesResult {
        var scores = requireNotNull(leaderboards.loadTopScores(
            ids.providerId(GameServicesProvider.GooglePlayGames, id).value,
            query.period.googleValue(), query.scope.googleValue(), 25,
        ).awaitGameServices { it.get()?.release() }.get())
        var first = true
        try {
            collectLeaderboardScores(query) {
                if (!first) {
                    val next = requireNotNull(leaderboards.loadMoreScores(scores.scores, 25, PageDirection.NEXT)
                        .awaitGameServices { it.get()?.release() }.get())
                    scores.release()
                    scores = next
                }
                first = false
                ScorePage(
                    (0 until scores.scores.count).map { scores.scores.get(it).toLeaderboardScore() },
                    DataBufferUtils.hasNextPage(scores.scores),
                )
            }
        } finally {
            scores.release()
        }
    }

    override suspend fun showLeaderboards(): Result<Unit> = gameServicesResult {
        withContext(Dispatchers.Main.immediate) { launcher.launch(leaderboards.allLeaderboardsIntent.awaitGameServices()) }
    }

    override suspend fun showLeaderboard(id: LeaderboardId): Result<Unit> = gameServicesResult {
        withContext(Dispatchers.Main.immediate) {
            launcher.launch(leaderboards.getLeaderboardIntent(ids.providerId(GameServicesProvider.GooglePlayGames, id).value).awaitGameServices())
        }
    }

    private fun toLeaderboard(leaderboard: GoogleLeaderboard): Leaderboard = Leaderboard(
        id = ids.commonId(GameServicesProvider.GooglePlayGames, LeaderboardId(leaderboard.leaderboardId)),
        title = leaderboard.displayName,
    )
}

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
    player = scoreHolder?.let { PlayerIdentity(PlayerId(it.playerId), scoreHolderDisplayName) },
    value = rawScore,
    formattedValue = displayScore,
    rank = rank.takeIf { it > 0 },
    displayName = scoreHolderDisplayName,
)
