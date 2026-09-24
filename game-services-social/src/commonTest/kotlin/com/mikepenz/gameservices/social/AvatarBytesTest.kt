@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.social

import kotlin.test.Test
import kotlin.test.assertContentEquals

class AvatarBytesTest {
    @Test
    fun copiesBytesAtItsPublicBoundary() {
        val source = byteArrayOf(1)
        val avatar = AvatarBytes.of(source)
        source[0] = 2

        assertContentEquals(byteArrayOf(1), avatar.copyBytes())
    }
}
