package com.mikepenz.gameservices.achievements

import com.mikepenz.gameservices.GameServicesPlatform

public fun createAchievementsClient(): AchievementsClient =
    UnsupportedAchievementsClient(GameServicesPlatform.JVM)
