package com.mikepenz.gameservices.localconsumer
import com.mikepenz.gameservices.playgames.createAchievementsClient
import com.mikepenz.gameservices.achievements.AchievementsClient
internal fun androidAchievementBackend(backend: com.mikepenz.gameservices.playgames.PlayGamesBackend): AchievementsClient = backend.createAchievementsClient()
