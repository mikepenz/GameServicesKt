package com.mikepenz.gameservices

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameServicesContractTest {
    @Test
    fun `service exposes support state and authenticated player`() {
        val player = PlayerIdentity(PlayerId("player-1"), "Player")
        val service = TestGameServices(player)

        assertEquals(GameServicesPlatform.Android, service.support.target)
        assertEquals(GameServicesProvider.GooglePlayGames, service.support.provider)
        assertTrue(service.support.isSupported)
        assertIs<AuthenticationState.Authenticated>(service.authenticationState.value)
        runTest { assertEquals(player, service.refreshAuthentication().getOrThrow()) }
        runTest { assertEquals(player, service.authenticate().getOrThrow()) }
    }

    @Test
    fun `unsupported service retains its target in the typed failure`() {
        val service = UnsupportedGameServices(GameServicesPlatform.Wasm)

        runTest {
            val refreshFailure = assertIs<GameServicesException.UnsupportedTarget>(service.refreshAuthentication().exceptionOrNull())
            assertEquals(GameServicesPlatform.Wasm, refreshFailure.target)
            val unsupported = assertIs<GameServicesException.UnsupportedTarget>(service.authenticate().exceptionOrNull())
            assertEquals(GameServicesPlatform.Wasm, unsupported.target)
        }
    }

    private class TestGameServices(
        private val player: PlayerIdentity,
    ) : GameServices {
        override val support = GameServicesSupport(
            target = GameServicesPlatform.Android,
            provider = GameServicesProvider.GooglePlayGames,
            isSupported = true,
        )
        override val authenticationState = MutableStateFlow<AuthenticationState>(
            AuthenticationState.Authenticated(player),
        )

        override suspend fun refreshAuthentication(): Result<PlayerIdentity?> = Result.success(player)

        override suspend fun authenticate(): Result<PlayerIdentity> = Result.success(player)
    }
}
