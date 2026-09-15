package com.mikepenz.gameservices.sample

import androidx.activity.ComponentActivity
import com.mikepenz.gameservices.createGameServices
import com.mikepenz.gameservices.achievements.createAchievementsClient
import com.mikepenz.gameservices.leaderboards.createLeaderboardsClient
import com.mikepenz.gameservices.savedgames.createSavedGamesClient
import com.mikepenz.gameservices.social.createSocialClient

public fun createGameServicesSample(activity: ComponentActivity): GameServicesSample = GameServicesSample(
    services = createGameServices(activity),
    achievements = createAchievementsClient(activity),
    leaderboards = createLeaderboardsClient(activity),
    savedGames = createSavedGamesClient(activity),
    social = createSocialClient(activity),
)
