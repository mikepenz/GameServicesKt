package com.mikepenz.gameservices.sample

import com.mikepenz.gameservices.GameServicesPlatform

import com.mikepenz.gameservices.createUnsupportedGameServices
import com.mikepenz.gameservices.achievements.createUnsupportedAchievementsClient
import com.mikepenz.gameservices.leaderboards.createUnsupportedLeaderboardsClient
import com.mikepenz.gameservices.savedgames.createUnsupportedSavedGamesClient
import com.mikepenz.gameservices.social.createUnsupportedSocialClient

public fun createGameServicesSample(): GameServicesSample = GameServicesSample(
    services = createUnsupportedGameServices(GameServicesPlatform.Wasm),
    achievements = createUnsupportedAchievementsClient(GameServicesPlatform.Wasm),
    leaderboards = createUnsupportedLeaderboardsClient(GameServicesPlatform.Wasm),
    savedGames = createUnsupportedSavedGamesClient(GameServicesPlatform.Wasm),
    social = createUnsupportedSocialClient(GameServicesPlatform.Wasm),
)
