package com.mikepenz.gameservices.sample

import com.mikepenz.gameservices.playgames.*

import androidx.activity.ComponentActivity

public fun createGameServicesSample(activity: ComponentActivity): GameServicesSample = PlayGamesBackend (activity).let { backend -> GameServicesSample(
    services = backend,
    achievements = backend.createAchievementsClient(),
    leaderboards = backend.createLeaderboardsClient(),
    savedGames = backend.createSavedGamesClient(),
    social = backend.createSocialClient(),
) }
