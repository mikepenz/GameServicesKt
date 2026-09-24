@file:OptIn(com.mikepenz.gameservices.ExperimentalGameServicesApi::class)
package com.mikepenz.gameservices.playgames.pc
import com.mikepenz.gameservices.recall.RecallSession
import kotlin.test.*
class PcFailuresTest {
    @Test fun hostActionsAndSensitiveSessionsArePreserved() {
        assertNull(pcFailure(0, 0))
        assertEquals(PcInitializationStatus.ShutdownRequired, assertIs<PcInitializationException>(pcFailure(0, 2)).status)
        assertEquals(PcInitializationStatus.RuntimeUpdateRequired, assertIs<PcInitializationException>(pcFailure(0, 3)).status)
        assertEquals(PcInitializationStatus.RuntimeUnavailable, assertIs<PcInitializationException>(pcFailure(0, 4)).status)
        assertEquals(PcInitializationStatus.NotInitialized, assertIs<PcInitializationException>(pcFailure(0, -1)).status)
        assertFalse(RecallSession("secret-session").toString().contains("secret-session"))
        assertFailsWith<IllegalArgumentException> { RecallSession("") }
    }
}
