package com.mikepenz.gameservices.achievements

import com.mikepenz.gameservices.GameServicesPlatform

public fun createAchievementsClient(
    ids: AchievementIdMappings = AchievementIdMappings.Empty,
): AchievementsClient =
    UnsupportedAchievementsClient(GameServicesPlatform.Wasm)
