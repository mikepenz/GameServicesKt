package com.mikepenz.gameservices.sample

import com.mikepenz.gameservices.createGameServices
import com.mikepenz.gameservices.achievements.createAchievementsClient
import com.mikepenz.gameservices.leaderboards.createLeaderboardsClient
import com.mikepenz.gameservices.savedgames.createSavedGamesClient
import com.mikepenz.gameservices.social.createSocialClient
import platform.UIKit.UIViewController

public fun createGameServicesSample(
    presentingViewController: () -> UIViewController,
): GameServicesSample = GameServicesSample(
    services = createGameServices(presentingViewController),
    achievements = createAchievementsClient(presentingViewController),
    leaderboards = createLeaderboardsClient(presentingViewController),
    savedGames = createSavedGamesClient(),
    social = createSocialClient(presentingViewController),
)
