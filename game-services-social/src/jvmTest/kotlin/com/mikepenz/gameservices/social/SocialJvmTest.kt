@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.social

import com.mikepenz.gameservices.GameServicesPlatform
import com.mikepenz.gameservices.GameServicesException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

class SocialJvmTest {
    @Test
    fun `JVM client returns a typed unsupported failure`() {
        val client = createUnsupportedSocialClient(GameServicesPlatform.JVM)

        assertEquals(FriendsAccessState.Unknown, client.friendsAccessState.value)
        assertIs<GameServicesException.UnsupportedTarget>(
            runBlocking { client.loadFriends().exceptionOrNull() },
        )
    }
}
