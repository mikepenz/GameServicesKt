@file:OptIn(InternalGameServicesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import platform.Foundation.NSError
import platform.GameKit.*
import platform.UIKit.UIViewController

class AuthenticationAdapterTest {
    @Test
    fun handlerPresentsOnceSurvivesCancellationAndObservesAccountChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var callback: ((UIViewController?, NSError?) -> Unit)? = null
            var player: PlayerIdentity? = null
            var installs = 0
            val presentations = mutableListOf<UIViewController>()
            val client = IosGameServices(presentations::add, { installs++; callback = it }, { player }, { it() })
            val first = async { client.refreshAuthentication() }
            val second = async { client.authenticate() }
            runCurrent()
            assertEquals(1, installs)
            val controller = UIViewController()
            requireNotNull(callback)(controller, null)
            assertEquals(listOf(controller), presentations)
            assertFalse(second.isCompleted)
            first.cancelAndJoin()
            player = PlayerIdentity(PlayerId("one"), "One")
            requireNotNull(callback)(null, null)
            assertEquals(player, second.await().getOrThrow())
            player = null
            requireNotNull(callback)(null, null)
            assertIs<AuthenticationState.Unauthenticated>(client.authenticationState.value)
            player = PlayerIdentity(PlayerId("two"), "Two")
            requireNotNull(callback)(null, null)
            assertEquals(player, assertIs<AuthenticationState.Authenticated>(client.authenticationState.value).player)
            assertEquals(player, client.refreshAuthentication().getOrThrow())
            assertEquals(1, installs)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun cancelledProviderLoginReturnsTypedFailure() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val client = IosGameServices({ error("Unexpected UI") }, {
                it(null, NSError.errorWithDomain(GKErrorDomain, GKErrorCancelled, null))
            }, { null }, { it() })
            assertSame(GameServicesException.UserCancelled, client.authenticate().exceptionOrNull())
            assertIs<AuthenticationState.Unauthenticated>(client.authenticationState.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun gameKitErrorsAreMappedOnlyWithinTheirDomain() {
        val cases = mapOf(
            GKErrorCancelled to GameServicesException.UserCancelled,
            GKErrorNotAuthenticated to GameServicesException.AuthenticationRequired,
            GKErrorNotAuthorized to GameServicesException.PermissionRequired,
            GKErrorGameUnrecognized to GameServicesException.ConfigurationMissing,
        )
        for ((code, expected) in cases) {
            assertSame(expected, NSError.errorWithDomain(GKErrorDomain, code, null).toGameServicesException())
            val unrelated = NSError.errorWithDomain("OtherDomain", code, null).toGameServicesException()
            assertEquals("OtherDomain:$code", assertIs<GameServicesException.ProviderFailure>(unrelated).code)
        }
    }
}
