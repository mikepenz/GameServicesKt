@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import platform.GameKit.GKPlayer

internal actual suspend fun presentLeaderboards(id: String?): Unit =
    throw GameServicesException.UnsupportedOperation(GameServicesProvider.GameCenter, if (id == null) GameServicesOperation.ShowLeaderboards else GameServicesOperation.ShowLeaderboard)
