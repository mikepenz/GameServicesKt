@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.achievements

import com.mikepenz.gameservices.GameServicesPlatform
import com.mikepenz.gameservices.GameServicesException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs

class AchievementsJvmTest {
    @Test
    fun `JVM client returns a typed unsupported failure`() {
        assertIs<GameServicesException.UnsupportedTarget>(
            runBlocking { createUnsupportedAchievementsClient(GameServicesPlatform.JVM).loadAchievements().exceptionOrNull() },
        )
    }
}
