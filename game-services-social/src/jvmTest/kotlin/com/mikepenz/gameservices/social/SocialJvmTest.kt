package com.mikepenz.gameservices.social

import com.mikepenz.gameservices.GameServicesException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs

class SocialJvmTest {
    @Test
    fun `JVM client returns a typed unsupported failure`() {
        assertIs<GameServicesException.UnsupportedTarget>(
            runBlocking { createSocialClient().loadFriends().exceptionOrNull() },
        )
    }
}
