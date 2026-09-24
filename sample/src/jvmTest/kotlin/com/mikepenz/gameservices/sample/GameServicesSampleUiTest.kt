package com.mikepenz.gameservices.sample

import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertTextContains
import com.mikepenz.gameservices.*
import com.mikepenz.gameservices.savedgames.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.assertContentEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mikepenz.gameservices.achievements.Achievement
import com.mikepenz.gameservices.achievements.AchievementId
import com.mikepenz.gameservices.achievements.AchievementState
import com.mikepenz.gameservices.leaderboards.Leaderboard
import com.mikepenz.gameservices.leaderboards.LeaderboardId
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GameServicesSampleUiTest {
    @Test
    fun `app awaits startup then recovers from failure and resolves the selected conflict bytes`() = runComposeUiTest {
        val base = createGameServicesSample()
        val refresh = CompletableDeferred<Result<PlayerIdentity?>>()
        var refreshes = 0
        val authState = MutableStateFlow<AuthenticationState>(AuthenticationState.Unauthenticated)
        val services = object : GameServices {
            override val support = GameServicesSupport(GameServicesPlatform.Android, GameServicesProvider.GooglePlayGames, true)
            override val authenticationState = authState
            override suspend fun refreshAuthentication(): Result<PlayerIdentity?> {
                refreshes++
                return refresh.await()
            }
            override suspend fun authenticate(): Result<PlayerIdentity> = error("Unexpected sign-in")
        }
        val metadata = SavedGameMetadata(SavedGameId("slot"), "slot")
        val read = CompletableDeferred<Result<SavedGameReadResult>>()
        val resolved = CompletableDeferred<Result<SavedGameWriteResult>>()
        var reads = 0
        var resolutions = 0
        val saves = object : SavedGamesClient by base.savedGames {
            override val isSupported = true
            override suspend fun read(id: SavedGameId): Result<SavedGameReadResult> {
                assertEquals(metadata.id, id)
                reads++
                return if (reads == 1) Result.failure(IllegalStateException("Offline")) else read.await()
            }
            override suspend fun resolve(conflictId: SavedGameConflictId, data: SavedGameData): Result<SavedGameWriteResult> {
                resolutions++
                assertEquals(SavedGameConflictId("conflict"), conflictId)
                assertContentEquals("second edited".encodeToByteArray(), data.copyBytes())
                return resolved.await()
            }
        }
        val sample = GameServicesSample(services, base.achievements, base.leaderboards, saves, base.social)
        setContent { GameServicesSampleApp(sample) }
        onNodeWithText("Authenticate").assertIsNotEnabled()
        runOnIdle {
            assertEquals(1, refreshes)
            refresh.complete(Result.success(null))
        }
        onNodeWithText("Authenticate").assertIsEnabled()
        onNodeWithText("Saved game ID").performScrollTo().performTextReplacement("slot")
        onNodeWithText("Read saved game").performScrollTo().performClick()
        onNodeWithText("Read failed: Offline").assertExists()
        onNodeWithText("Read saved game").assertIsEnabled().performClick()
        onNodeWithText("Read saved game").assertIsNotEnabled()
        runOnIdle {
            assertEquals(2, reads)
            read.complete(Result.success(SavedGameReadResult.Conflict(SavedGameConflict(
                SavedGameConflictId("conflict"), listOf("first", "second").map {
                    SavedGameVersion(metadata, SavedGameData.of(it.encodeToByteArray()))
                },
            ))))
        }
        onNodeWithText("Use version 2: second").performScrollTo().performClick()
        onNodeWithText("Saved data (UTF-8)").performScrollTo().assertTextContains("second")
            .performTextReplacement("second edited")
        onNodeWithText("Resolve conflict").performScrollTo().performClick()
        onNodeWithText("Resolve conflict").assertIsNotEnabled()
        runOnIdle {
            assertEquals(1, resolutions)
            resolved.complete(Result.success(SavedGameWriteResult.Saved(metadata)))
        }
        onNodeWithText("Saved slot").assertExists()
        onNodeWithText("Resolve conflict").assertIsNotEnabled()
        onNodeWithText("Read saved game").performScrollTo().assertIsEnabled()
        runOnIdle { authState.value = AuthenticationState.Authenticated(PlayerIdentity(PlayerId("changed-account"), "Changed")) }
        onNodeWithText("Player ID").performScrollTo().assertTextContains("changed-account")
    }

    @Test
    fun `unavailable actions stay visible and disabled`() = runComposeUiTest {
        setContent {
            GameServicesSampleContent(
                state = SampleScreenState(),
                availableActions = setOf(SampleAction.Authenticate, SampleAction.Achievements),
                onFieldChange = { _, _ -> },
                onAction = {},
                onLeaderboardSelected = {},
                onAchievementSelected = {},
            )
        }

        onNodeWithText("Authenticate").assertIsEnabled()
        onNodeWithText("Show achievements").assertIsEnabled()
        onNodeWithText("Select saved game").assertIsNotEnabled()
        onNodeWithText("Report achievement progress").assertIsNotEnabled()
    }

    @Test
    fun `loaded leaderboard fills its ID`() = runComposeUiTest {
        var selectedId = ""
        setContent {
            GameServicesSampleContent(
                state = SampleScreenState(leaderboards = listOf(Leaderboard(LeaderboardId("test-id"), "Test leaderboard"))),
                availableActions = setOf(SampleAction.Leaderboards),
                onFieldChange = { _, _ -> },
                onAction = {},
                onLeaderboardSelected = { selectedId = it },
                onAchievementSelected = {},
            )
        }

        onNodeWithText("Test leaderboard (test-id)").performClick()

        assertEquals("test-id", selectedId)
    }

    @Test
    fun `loaded achievement fills its ID`() = runComposeUiTest {
        var selectedId = ""
        setContent {
            GameServicesSampleContent(
                state = SampleScreenState(
                    achievements = listOf(
                        Achievement(
                            id = AchievementId("test-id"),
                            title = "Test achievement",
                            description = "Description",
                            points = 10,
                            isHidden = false,
                            state = AchievementState.Locked,
                            completionPercent = 0,
                        ),
                    ),
                ),
                availableActions = setOf(SampleAction.Achievements),
                onFieldChange = { _, _ -> },
                onAction = {},
                onLeaderboardSelected = {},
                onAchievementSelected = { selectedId = it },
            )
        }

        onNodeWithText("Test achievement (test-id)").performClick()

        assertEquals("test-id", selectedId)
    }
    @Test
    fun `pending operations disable provider actions`() = runComposeUiTest {
        setContent {
            GameServicesSampleContent(
                state = SampleScreenState(busy = true, leaderboardId = "scores"),
                availableActions = SampleAction.entries.toSet(),
                onFieldChange = { _, _ -> }, onAction = {}, onLeaderboardSelected = {}, onAchievementSelected = {},
            )
        }
        onNodeWithText("Authenticate").assertIsNotEnabled()
        onNodeWithText("Load scores").assertIsNotEnabled()
        onNodeWithText("Load my score").assertIsNotEnabled()
    }

    @Test
    fun `conflict version fills editable save data`() = runComposeUiTest {
        var chosen = ""
        setContent {
            GameServicesSampleContent(
                state = SampleScreenState(conflictId = "conflict", conflictVersions = listOf("first", "second")),
                availableActions = setOf(SampleAction.SavedGames),
                onFieldChange = { field, value -> if (field == SampleField.SaveData) chosen = value },
                onAction = {}, onLeaderboardSelected = {}, onAchievementSelected = {},
            )
        }
        onNodeWithText("Use version 2: second").performScrollTo().performClick()
        assertEquals("second", chosen)
    }
}
