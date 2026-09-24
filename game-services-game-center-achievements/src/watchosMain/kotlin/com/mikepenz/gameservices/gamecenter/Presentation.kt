@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import platform.GameKit.GKPlayer

internal actual suspend fun presentAchievementsDashboard(): Unit =
    throw GameServicesException.UnsupportedOperation(GameServicesProvider.GameCenter, GameServicesOperation.ShowAchievements)
