package com.mikepenz.gameservices.savedgames

import kotlin.test.Test
import kotlin.test.assertContentEquals

class SavedGameDataTest {
    @Test
    fun copiesBytesAtItsPublicBoundary() {
        val source = byteArrayOf(1)
        val data = SavedGameData.of(source)
        source[0] = 2

        val firstCopy = data.copyBytes()
        firstCopy[0] = 3

        assertContentEquals(byteArrayOf(1), data.copyBytes())
    }
}
