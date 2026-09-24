@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)
package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*
import kotlin.test.*

class BridgeCodecTest {
    @Test fun preservesUnicodeEmptyStringsAndControlCharacters() {
        val rows = listOf(emptyList(), listOf("", "玩家 🎮", "a\u0000b\n\"\\"))
        assertEquals(rows, decodeBridgeRows(encodeBridgeRows(rows)))
        assertFails { decodeBridgeRows("[[1]]") }
        assertFails { decodeBridgeRows("[null]") }
    }
    @Test fun preservesTypedFailures() {
        val unsupported = GameServicesException.UnsupportedOperation(GameServicesProvider.GameCenter, GameServicesOperation.SelectSavedGame)
        val decoded = assertIs<GameServicesException.UnsupportedOperation>(decodeBridgeError(bridgeError(unsupported)))
        assertEquals(unsupported.operation, decoded.operation)
        assertSame(GameServicesException.AuthenticationRequired, decodeBridgeError(bridgeError(GameServicesException.AuthenticationRequired)))
    }
}
