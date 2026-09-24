@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.savedgames

import com.mikepenz.gameservices.GameServicesPlatform
import com.mikepenz.gameservices.GameServicesException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SavedGamesJvmTest {
    @Test
    fun `JVM factory returns a typed unsupported client`() {
        val client = createUnsupportedSavedGamesClient(GameServicesPlatform.JVM)

        assertFalse(client.isSelectionPresenterSupported)
        assertIs<GameServicesException.UnsupportedTarget>(
            runBlocking { client.listSavedGames().exceptionOrNull() },
        )
    }
}
