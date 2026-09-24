@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class GameServicesJvmTest {
    @Test
    fun `JVM factory returns a typed unsupported client`() {
        val service = createUnsupportedGameServices(GameServicesPlatform.JVM)

        assertEquals(GameServicesPlatform.JVM, service.support.target)
        assertEquals(GameServicesProvider.None, service.support.provider)
        assertFalse(service.support.isSupported)
        assertIs<GameServicesException.UnsupportedTarget>(
            runBlocking { service.authenticate().exceptionOrNull() },
        )
    }
}
