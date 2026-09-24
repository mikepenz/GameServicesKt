@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.GameKit.*

internal actual suspend fun presentAchievementsDashboard() {
    withContext(Dispatchers.Main.immediate) {
        GKAccessPoint.shared().triggerAccessPointWithState(GKGameCenterViewControllerStateAchievements) {}
    }
}
