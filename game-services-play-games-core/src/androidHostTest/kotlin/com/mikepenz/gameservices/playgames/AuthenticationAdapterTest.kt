@file:OptIn(InternalGameServicesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mikepenz.gameservices.playgames

import com.mikepenz.gameservices.*

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.games.GamesClientStatusCodes
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayersClient
import com.google.android.gms.games.Player
import com.google.android.gms.games.AuthenticationResult
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.OnCompleteListener
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.any

class AuthenticationAdapterTest {
    @Test
    fun refreshDoesNotSignInButExplicitAuthenticationWaitsAndObservesAccountChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val signIn = mock(GamesSignInClient::class.java)
            val players = mock(PlayersClient::class.java)
            val signedOut = mock(AuthenticationResult::class.java)
            val check = DeferredTask(signedOut, immediate = true)
            `when`(signIn.isAuthenticated()).thenReturn(check.task)
            val signedIn = mock(AuthenticationResult::class.java)
            `when`(signedIn.isAuthenticated).thenReturn(true)
            val login = DeferredTask(signedIn)
            `when`(signIn.signIn()).thenReturn(login.task)
            val player = mock(Player::class.java)
            `when`(player.playerId).thenReturn("one")
            `when`(player.displayName).thenReturn("One")
            val current = DeferredTask(player, immediate = true)
            `when`(players.currentPlayer).thenReturn(current.task)
            val client = AndroidGameServices(signIn, players)
            assertNull(client.refreshAuthentication().getOrThrow())
            verify(signIn, never()).signIn()
            val request = async { client.authenticate() }
            runCurrent()
            assertFalse(request.isCompleted)
            assertIs<AuthenticationState.Authenticating>(client.authenticationState.value)
            login.finish()
            assertEquals(PlayerId("one"), request.await().getOrThrow().id)
            `when`(signedOut.isAuthenticated).thenReturn(true)
            `when`(player.playerId).thenReturn("two")
            assertEquals(PlayerId("two"), client.refreshAuthentication().getOrThrow()?.id)
            `when`(signedOut.isAuthenticated).thenReturn(false)
            assertNull(client.refreshAuthentication().getOrThrow())
            assertIs<AuthenticationState.Unauthenticated>(client.authenticationState.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun cancellationClearsStateAndLateCallbackCannotReplaceTheNextAuthentication() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val signIn = mock(GamesSignInClient::class.java)
            val result = mock(AuthenticationResult::class.java)
            val pending = DeferredTask(result)
            `when`(signIn.isAuthenticated()).thenReturn(pending.task)
            val client = AndroidGameServices(signIn, mock(PlayersClient::class.java))
            val request = async { client.refreshAuthentication() }
            runCurrent()
            request.cancelAndJoin()
            assertIs<AuthenticationState.Unauthenticated>(client.authenticationState.value)
            val next = DeferredTask(result, immediate = true)
            `when`(signIn.isAuthenticated()).thenReturn(next.task)
            assertNull(client.refreshAuthentication().getOrThrow())
            pending.finish()
            assertIs<AuthenticationState.Unauthenticated>(client.authenticationState.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun authenticationFailureResetsStateAndAllowsRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val signIn = mock(GamesSignInClient::class.java)
            val task = DeferredTask(mock(AuthenticationResult::class.java))
            `when`(signIn.isAuthenticated()).thenReturn(task.task)
            val client = AndroidGameServices(signIn, mock(PlayersClient::class.java))
            val request = async { client.refreshAuthentication() }
            runCurrent()
            val error = mock(ApiException::class.java)
            `when`(error.statusCode).thenReturn(GamesClientStatusCodes.SIGN_IN_REQUIRED)
            task.finish(error)
            assertSame(GameServicesException.AuthenticationRequired, request.await().exceptionOrNull())
            assertIs<AuthenticationState.Unauthenticated>(client.authenticationState.value)
            val retry = DeferredTask(mock(AuthenticationResult::class.java), immediate = true)
            `when`(signIn.isAuthenticated()).thenReturn(retry.task)
            assertNull(client.refreshAuthentication().getOrThrow())
        } finally { Dispatchers.resetMain() }
    }

    @Test
    fun providerStatusCodesKeepTheirPublicMeaningAndUnknownFailuresKeepTheirCause() {
        val cases = mapOf(
            CommonStatusCodes.CANCELED to GameServicesException.UserCancelled,
            GamesClientStatusCodes.SIGN_IN_REQUIRED to GameServicesException.AuthenticationRequired,
            GamesClientStatusCodes.CONSENT_REQUIRED to GameServicesException.PermissionRequired,
            GamesClientStatusCodes.APP_MISCONFIGURED to GameServicesException.ConfigurationMissing,
            GamesClientStatusCodes.GAME_NOT_FOUND to GameServicesException.ConfigurationMissing,
            CommonStatusCodes.DEVELOPER_ERROR to GameServicesException.ConfigurationMissing,
        )
        for ((code, expected) in cases) {
            val error = mock(ApiException::class.java)
            `when`(error.statusCode).thenReturn(code)
            assertSame(expected, error.toGameServicesException())
        }
        val unknown = mock(ApiException::class.java)
        `when`(unknown.statusCode).thenReturn(12345)
        val mapped = assertIs<GameServicesException.ProviderFailure>(unknown.toGameServicesException())
        assertEquals("12345", mapped.code)
        assertSame(unknown, mapped.cause)
        assertSame(GameServicesException.UserCancelled, GameServicesException.UserCancelled.toGameServicesException())
    }
}

private class DeferredTask<T>(value: T, immediate: Boolean = false) {
    @Suppress("UNCHECKED_CAST")
    val task = mock(Task::class.java) as Task<T>
    private var listener: OnCompleteListener<T>? = null
    init {
        `when`(task.isSuccessful).thenReturn(true)
        `when`(task.result).thenReturn(value)
        doAnswer {
            listener = it.getArgument(0)
            if (immediate) finish()
            task
        }.`when`(task).addOnCompleteListener(any<OnCompleteListener<T>>())
    }
    fun finish(error: Exception? = null) {
        `when`(task.isSuccessful).thenReturn(error == null)
        `when`(task.exception).thenReturn(error)
        requireNotNull(listener).onComplete(task)
    }
}
