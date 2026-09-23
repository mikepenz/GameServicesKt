package com.mikepenz.gameservices.sample

import androidx.compose.runtime.saveable.SaverScope
import com.mikepenz.gameservices.GameServices
import com.mikepenz.gameservices.GameServicesPlatform
import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.GameServicesSupport
import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.achievements.Achievement
import com.mikepenz.gameservices.achievements.AchievementId
import com.mikepenz.gameservices.achievements.AchievementProgress
import com.mikepenz.gameservices.achievements.AchievementsClient
import com.mikepenz.gameservices.leaderboards.Leaderboard
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

class GameServicesSampleTest {
    private var achievementsSupported = false
    private var leaderboardsSupported = false
    private var savedGamesSupported = false
    private var selectionSupported = false
    private var socialSupported = false

    @Test
    fun `unsupported clients hide every action`() {
        assertEquals(
            emptySet(),
            GameServicesSample(
                services(supported = false),
                achievements,
                leaderboards,
                savedGames,
                social,
            ).availableActions(),
        )
    }

    @Test
    fun `actions follow each client capability`() {
        leaderboardsSupported = true
        savedGamesSupported = true

        assertEquals(
            setOf(SampleAction.Authenticate, SampleAction.Leaderboards, SampleAction.SavedGames),
            GameServicesSample(
                services(supported = true),
                achievements,
                leaderboards,
                savedGames,
                social,
            ).availableActions(),
        )
    }

    @Test
    fun `save selection requires saved games and a presenter`() {
        savedGamesSupported = true
        selectionSupported = true

        assertEquals(
            setOf(SampleAction.SavedGames, SampleAction.SavedGameSelection),
            GameServicesSample(
                services(supported = false),
                achievements,
                leaderboards,
                savedGames,
                social,
            ).availableActions(),
        )
    }

    @Test
    fun `query limits and portable save names are validated before dispatch`() {
        val state = SampleScreenState(leaderboardId = "scores", saveId = "slot-1")
        assertTrue(state.isValid(SampleOperation.LoadScores))
        assertTrue(state.isValid(SampleOperation.WriteSavedGame))
        assertFalse(state.copy(startRank = "1001").isValid(SampleOperation.LoadScores))
        assertFalse(state.copy(limit = "26").isValid(SampleOperation.LoadScores))
        assertFalse(state.copy(saveId = "bad/name").isValid(SampleOperation.WriteSavedGame))
        assertFalse(state.copy(busy = true).isValid(SampleOperation.Authenticate))
    }

    @Test
    fun `restoring fields clears pending operations and session conflict handles`() {
        val original = SampleScreenState(achievementId = "achievement", saveId = "slot", saveData = "chosen bytes",
            leaderboardId = "board", startRank = "20", limit = "10", busy = true, conflictId = "old-session")
        val scope = object : SaverScope { override fun canBeSaved(value: Any): Boolean = true }
        val saved = with(SampleScreenState.Saver) { scope.save(original) }
        val restored = requireNotNull(SampleScreenState.Saver.restore(requireNotNull(saved)))
        assertEquals(original.saveData, restored.saveData)
        assertEquals(original.achievementId, restored.achievementId)
        assertEquals(original.startRank, restored.startRank)
        assertFalse(restored.busy)
        assertEquals("", restored.conflictId)
    }

    @Test
    fun `saved state omits oversized fields without truncating live data`() {
        val scope = object : SaverScope { override fun canBeSaved(value: Any): Boolean = true }
        for (size in listOf(16_384, 16_385, 1_000_000)) {
            val original = SampleScreenState(saveId = "slot", saveData = "x".repeat(size), playerId = "p".repeat(size))
            val saved = requireNotNull(with(SampleScreenState.Saver) { scope.save(original) })
            val restored = requireNotNull(SampleScreenState.Saver.restore(saved))
            assertEquals("slot", restored.saveId)
            assertEquals(if (size <= 16_384) original.saveData else "", restored.saveData)
            assertEquals(if (size <= 16_384) original.playerId else "", restored.playerId)
            assertEquals(size > 16_384, restored.fieldsOmittedOnRestore)
            assertEquals(size, original.saveData.length)
            val savedAgain = requireNotNull(with(SampleScreenState.Saver) { scope.save(restored) })
            assertEquals(restored.fieldsOmittedOnRestore, SampleScreenState.Saver.restore(savedAgain)?.fieldsOmittedOnRestore)
        }
    }

    private fun services(supported: Boolean) = object : GameServices {
        override val support = GameServicesSupport(
            if (supported) GameServicesPlatform.Android else GameServicesPlatform.JVM,
            if (supported) GameServicesProvider.GooglePlayGames else GameServicesProvider.None,
            supported,
        )
        override val authenticationState = MutableStateFlow<com.mikepenz.gameservices.AuthenticationState>(
            if (supported) {
                com.mikepenz.gameservices.AuthenticationState.Unauthenticated
            } else {
                com.mikepenz.gameservices.AuthenticationState.Unsupported
            },
        )
        override suspend fun refreshAuthentication() = error("not called")
        override suspend fun authenticate() = error("not called")
    }

    private val achievements = object : AchievementsClient {
        override val isSupported: Boolean get() = achievementsSupported
        override suspend fun loadAchievements(forceReload: Boolean): Result<List<Achievement>> = error("not called")
        override suspend fun reportProgress(id: AchievementId, progress: AchievementProgress): Result<Unit> = error("not called")
        override suspend fun showAchievements(): Result<Unit> = error("not called")
    }

    private val leaderboards = object : LeaderboardsClient {
        override val isSupported: Boolean get() = leaderboardsSupported
        override suspend fun loadLeaderboards(): Result<List<Leaderboard>> = error("not called")
        override suspend fun submitScore(id: LeaderboardId, score: Long): Result<Unit> = error("not called")
        override suspend fun loadCurrentPlayerScore(id: LeaderboardId, scope: LeaderboardScope, period: LeaderboardPeriod): Result<LeaderboardScore?> = error("not called")
        override suspend fun loadScores(id: LeaderboardId, query: LeaderboardQuery): Result<List<LeaderboardScore>> = error("not called")
        override suspend fun showLeaderboards(): Result<Unit> = error("not called")
        override suspend fun showLeaderboard(id: LeaderboardId): Result<Unit> = error("not called")
    }

    private val savedGames = object : SavedGamesClient {
        override val isSupported: Boolean get() = savedGamesSupported
        override val isSelectionPresenterSupported: Boolean get() = selectionSupported
        override suspend fun listSavedGames(): Result<List<SavedGameMetadata>> = error("not called")
        override suspend fun read(id: SavedGameId): Result<SavedGameReadResult> = error("not called")
        override suspend fun write(id: SavedGameId, data: SavedGameData): Result<SavedGameWriteResult> = error("not called")
        override suspend fun delete(id: SavedGameId): Result<Unit> = error("not called")
        override suspend fun resolve(conflictId: SavedGameConflictId, data: SavedGameData): Result<SavedGameWriteResult> = error("not called")
        override suspend fun showSavedGameSelection(): Result<SavedGameMetadata?> = error("not called")
    }

    private val social = object : SocialClient {
        override val isSupported: Boolean get() = socialSupported
        override val friendsAccessState = MutableStateFlow(FriendsAccessState.Unknown)
        override suspend fun requestFriendsAccess(): Result<FriendsAccessState> = error("not called")
        override suspend fun loadFriends(): Result<List<PlayerProfile>> = error("not called")
        override suspend fun loadAvatar(playerId: PlayerId): Result<AvatarBytes?> = error("not called")
        override suspend fun showPlayerProfile(playerId: PlayerId): Result<Unit> = error("not called")
    }
}
