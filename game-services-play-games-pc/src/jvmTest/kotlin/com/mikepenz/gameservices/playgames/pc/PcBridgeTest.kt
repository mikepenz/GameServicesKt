@file:OptIn(com.mikepenz.gameservices.ExperimentalGameServicesApi::class)
package com.mikepenz.gameservices.playgames.pc
import kotlinx.coroutines.runBlocking
import kotlin.test.*
class PcBridgeTest {
    @Test fun nativeLibraryRejectsRecallBeforeInitialization() = runBlocking {
        val path = System.getenv("PLAY_PC_BRIDGE") ?: return@runBlocking
        val backend = PlayGamesPcBackend(path)
        try {
            assertEquals(PcInitializationStatus.NotInitialized, assertIs<PcInitializationException>(backend.requestRecallAccess().exceptionOrNull()).status)
        } finally { backend.close() }
    }
}
