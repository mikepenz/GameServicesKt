package com.mikepenz.gameservices.leaderboards

import com.mikepenz.gameservices.GameServicesException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs

class LeaderboardsJvmTest {
    @Test
    fun `JVM client returns a typed unsupported failure`() {
        assertIs<GameServicesException.UnsupportedTarget>(
            runBlocking { createLeaderboardsClient().loadLeaderboards().exceptionOrNull() },
        )
    }
}
