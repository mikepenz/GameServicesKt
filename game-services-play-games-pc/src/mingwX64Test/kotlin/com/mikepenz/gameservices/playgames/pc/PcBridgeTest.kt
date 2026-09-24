@file:OptIn(com.mikepenz.gameservices.ExperimentalGameServicesApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)
package com.mikepenz.gameservices.playgames.pc
import kotlinx.cinterop.toKString
import platform.posix.getenv
import kotlinx.coroutines.runBlocking
import kotlin.test.*
class PcBridgeTest {
    @Test fun nativeLibraryRejectsRecallBeforeInitialization() = runBlocking {
        val path = getenv("PLAY_PC_BRIDGE")?.toKString() ?: return@runBlocking
        val backend = PlayGamesPcBackend(path)
        try {
            assertEquals(PcInitializationStatus.NotInitialized, assertIs<PcInitializationException>(backend.requestRecallAccess().exceptionOrNull()).status)
        } finally { backend.close() }
    }
}
