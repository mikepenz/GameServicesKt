package com.mikepenz.gameservices.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mikepenz.gameservices.AuthenticationState
import com.mikepenz.gameservices.GameServices
import com.mikepenz.gameservices.GameServicesOperation
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
import com.mikepenz.gameservices.leaderboards.LeaderboardsClient
import com.mikepenz.gameservices.savedgames.SavedGameConflict
import com.mikepenz.gameservices.savedgames.SavedGameConflictId
import com.mikepenz.gameservices.savedgames.SavedGameData
import com.mikepenz.gameservices.savedgames.SavedGameId
import com.mikepenz.gameservices.savedgames.SavedGameReadResult
import com.mikepenz.gameservices.savedgames.SavedGameWriteResult
import com.mikepenz.gameservices.savedgames.SavedGamesClient
import com.mikepenz.gameservices.social.AvatarBytes
import com.mikepenz.gameservices.social.FriendsAccessState
import com.mikepenz.gameservices.social.SocialClient
import kotlinx.coroutines.CancellationException
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
    internal fun availableOperations(): Set<SampleOperation> {
        val operations = services.supportedOperations + achievements.supportedOperations + leaderboards.supportedOperations +
            savedGames.supportedOperations + social.supportedOperations
        return SampleOperation.entries.filter { GameServicesOperation.valueOf(it.name) in operations }.toSet()
    }

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
    var state by rememberSaveable(sample, stateSaver = SampleScreenState.Saver) {
        mutableStateOf(
            SampleScreenState(status = "${sample.services.support.provider}; enter configured IDs before testing."),
        )
    }
    val scope = rememberCoroutineScope()
    val authentication by sample.services.authenticationState.collectAsState()
    LaunchedEffect(sample) {
        if (sample.services.support.isSupported) {
            state = state.copy(busy = true)
            try {
                val result = sample.services.refreshAuthentication()
                state = state.copy(status = result.message("Authentication refresh"))
            } finally {
                state = state.copy(busy = false)
            }
        }
    }
    LaunchedEffect(authentication) {
        val player = (authentication as? AuthenticationState.Authenticated)?.player
        state = state.copy(
            currentPlayerId = player?.id?.value.orEmpty(),
            playerId = if (state.playerId.isEmpty() || state.playerId == state.currentPlayerId) player?.id?.value.orEmpty() else state.playerId,
            avatar = if (player?.id?.value == state.currentPlayerId) state.avatar else null,
        )
    }
    GameServicesSampleContent(
        state = state,
        availableActions = sample.availableActions(),
        availableOperations = sample.availableOperations(),
        onFieldChange = { field, value -> state = state.update(field, value) },
        onAction = { operation ->
            if (operation in sample.availableOperations() && state.isValid(operation)) {
                val input = state
                state = state.copy(busy = true, status = "${operation.label}…")
                scope.launch {
                    try {
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
                            SampleOperation.LoadAvatar -> sample.social.loadAvatar(PlayerId(input.playerId)).fold(
                                onSuccess = { avatar ->
                                    state = state.copy(
                                        avatar = avatar,
                                        status = if (avatar == null) "Avatar: unavailable" else "Avatar loaded",
                                    )
                                },
                                onFailure = { state = state.copy(status = "Load avatar failed: ${it.message}") },
                            )
                            SampleOperation.ReadSavedGame -> {
                                val result = sample.savedGames.read(SavedGameId(input.saveId))
                                state = result.fold(
                                    onSuccess = { read -> when (read) {
                                        SavedGameReadResult.NotFound -> state.copy(status = "Saved game not found", conflictVersions = emptyList(), conflictId = "")
                                        is SavedGameReadResult.Loaded -> state.copy(status = "Saved game loaded", saveData = read.version.data.copyBytes().decodeToString(), conflictVersions = emptyList(), conflictId = "")
                                        is SavedGameReadResult.Conflict -> state.withConflict(read.conflict)
                                    } },
                                    onFailure = { state.copy(status = "Read failed: ${it.message}") },
                                )
                            }
                            SampleOperation.WriteSavedGame, SampleOperation.ResolveConflict -> {
                                val data = SavedGameData.of(input.saveData.encodeToByteArray())
                                val result = if (operation == SampleOperation.WriteSavedGame) sample.savedGames.write(SavedGameId(input.saveId), data)
                                    else sample.savedGames.resolve(SavedGameConflictId(input.conflictId), data)
                                state = result.fold(
                                    onSuccess = { write -> when (write) {
                                        is SavedGameWriteResult.Saved -> state.copy(status = "Saved ${write.metadata.name}", conflictVersions = emptyList(), conflictId = "")
                                        is SavedGameWriteResult.Conflict -> state.withConflict(write.conflict)
                                    } },
                                    onFailure = { state.copy(status = "Save failed: ${it.message}") },
                                )
                            }
                            SampleOperation.SelectSavedGame -> {
                                val result = sample.savedGames.showSavedGameSelection()
                                state = result.fold(
                                    onSuccess = { selected -> state.copy(saveId = selected?.id?.value ?: state.saveId, status = selected?.name ?: "Selection cancelled") },
                                    onFailure = { state.copy(status = "Selection failed: ${it.message}") },
                                )
                            }
                            else -> {
                                val message = execute(sample, input, operation)
                                state = state.copy(status = message)
                            }
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        state = state.copy(status = "${operation.label} failed: ${error.message}")
                    } finally {
                        state = state.copy(busy = false)
                    }
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
    StartRank,
    Limit,
    Scope,
    Period,
    SaveData,
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
    LoadScores("Load scores", SampleAction.Leaderboards),
    LoadCurrentScore("Load my score", SampleAction.Leaderboards),
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
    val busy: Boolean = false,
    val fieldsOmittedOnRestore: Boolean = false,
    val achievementId: String = "",
    val achievementProgress: String = "100",
    val achievements: List<Achievement> = emptyList(),
    val leaderboardId: String = "",
    val leaderboards: List<Leaderboard> = emptyList(),
    val score: String = "",
    val startRank: String = "1",
    val limit: String = "25",
    val scope: LeaderboardScope = LeaderboardScope.Global,
    val period: LeaderboardPeriod = LeaderboardPeriod.AllTime,
    val saveData: String = "sample",
    val conflictVersions: List<String> = emptyList(),
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
        SampleField.StartRank -> copy(startRank = value)
        SampleField.Limit -> copy(limit = value)
        SampleField.Scope -> copy(scope = LeaderboardScope.valueOf(value))
        SampleField.Period -> copy(period = LeaderboardPeriod.valueOf(value))
        SampleField.SaveData -> copy(saveData = value)
        SampleField.SaveId -> copy(saveId = value)
        SampleField.ConflictId -> copy(conflictId = value)
        SampleField.PlayerId -> copy(playerId = value)
    }

    fun isValid(operation: SampleOperation): Boolean = !busy && when (operation) {
        SampleOperation.ReportAchievement -> achievementId.isNotBlank() && achievementProgress.toIntOrNull() in 0..100
        SampleOperation.ShowLeaderboard, SampleOperation.LoadCurrentScore -> leaderboardId.isNotBlank()
        SampleOperation.LoadScores -> leaderboardId.isNotBlank() && startRank.toIntOrNull() in 1..1000 && limit.toIntOrNull() in 1..25
        SampleOperation.SubmitScore -> leaderboardId.isNotBlank() && score.toLongOrNull() != null
        SampleOperation.WriteSavedGame,
        SampleOperation.ReadSavedGame,
        SampleOperation.DeleteSavedGame,
        -> saveId.length in 1..100 && saveId.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "-._~" }
        SampleOperation.ResolveConflict -> conflictId.isNotBlank()
        SampleOperation.LoadAvatar -> playerId.isNotBlank()
        SampleOperation.ShowPlayerProfile -> playerId.isNotBlank() && playerId != currentPlayerId
        else -> true
    }

    fun withConflict(conflict: SavedGameConflict): SampleScreenState = copy(
        status = "Choose a conflict version or edit the saved data before resolving",
        conflictId = conflict.id.value,
        conflictVersions = conflict.versions.map { it.data.copyBytes().decodeToString() },
    )

    companion object {
        val Saver = listSaver<SampleScreenState, String>(
            save = { state ->
                val fields = listOf(state.achievementId, state.achievementProgress, state.leaderboardId, state.score,
                    state.saveId, state.playerId, state.startRank, state.limit, state.scope.name, state.period.name, state.saveData)
                // ponytail: keep Bundle text bounded; use file-backed drafts if large edits must survive recreation.
                fields.map { it.takeIf { field -> field.length <= 16_384 }.orEmpty() } +
                    (state.fieldsOmittedOnRestore || fields.any { it.length > 16_384 }).toString()
            },
            restore = { SampleScreenState(achievementId = it[0], achievementProgress = it[1], leaderboardId = it[2],
                score = it[3], saveId = it[4], playerId = it[5], startRank = it[6], limit = it[7],
                scope = LeaderboardScope.valueOf(it[8]), period = LeaderboardPeriod.valueOf(it[9]), saveData = it[10],
                fieldsOmittedOnRestore = it.getOrNull(11).toBoolean()) },
        )
    }

}

@Composable
public fun GameServicesSamplePreview() {
    GameServicesSampleContent(
        state = SampleScreenState(status = "Google Play Games; enter configured IDs before testing."),
        availableActions = SampleAction.entries.toSet(),
        onFieldChange = { _, _ -> },
        onAction = {},
        onLeaderboardSelected = {},
        onAchievementSelected = {},
    )
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
    availableOperations: Set<SampleOperation> = SampleOperation.entries.filter { it.capability in availableActions }.toSet(),
) {
    MaterialTheme {
        Surface(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .safeDrawingPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(state.status, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()))
                if (state.fieldsOmittedOnRestore) {
                    Text("Large fields were not restored. Read the saved game again before writing.")
                }
                state.avatar?.let { avatar ->
                    AsyncImage(
                        model = remember(avatar) { avatar.copyBytes() },
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
                    SampleOperation.Authenticate.button(state, availableOperations, onAction)

                    SampleField.AchievementId.input("Achievement ID", state.achievementId, onFieldChange)
                    SampleField.AchievementProgress.input(
                        "Achievement progress (0-100)",
                        state.achievementProgress,
                        onFieldChange,
                        KeyboardType.Number,
                    )
                    SampleOperation.ShowAchievements.button(state, availableOperations, onAction)
                    SampleOperation.LoadAchievements.button(state, availableOperations, onAction)
                    state.achievements.forEach { achievement ->
                        Button(
                            onClick = { onAchievementSelected(achievement.id.value) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${achievement.title} (${achievement.id.value})")
                        }
                    }
                    SampleOperation.ReportAchievement.button(state, availableOperations, onAction)

                    SampleField.LeaderboardId.input("Leaderboard ID", state.leaderboardId, onFieldChange)
                    SampleField.Score.input("Score", state.score, onFieldChange, KeyboardType.Number)
                    SampleOperation.LoadLeaderboards.button(state, availableOperations, onAction)
                    state.leaderboards.forEach { leaderboard ->
                        Button(
                            onClick = { onLeaderboardSelected(leaderboard.id.value) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${leaderboard.title} (${leaderboard.id.value})")
                        }
                    }
                    SampleOperation.ShowLeaderboards.button(state, availableOperations, onAction)
                    SampleOperation.ShowLeaderboard.button(state, availableOperations, onAction)
                    SampleOperation.SubmitScore.button(state, availableOperations, onAction)
                    SampleField.StartRank.input("Start rank (1-1000)", state.startRank, onFieldChange, KeyboardType.Number)
                    SampleField.Limit.input("Entry limit (1-25)", state.limit, onFieldChange, KeyboardType.Number)
                    Button(onClick = { onFieldChange(SampleField.Scope, LeaderboardScope.entries[(state.scope.ordinal + 1) % LeaderboardScope.entries.size].name) }) {
                        Text("Scope: ${state.scope}")
                    }
                    Button(onClick = { onFieldChange(SampleField.Period, LeaderboardPeriod.entries[(state.period.ordinal + 1) % LeaderboardPeriod.entries.size].name) }) {
                        Text("Period: ${state.period}")
                    }
                    SampleOperation.LoadScores.button(state, availableOperations, onAction)
                    SampleOperation.LoadCurrentScore.button(state, availableOperations, onAction)

                    SampleField.SaveId.input("Saved game ID", state.saveId, onFieldChange)
                    SampleField.SaveData.input("Saved data (UTF-8)", state.saveData, onFieldChange)
                    SampleField.ConflictId.input("Conflict ID", state.conflictId, onFieldChange)
                    state.conflictVersions.forEachIndexed { index, data ->
                        Button(onClick = { onFieldChange(SampleField.SaveData, data) }, enabled = !state.busy) {
                            Text("Use version ${index + 1}: $data")
                        }
                    }
                    SampleOperation.ListSavedGames.button(state, availableOperations, onAction)
                    SampleOperation.WriteSavedGame.button(state, availableOperations, onAction)
                    SampleOperation.ReadSavedGame.button(state, availableOperations, onAction)
                    SampleOperation.DeleteSavedGame.button(state, availableOperations, onAction)
                    SampleOperation.ResolveConflict.button(state, availableOperations, onAction)
                    SampleOperation.SelectSavedGame.button(state, availableOperations, onAction)

                    SampleField.PlayerId.input("Player ID", state.playerId, onFieldChange)
                    SampleOperation.RequestFriendsAccess.button(state, availableOperations, onAction)
                    SampleOperation.LoadFriends.button(state, availableOperations, onAction)
                    SampleOperation.LoadAvatar.button(state, availableOperations, onAction)
                    SampleOperation.ShowPlayerProfile.button(state, availableOperations, onAction)
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
    availableOperations: Set<SampleOperation>,
    onAction: (SampleOperation) -> Unit,
) {
    Button(
        onClick = { onAction(this) },
        modifier = Modifier.fillMaxWidth(),
        enabled = this in availableOperations && state.isValid(this),
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
    SampleOperation.LoadScores -> sample.leaderboards.loadScores(
        LeaderboardId(state.leaderboardId), LeaderboardQuery(state.scope, state.period, state.startRank.toInt(), state.limit.toInt()),
    ).message("Scores")
    SampleOperation.LoadCurrentScore -> sample.leaderboards.loadCurrentPlayerScore(
        LeaderboardId(state.leaderboardId), state.scope, state.period,
    ).message("My score")
    SampleOperation.ListSavedGames -> sample.savedGames.listSavedGames().message("Saved games")
    SampleOperation.WriteSavedGame, SampleOperation.ReadSavedGame, SampleOperation.ResolveConflict,
    SampleOperation.SelectSavedGame -> error("Handled before execute")
    SampleOperation.DeleteSavedGame -> sample.savedGames.delete(SavedGameId(state.saveId)).message("Saved game")
    SampleOperation.RequestFriendsAccess -> sample.social.requestFriendsAccess().message("Friends access")
    SampleOperation.LoadFriends -> sample.social.loadFriends().message("Friends")
    SampleOperation.LoadAvatar -> error("Handled before execute")
    SampleOperation.ShowPlayerProfile -> sample.social.showPlayerProfile(
        PlayerId(state.playerId),
    ).message("Player profile")
}

private fun <T> Result<T>.message(action: String): String = fold(
    onSuccess = { "$action: $it" },
    onFailure = { "$action failed: ${it.message}" },
)
