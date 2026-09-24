package com.mikepenz.gameservices.sample

import com.mikepenz.gameservices.gamecenter.*

import com.mikepenz.gameservices.GameServicesPlatform

import com.mikepenz.gameservices.createUnsupportedGameServices
import com.mikepenz.gameservices.achievements.createUnsupportedAchievementsClient
import com.mikepenz.gameservices.leaderboards.createUnsupportedLeaderboardsClient
import com.mikepenz.gameservices.savedgames.createUnsupportedSavedGamesClient
import com.mikepenz.gameservices.social.createUnsupportedSocialClient

public fun createGameServicesSample(): GameServicesSample = GameServicesSample(
    services = createUnsupportedGameServices(GameServicesPlatform.JVM),
    achievements = createUnsupportedAchievementsClient(GameServicesPlatform.JVM),
    leaderboards = createUnsupportedLeaderboardsClient(GameServicesPlatform.JVM),
    savedGames = createUnsupportedSavedGamesClient(GameServicesPlatform.JVM),
    social = createUnsupportedSocialClient(GameServicesPlatform.JVM),
)

/** The host owns and closes the explicitly selected backend. */
public fun createGameServicesSample(backend: GameCenterBackend): GameServicesSample = GameServicesSample(
    services = backend,
    achievements = backend.createAchievementsClient(),
    leaderboards = backend.createLeaderboardsClient(),
    savedGames = backend.createSavedGamesClient(),
    social = backend.createSocialClient(),
)
