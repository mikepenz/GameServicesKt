@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.savedgames

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

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

    @Test
    fun failedWritesNeverCommit() = runTest {
        var commits = 0
        assertFailsWith<IllegalStateException> { writeAndCommit({ false }) { commits++ } }
        assertEquals(0, commits)
        writeAndCommit({ true }) { commits++ }
        assertEquals(1, commits)
    }

    @Test
    fun validatesPortableSaveNames() {
        SavedGameId("slot-1.backup_~").requirePortableName()
        assertFailsWith<IllegalArgumentException> { SavedGameId("") }
        assertFailsWith<IllegalArgumentException> { SavedGameId("a/b").requirePortableName() }
        assertFailsWith<IllegalArgumentException> { SavedGameId("a".repeat(101)).requirePortableName() }
    }
}
