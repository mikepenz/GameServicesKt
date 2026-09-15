package com.mikepenz.gameservices.sample

import com.mikepenz.gameservices.GameServices
import com.mikepenz.gameservices.GameServicesPlatform
import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.GameServicesSupport
import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.achievements.Achievement
import com.mikepenz.gameservices.achievements.AchievementId
import com.mikepenz.gameservices.achievements.AchievementProgress
import com.mikepenz.gameservices.achievements.AchievementsClient
import com.mikepenz.gameservices.leaderboards.LeaderboardId
import com.mikepenz.gameservices.leaderboards.LeaderboardPeriod
import com.mikepenz.gameservices.leaderboards.LeaderboardQuery
import com.mikepenz.gameservices.leaderboards.LeaderboardScope
import com.mikepenz.gameservices.leaderboards.LeaderboardScore
import com.mikepenz.gameservices.leaderboards.LeaderboardsClient
import com.mikepenz.gameservices.savedgames.SavedGameConflictId
import com.mikepenz.gameservices.savedgames.SavedGameData
import com.mikepenz.gameservices.savedgames.SavedGameId
import com.mikepenz.gameservices.savedgames.SavedGameMetadata
import com.mikepenz.gameservices.savedgames.SavedGameReadResult
import com.mikepenz.gameservices.savedgames.SavedGameWriteResult
import com.mikepenz.gameservices.savedgames.SavedGamesClient
import com.mikepenz.gameservices.social.AvatarBytes
import com.mikepenz.gameservices.social.FriendsAccessState
import com.mikepenz.gameservices.social.PlayerProfile
import com.mikepenz.gameservices.social.SocialClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class GameServicesSampleTest {
    @Test
    fun `unsupported clients hide every action`() {
        val unsupported = object : GameServices {
            override val support = GameServicesSupport(GameServicesPlatform.JVM, GameServicesProvider.None, false)
            override val authenticationState = MutableStateFlow<com.mikepenz.gameservices.AuthenticationState>(
                com.mikepenz.gameservices.AuthenticationState.Unsupported,
            )
            override suspend fun authenticate() = error("not called")
        }
        assertEquals(
            emptySet(),
            GameServicesSample(
                unsupported,
                achievements,
                leaderboards,
                savedGames,
                social,
            ).availableActions(),
        )
    }

    private val achievements = object : AchievementsClient {
        override suspend fun loadAchievements(): Result<List<Achievement>> = error("not called")
        override suspend fun reportProgress(id: AchievementId, progress: AchievementProgress): Result<Unit> = error("not called")
        override suspend fun showAchievements(): Result<Unit> = error("not called")
    }

    private val leaderboards = object : LeaderboardsClient {
        override suspend fun submitScore(id: LeaderboardId, score: Long): Result<Unit> = error("not called")
        override suspend fun loadCurrentPlayerScore(id: LeaderboardId, scope: LeaderboardScope, period: LeaderboardPeriod): Result<LeaderboardScore?> = error("not called")
        override suspend fun loadScores(id: LeaderboardId, query: LeaderboardQuery): Result<List<LeaderboardScore>> = error("not called")
        override suspend fun showLeaderboards(): Result<Unit> = error("not called")
        override suspend fun showLeaderboard(id: LeaderboardId): Result<Unit> = error("not called")
    }

    private val savedGames = object : SavedGamesClient {
        override val isSelectionPresenterSupported: Boolean = false
        override suspend fun listSavedGames(): Result<List<SavedGameMetadata>> = error("not called")
        override suspend fun read(id: SavedGameId): Result<SavedGameReadResult> = error("not called")
        override suspend fun write(id: SavedGameId, data: SavedGameData): Result<SavedGameWriteResult> = error("not called")
        override suspend fun delete(id: SavedGameId): Result<Unit> = error("not called")
        override suspend fun resolve(conflictId: SavedGameConflictId, data: SavedGameData): Result<SavedGameWriteResult> = error("not called")
        override suspend fun showSavedGameSelection(): Result<SavedGameMetadata?> = error("not called")
    }

    private val social = object : SocialClient {
        override val friendsAccessState = MutableStateFlow(FriendsAccessState.Unknown)
        override suspend fun requestFriendsAccess(): Result<FriendsAccessState> = error("not called")
        override suspend fun loadFriends(): Result<List<PlayerProfile>> = error("not called")
        override suspend fun loadAvatar(playerId: PlayerId): Result<AvatarBytes?> = error("not called")
        override suspend fun showPlayerProfile(playerId: PlayerId): Result<Unit> = error("not called")
    }
}
