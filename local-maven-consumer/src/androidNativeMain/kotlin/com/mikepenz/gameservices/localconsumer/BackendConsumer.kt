@file:OptIn(com.mikepenz.gameservices.ExperimentalGameServicesApi::class)
package com.mikepenz.gameservices.localconsumer
import com.mikepenz.gameservices.playgames.nativec.NativePlayGamesBackend
import com.mikepenz.gameservices.achievements.AchievementsClient
internal fun nativeAchievementBackend(backend: NativePlayGamesBackend): AchievementsClient = backend.createAchievementsClient()
