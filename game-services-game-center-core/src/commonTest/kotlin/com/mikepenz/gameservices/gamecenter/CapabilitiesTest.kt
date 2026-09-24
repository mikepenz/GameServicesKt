@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)
package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*
import kotlin.test.*

class CapabilitiesTest {
    @Test fun partialTargetsAndOlderVersionsDoNotAdvertiseUnavailableUi() {
        val all = GameServicesOperation.entries.toSet()
        val watch = gameCenterSupportedOperations(GameServicesPlatform.WatchOS, 26, all)
        assertTrue(GameServicesOperation.ReportAchievement in watch)
        assertFalse(GameServicesOperation.ShowAchievements in watch)
        assertFalse(GameServicesOperation.LoadAvatar in watch)
        assertFailsWith<GameServicesException.UnsupportedOperation> { requireGameCenterOperation(watch, GameServicesOperation.ShowAchievements) }
        for ((target, minimum) in listOf(GameServicesPlatform.MacOS to 15L, GameServicesPlatform.IOS to 18L, GameServicesPlatform.TvOS to 18L)) {
            assertFalse(GameServicesOperation.ShowLeaderboard in gameCenterSupportedOperations(target, minimum - 1, all))
            assertTrue(GameServicesOperation.ShowLeaderboard in gameCenterSupportedOperations(target, minimum, all))
        }
    }
}
