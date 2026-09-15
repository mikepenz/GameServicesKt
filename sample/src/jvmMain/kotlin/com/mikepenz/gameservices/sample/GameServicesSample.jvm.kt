package com.mikepenz.gameservices.sample

import com.mikepenz.gameservices.createGameServices
import com.mikepenz.gameservices.achievements.createAchievementsClient
import com.mikepenz.gameservices.leaderboards.createLeaderboardsClient
import com.mikepenz.gameservices.savedgames.createSavedGamesClient
import com.mikepenz.gameservices.social.createSocialClient

public fun createGameServicesSample(): GameServicesSample = GameServicesSample(
    services = createGameServices(),
    achievements = createAchievementsClient(),
    leaderboards = createLeaderboardsClient(),
    savedGames = createSavedGamesClient(),
    social = createSocialClient(),
)
