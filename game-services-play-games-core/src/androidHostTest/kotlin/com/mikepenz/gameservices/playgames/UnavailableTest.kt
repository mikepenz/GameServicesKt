package com.mikepenz.gameservices.playgames

import com.mikepenz.gameservices.*
import kotlin.test.*
import kotlinx.coroutines.test.runTest

class UnavailableTest {
    @Test
    fun unavailablePlayServicesReportUnsupported() = runTest {
        val services: GameServices = UnavailablePlayGamesServices
        assertFalse(services.support.isSupported)
        assertTrue(services.supportedOperations.isEmpty())
        assertIs<AuthenticationState.Unsupported>(services.authenticationState.value)
        assertIs<GameServicesException.UnsupportedTarget>(services.authenticate().exceptionOrNull())
    }
}
