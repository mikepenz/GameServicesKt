package com.mikepenz.gameservices.savedgames

import com.mikepenz.gameservices.GameServicesException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SavedGamesJvmTest {
    @Test
    fun `JVM factory returns a typed unsupported client`() {
        val client = createSavedGamesClient()

        assertFalse(client.isSelectionPresenterSupported)
        assertIs<GameServicesException.UnsupportedTarget>(
            runBlocking { client.listSavedGames().exceptionOrNull() },
        )
    }
}
