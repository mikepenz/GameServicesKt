@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.GameKit.*

internal actual suspend fun presentLeaderboards(id: String?) {
    withContext(Dispatchers.Main.immediate) {
        if (id == null) GKAccessPoint.shared().triggerAccessPointWithState(GKGameCenterViewControllerStateLeaderboards) {}
        else GKAccessPoint.shared().triggerAccessPointWithLeaderboardID(id, GKLeaderboardPlayerScopeGlobal, GKLeaderboardTimeScopeAllTime) {}
    }
}
