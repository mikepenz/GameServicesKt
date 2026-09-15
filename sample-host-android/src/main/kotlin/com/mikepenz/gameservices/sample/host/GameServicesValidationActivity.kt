package com.mikepenz.gameservices.sample.host

import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.mikepenz.gameservices.achievements.AchievementId
import com.mikepenz.gameservices.achievements.AchievementProgress
import com.mikepenz.gameservices.leaderboards.LeaderboardId
import com.mikepenz.gameservices.sample.GameServicesSample
import com.mikepenz.gameservices.sample.SampleAction
import com.mikepenz.gameservices.sample.createGameServicesSample
import com.mikepenz.gameservices.savedgames.SavedGameConflictId
import com.mikepenz.gameservices.savedgames.SavedGameData
import com.mikepenz.gameservices.savedgames.SavedGameId
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

public class GameServicesValidationActivity : ComponentActivity() {
    private val scope = MainScope()
    private lateinit var sample: GameServicesSample
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sample = createGameServicesSample(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        status = TextView(this).also(content::addView)
        val achievementId = content.input("Achievement ID")
        val leaderboardId = content.input("Leaderboard ID")
        val score = content.input("Score", inputType = android.text.InputType.TYPE_CLASS_NUMBER)
        val saveId = content.input("Saved game ID")
        val conflictId = content.input("Conflict ID")
        val playerId = content.input("Player ID")

        val actions = sample.availableActions()
        content.action(actions, SampleAction.Authenticate, "Authenticate") {
            sample.services.authenticate().message("Authenticated")
        }
        content.action(actions, SampleAction.Achievements, "Show achievements") {
            sample.achievements.showAchievements().message("Achievements")
        }
        content.action(actions, SampleAction.Achievements, "Report achievement at 100%") {
            sample.achievements.reportProgress(
                AchievementId(achievementId.text.toString()),
                AchievementProgress.Percent(100),
            ).message("Achievement progress")
        }
        content.action(actions, SampleAction.Leaderboards, "Show leaderboards") {
            sample.leaderboards.showLeaderboards().message("Leaderboards")
        }
        content.action(actions, SampleAction.Leaderboards, "Show leaderboard") {
            sample.leaderboards.showLeaderboard(LeaderboardId(leaderboardId.text.toString()))
                .message("Leaderboard")
        }
        content.action(actions, SampleAction.Leaderboards, "Submit score") {
            sample.leaderboards.submitScore(
                LeaderboardId(leaderboardId.text.toString()),
                score.text.toString().toLong(),
            ).message("Score")
        }
        content.action(actions, SampleAction.SavedGames, "List saved games") {
            sample.savedGames.listSavedGames().message("Saved games")
        }
        content.action(actions, SampleAction.SavedGames, "Write saved game") {
            sample.savedGames.write(
                SavedGameId(saveId.text.toString()),
                SavedGameData.of("sample".encodeToByteArray()),
            ).message("Saved game")
        }
        content.action(actions, SampleAction.SavedGames, "Read saved game") {
            sample.savedGames.read(SavedGameId(saveId.text.toString())).message("Saved game")
        }
        content.action(actions, SampleAction.SavedGames, "Delete saved game") {
            sample.savedGames.delete(SavedGameId(saveId.text.toString())).message("Saved game")
        }
        content.action(actions, SampleAction.SavedGames, "Resolve conflict") {
            sample.savedGames.resolve(
                SavedGameConflictId(conflictId.text.toString()),
                SavedGameData.of("sample".encodeToByteArray()),
            ).message("Conflict")
        }
        content.action(actions, SampleAction.SavedGameSelection, "Select saved game") {
            sample.savedGames.showSavedGameSelection().message("Selected saved game")
        }
        content.action(actions, SampleAction.Friends, "Request friends access") {
            sample.social.requestFriendsAccess().message("Friends access")
        }
        content.action(actions, SampleAction.Friends, "Load friends") {
            sample.social.loadFriends().message("Friends")
        }
        content.action(actions, SampleAction.Friends, "Load avatar") {
            sample.social.loadAvatar(com.mikepenz.gameservices.PlayerId(playerId.text.toString()))
                .message("Avatar")
        }
        content.action(actions, SampleAction.Friends, "Show player profile") {
            sample.social.showPlayerProfile(com.mikepenz.gameservices.PlayerId(playerId.text.toString()))
                .message("Player profile")
        }
        status.text = "${sample.services.support.provider}; enter configured IDs before testing."
        setContentView(ScrollView(this).apply { addView(content) })
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun LinearLayout.input(hint: String, inputType: Int = android.text.InputType.TYPE_CLASS_TEXT): EditText =
        EditText(context).apply {
            this.hint = hint
            this.inputType = inputType
            addView(this, LinearLayout.LayoutParams(MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun LinearLayout.action(
        actions: Set<SampleAction>,
        action: SampleAction,
        label: String,
        block: suspend () -> String,
    ) {
        if (action !in actions) return
        addView(Button(context).apply {
            text = label
            setOnClickListener { scope.launch { status.text = block() } }
        })
    }

    private fun <T> Result<T>.message(action: String): String = fold(
        onSuccess = { "$action: $it" },
        onFailure = { "$action failed: ${it.message}" },
    )
}
