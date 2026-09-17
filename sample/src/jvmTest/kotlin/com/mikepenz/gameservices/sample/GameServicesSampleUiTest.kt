package com.mikepenz.gameservices.sample

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mikepenz.gameservices.achievements.Achievement
import com.mikepenz.gameservices.achievements.AchievementId
import com.mikepenz.gameservices.achievements.AchievementState
import com.mikepenz.gameservices.leaderboards.Leaderboard
import com.mikepenz.gameservices.leaderboards.LeaderboardId
import kotlin.test.assertEquals
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GameServicesSampleUiTest {
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
}
