package com.mikepenz.gameservices.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mikepenz.gameservices.GameServices
import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.achievements.Achievement
import com.mikepenz.gameservices.achievements.AchievementId
import com.mikepenz.gameservices.achievements.AchievementProgress
import com.mikepenz.gameservices.achievements.AchievementsClient
import com.mikepenz.gameservices.leaderboards.Leaderboard
import com.mikepenz.gameservices.leaderboards.LeaderboardId
import com.mikepenz.gameservices.leaderboards.LeaderboardsClient
import com.mikepenz.gameservices.savedgames.SavedGameConflictId
import com.mikepenz.gameservices.savedgames.SavedGameData
import com.mikepenz.gameservices.savedgames.SavedGameId
import com.mikepenz.gameservices.savedgames.SavedGameReadResult
import com.mikepenz.gameservices.savedgames.SavedGamesClient
import com.mikepenz.gameservices.social.AvatarBytes
import com.mikepenz.gameservices.social.FriendsAccessState
import com.mikepenz.gameservices.social.SocialClient
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

public enum class SampleAction {
    Authenticate,
    Achievements,
    Leaderboards,
    SavedGames,
    SavedGameSelection,
    Friends,
}

public class GameServicesSample public constructor(
    public val services: GameServices,
    public val achievements: AchievementsClient,
    public val leaderboards: LeaderboardsClient,
    public val savedGames: SavedGamesClient,
    public val social: SocialClient,
) {
    public fun availableActions(): Set<SampleAction> {
        return buildSet {
            if (services.support.isSupported) add(SampleAction.Authenticate)
            if (achievements.isSupported) add(SampleAction.Achievements)
            if (leaderboards.isSupported) add(SampleAction.Leaderboards)
            if (savedGames.isSupported) add(SampleAction.SavedGames)
            if (savedGames.isSupported && savedGames.isSelectionPresenterSupported) {
                add(SampleAction.SavedGameSelection)
            }
            if (social.isSupported) add(SampleAction.Friends)
        }
    }
}

@Composable
public fun GameServicesSampleApp(
    sample: GameServicesSample,
    modifier: Modifier = Modifier,
) {
    var state by remember(sample) {
        mutableStateOf(
            SampleScreenState(status = "${sample.services.support.provider}; enter configured IDs before testing."),
        )
    }
    val scope = rememberCoroutineScope()
    GameServicesSampleContent(
        state = state,
        availableActions = sample.availableActions(),
        onFieldChange = { field, value -> state = state.update(field, value) },
        onAction = { operation ->
            scope.launch {
                when (operation) {
                    SampleOperation.Authenticate -> sample.services.authenticate().fold(
                        onSuccess = { player ->
                            state = state.copy(
                                playerId = player.id.value,
                                currentPlayerId = player.id.value,
                                status = "Authentication: $player",
                            )
                        },
                        onFailure = { state = state.copy(status = "Authentication failed: ${it.message}") },
                    )
                    SampleOperation.LoadAchievements -> sample.achievements.loadAchievements(forceReload = true).fold(
                        onSuccess = { achievements ->
                            state = state.copy(
                                achievements = achievements,
                                status = "Loaded ${achievements.size} achievements; choose one to fill its ID.",
                            )
                        },
                        onFailure = { state = state.copy(status = "Load achievements failed: ${it.message}") },
                    )
                    SampleOperation.LoadLeaderboards -> sample.leaderboards.loadLeaderboards().fold(
                        onSuccess = { leaderboards ->
                            state = state.copy(
                                leaderboards = leaderboards,
                                status = "Loaded ${leaderboards.size} leaderboards; choose one to fill its ID.",
                            )
                        },
                        onFailure = { state = state.copy(status = "Load leaderboards failed: ${it.message}") },
                    )
                    SampleOperation.LoadAvatar -> sample.social.loadAvatar(PlayerId(state.playerId)).fold(
                        onSuccess = { avatar ->
                            state = state.copy(
                                avatar = avatar,
                                status = if (avatar == null) "Avatar: unavailable" else "Avatar loaded",
                            )
                        },
                        onFailure = { state = state.copy(status = "Load avatar failed: ${it.message}") },
                    )
                    else -> state = state.copy(status = execute(sample, state, operation))
                }
            }
        },
        onLeaderboardSelected = { id -> state = state.copy(leaderboardId = id) },
        onAchievementSelected = { id -> state = state.copy(achievementId = id) },
        modifier = modifier,
    )
}

internal enum class SampleField {
    AchievementId,
    AchievementProgress,
    LeaderboardId,
    Score,
    SaveId,
    ConflictId,
    PlayerId,
}

internal enum class SampleOperation(
    val label: String,
    val capability: SampleAction,
) {
    Authenticate("Authenticate", SampleAction.Authenticate),
    ShowAchievements("Show achievements", SampleAction.Achievements),
    LoadAchievements("Load achievement IDs", SampleAction.Achievements),
    ReportAchievement("Report achievement progress", SampleAction.Achievements),
    LoadLeaderboards("Load leaderboard IDs", SampleAction.Leaderboards),
    ShowLeaderboards("Show leaderboards", SampleAction.Leaderboards),
    ShowLeaderboard("Show leaderboard", SampleAction.Leaderboards),
    SubmitScore("Submit score", SampleAction.Leaderboards),
    ListSavedGames("List saved games", SampleAction.SavedGames),
    WriteSavedGame("Write saved game", SampleAction.SavedGames),
    ReadSavedGame("Read saved game", SampleAction.SavedGames),
    DeleteSavedGame("Delete saved game", SampleAction.SavedGames),
    ResolveConflict("Resolve conflict", SampleAction.SavedGames),
    SelectSavedGame("Select saved game", SampleAction.SavedGameSelection),
    RequestFriendsAccess("Request friends access", SampleAction.Friends),
    LoadFriends("Load friends", SampleAction.Friends),
    LoadAvatar("Load avatar", SampleAction.Friends),
    ShowPlayerProfile("Compare player profile", SampleAction.Friends),
}

internal data class SampleScreenState(
    val status: String = "Ready",
    val achievementId: String = "",
    val achievementProgress: String = "100",
    val achievements: List<Achievement> = emptyList(),
    val leaderboardId: String = "",
    val leaderboards: List<Leaderboard> = emptyList(),
    val score: String = "",
    val saveId: String = "",
    val conflictId: String = "",
    val playerId: String = "",
    val currentPlayerId: String = "",
    val avatar: AvatarBytes? = null,
) {
    fun update(field: SampleField, value: String): SampleScreenState = when (field) {
        SampleField.AchievementId -> copy(achievementId = value)
        SampleField.AchievementProgress -> copy(achievementProgress = value)
        SampleField.LeaderboardId -> copy(leaderboardId = value)
        SampleField.Score -> copy(score = value)
        SampleField.SaveId -> copy(saveId = value)
        SampleField.ConflictId -> copy(conflictId = value)
        SampleField.PlayerId -> copy(playerId = value)
    }

    fun isValid(operation: SampleOperation): Boolean = when (operation) {
        SampleOperation.ReportAchievement -> achievementId.isNotBlank() && achievementProgress.toIntOrNull() in 0..100
        SampleOperation.ShowLeaderboard -> leaderboardId.isNotBlank()
        SampleOperation.SubmitScore -> leaderboardId.isNotBlank() && score.toLongOrNull() != null
        SampleOperation.WriteSavedGame,
        SampleOperation.ReadSavedGame,
        SampleOperation.DeleteSavedGame,
        -> saveId.isNotBlank()
        SampleOperation.ResolveConflict -> conflictId.isNotBlank()
        SampleOperation.LoadAvatar -> playerId.isNotBlank()
        SampleOperation.ShowPlayerProfile -> playerId.isNotBlank() && playerId != currentPlayerId
        else -> true
    }
}

@Composable
internal fun GameServicesSampleContent(
    state: SampleScreenState,
    availableActions: Set<SampleAction>,
    onFieldChange: (SampleField, String) -> Unit,
    onAction: (SampleOperation) -> Unit,
    onLeaderboardSelected: (String) -> Unit,
    onAchievementSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .safeDrawingPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(state.status, style = MaterialTheme.typography.bodyLarge)
                state.avatar?.let { avatar ->
                    AsyncImage(
                        model = avatar.copyBytes(),
                        contentDescription = "Player avatar",
                        modifier = Modifier.size(96.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SampleOperation.Authenticate.button(state, availableActions, onAction)

                    SampleField.AchievementId.input("Achievement ID", state.achievementId, onFieldChange)
                    SampleField.AchievementProgress.input(
                        "Achievement progress (0-100)",
                        state.achievementProgress,
                        onFieldChange,
                        KeyboardType.Number,
                    )
                    SampleOperation.ShowAchievements.button(state, availableActions, onAction)
                    SampleOperation.LoadAchievements.button(state, availableActions, onAction)
                    state.achievements.forEach { achievement ->
                        Button(
                            onClick = { onAchievementSelected(achievement.id.value) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${achievement.title} (${achievement.id.value})")
                        }
                    }
                    SampleOperation.ReportAchievement.button(state, availableActions, onAction)

                    SampleField.LeaderboardId.input("Leaderboard ID", state.leaderboardId, onFieldChange)
                    SampleField.Score.input("Score", state.score, onFieldChange, KeyboardType.Number)
                    SampleOperation.LoadLeaderboards.button(state, availableActions, onAction)
                    state.leaderboards.forEach { leaderboard ->
                        Button(
                            onClick = { onLeaderboardSelected(leaderboard.id.value) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${leaderboard.title} (${leaderboard.id.value})")
                        }
                    }
                    SampleOperation.ShowLeaderboards.button(state, availableActions, onAction)
                    SampleOperation.ShowLeaderboard.button(state, availableActions, onAction)
                    SampleOperation.SubmitScore.button(state, availableActions, onAction)

                    SampleField.SaveId.input("Saved game ID", state.saveId, onFieldChange)
                    SampleField.ConflictId.input("Conflict ID", state.conflictId, onFieldChange)
                    SampleOperation.ListSavedGames.button(state, availableActions, onAction)
                    SampleOperation.WriteSavedGame.button(state, availableActions, onAction)
                    SampleOperation.ReadSavedGame.button(state, availableActions, onAction)
                    SampleOperation.DeleteSavedGame.button(state, availableActions, onAction)
                    SampleOperation.ResolveConflict.button(state, availableActions, onAction)
                    SampleOperation.SelectSavedGame.button(state, availableActions, onAction)

                    SampleField.PlayerId.input("Player ID", state.playerId, onFieldChange)
                    SampleOperation.RequestFriendsAccess.button(state, availableActions, onAction)
                    SampleOperation.LoadFriends.button(state, availableActions, onAction)
                    SampleOperation.LoadAvatar.button(state, availableActions, onAction)
                    SampleOperation.ShowPlayerProfile.button(state, availableActions, onAction)
                }
            }
        }
    }
}

@Composable
private fun SampleField.input(
    label: String,
    value: String,
    onFieldChange: (SampleField, String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onFieldChange(this, it) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun SampleOperation.button(
    state: SampleScreenState,
    availableActions: Set<SampleAction>,
    onAction: (SampleOperation) -> Unit,
) {
    Button(
        onClick = { onAction(this) },
        modifier = Modifier.fillMaxWidth(),
        enabled = capability in availableActions && state.isValid(this),
    ) {
        Text(label)
    }
}

private suspend fun execute(
    sample: GameServicesSample,
    state: SampleScreenState,
    operation: SampleOperation,
): String = when (operation) {
    SampleOperation.Authenticate -> error("Handled before execute")
    SampleOperation.ShowAchievements -> sample.achievements.showAchievements().message("Achievements")
    SampleOperation.LoadAchievements -> error("Handled before execute")
    SampleOperation.ReportAchievement -> sample.achievements.reportProgress(
        AchievementId(state.achievementId),
        AchievementProgress.Percent(requireNotNull(state.achievementProgress.toIntOrNull())),
    ).message("Achievement progress")
    SampleOperation.LoadLeaderboards -> error("Handled before execute")
    SampleOperation.ShowLeaderboards -> sample.leaderboards.showLeaderboards().message("Leaderboards")
    SampleOperation.ShowLeaderboard -> sample.leaderboards.showLeaderboard(
        LeaderboardId(state.leaderboardId),
    ).message("Leaderboard")
    SampleOperation.SubmitScore -> sample.leaderboards.submitScore(
        LeaderboardId(state.leaderboardId),
        requireNotNull(state.score.toLongOrNull()),
    ).message("Score")
    SampleOperation.ListSavedGames -> sample.savedGames.listSavedGames().message("Saved games")
    SampleOperation.WriteSavedGame -> sample.savedGames.write(
        SavedGameId(state.saveId),
        SavedGameData.of("sample".encodeToByteArray()),
    ).message("Saved game")
    SampleOperation.ReadSavedGame -> sample.savedGames.read(SavedGameId(state.saveId)).message()
    SampleOperation.DeleteSavedGame -> sample.savedGames.delete(SavedGameId(state.saveId)).message("Saved game")
    SampleOperation.ResolveConflict -> sample.savedGames.resolve(
        SavedGameConflictId(state.conflictId),
        SavedGameData.of("sample".encodeToByteArray()),
    ).message("Conflict")
    SampleOperation.SelectSavedGame -> sample.savedGames.showSavedGameSelection().message("Selected saved game")
    SampleOperation.RequestFriendsAccess -> sample.social.requestFriendsAccess().message("Friends access")
    SampleOperation.LoadFriends -> sample.social.requestFriendsAccess().fold(
        onSuccess = { access ->
            if (access == FriendsAccessState.Granted) sample.social.loadFriends().message("Friends")
            else "Friends access: $access"
        },
        onFailure = { "Friends failed: ${it.message}" },
    )
    SampleOperation.LoadAvatar -> error("Handled before execute")
    SampleOperation.ShowPlayerProfile -> sample.social.showPlayerProfile(
        PlayerId(state.playerId),
    ).message("Player profile")
}

private fun <T> Result<T>.message(action: String): String = fold(
    onSuccess = { "$action: $it" },
    onFailure = { "$action failed: ${it.message}" },
)

private fun Result<SavedGameReadResult>.message(): String = fold(
    onSuccess = { result ->
        when (result) {
            SavedGameReadResult.NotFound -> "Saved game: not found"
            is SavedGameReadResult.Loaded -> """
                Saved game: ${result.version.metadata.name}
                ${result.version.data.copyBytes().decodeToString()}
            """.trimIndent()
            is SavedGameReadResult.Conflict -> "Saved game conflict: ${result.conflict.id.value}"
        }
    },
    onFailure = { "Saved game failed: ${it.message}" },
)
