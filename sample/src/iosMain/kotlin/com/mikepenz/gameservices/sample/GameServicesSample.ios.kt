package com.mikepenz.gameservices.sample

import com.mikepenz.gameservices.gamecenter.*

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

public fun createGameServicesSample(
    presentingViewController: () -> UIViewController,
): GameServicesSample = GameCenterBackend(presentingViewController).let { backend -> GameServicesSample(
    services = backend,
    achievements = backend.createAchievementsClient(),
    leaderboards = backend.createLeaderboardsClient(),
    savedGames = backend.createSavedGamesClient(),
    social = backend.createSocialClient(),
) }

public fun createMainViewController(): UIViewController {
    lateinit var controller: UIViewController
    controller = ComposeUIViewController {
        val sample = remember {
            createGameServicesSample {
                controller.presentedViewController ?: controller
            }
        }
        GameServicesSampleApp(sample)
    }
    return controller
}
