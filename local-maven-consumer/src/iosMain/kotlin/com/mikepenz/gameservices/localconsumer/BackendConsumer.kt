package com.mikepenz.gameservices.localconsumer
import com.mikepenz.gameservices.gamecenter.createAchievementsClient
import com.mikepenz.gameservices.achievements.AchievementsClient
internal fun appleAchievementBackend(backend: com.mikepenz.gameservices.gamecenter.GameCenterBackend): AchievementsClient = backend.createAchievementsClient()
