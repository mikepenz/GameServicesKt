@file:OptIn(InternalGameServicesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mikepenz.gameservices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class ProviderLifecycleTest {
    @Test
    fun cancelledUiWaiterCannotDeliverItsResultToTheNextRequest() = runTest {
        val ui = ProviderUiRequest<String>()
        val first = async { ui.launchAndAwait {} }
        runCurrent()
        first.cancel()
        assertFailsWith<IllegalStateException> { ui.launchAndAwait { error("Must not launch twice") } }
        ui.complete("old result")
        val second = async { ui.launchAndAwait {} }
        runCurrent()
        ui.complete("new result")
        assertEquals("new result", second.await())
        ui.close()
        assertFailsWith<IllegalStateException> { ui.launchAndAwait {} }
    }

    @Test
    fun launchFailureReleasesTheUiSlot() = runTest {
        val ui = ProviderUiRequest<Int>()
        assertFailsWith<IllegalArgumentException> { ui.launchAndAwait { throw IllegalArgumentException() } }
        assertEquals(42, ui.launchAndAwait { ui.complete(42) })
    }

    @Test
    fun nativeAuthenticationOutlivesCancelledAndConcurrentWaiters() = runTest {
        val session = AuthenticationSession()
        var installs = 0
        val first = async { session.refresh({ installs++ }, { null }) }
        val second = async { session.refresh({ installs++ }, { null }) }
        runCurrent()
        first.cancel()
        val player = PlayerIdentity(PlayerId("one"), "One")
        session.complete(Result.success(player))
        assertEquals(player, second.await().getOrThrow())
        assertEquals(1, installs)
        session.complete(Result.success(null))
        assertIs<AuthenticationState.Unauthenticated>(session.authenticationState.value)
        val changed = player.copy(id = PlayerId("two"))
        session.complete(Result.success(changed))
        assertEquals(changed, assertIs<AuthenticationState.Authenticated>(session.authenticationState.value).player)
    }
}
