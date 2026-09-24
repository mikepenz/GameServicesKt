@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mikepenz.gameservices.savedgames

import com.mikepenz.gameservices.GameServicesException
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.GameKit.GKSavedGame
import platform.posix.memcpy
import kotlin.test.*

class IosSavedGamesConflictTest {
    private val id = SavedGameId("slot")

    @Test
    fun missingSingleAndMultipleVersionsMatchGameKitFetchSemantics() = runTest {
        val sdk = FakeGameKit()
        val client = sdk.client()
        assertIs<SavedGameReadResult.NotFound>(client.read(id).getOrThrow())
        sdk.games = listOf(Game("slot", byteArrayOf(0, -1)))
        assertContentEquals(byteArrayOf(0, -1), assertIs<SavedGameReadResult.Loaded>(client.read(id).getOrThrow()).version.data.copyBytes())
        sdk.games = listOf(Game("slot", byteArrayOf(1)), Game("slot", byteArrayOf(2)), Game("slot", byteArrayOf(3)), Game("other", byteArrayOf(4)))
        val conflict = assertIs<SavedGameReadResult.Conflict>(client.read(id).getOrThrow()).conflict
        assertEquals(listOf(1, 2, 3), conflict.versions.map { it.data.copyBytes().single().toInt() })
        assertEquals(listOf("slot", "other"), client.listSavedGames().getOrThrow().map { it.name })
        assertIs<SavedGameWriteResult.Conflict>(client.write(id, data(9)).getOrThrow())
        assertEquals(0, sdk.saves)
    }

    @Test
    fun eitherVersionCustomMergeAndEmptyBinaryPayloadsRoundTripExactly() = runTest {
        for (bytes in listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(1, 2), byteArrayOf(), byteArrayOf(0, -1, -128))) {
            val sdk = FakeGameKit().apply { games = listOf(Game("slot", byteArrayOf(1)), Game("slot", byteArrayOf(2))) }
            val client = sdk.client()
            val conflict = assertIs<SavedGameReadResult.Conflict>(client.read(id).getOrThrow()).conflict
            assertIs<SavedGameWriteResult.Saved>(client.resolve(conflict.id, SavedGameData.of(bytes)).getOrThrow())
            assertContentEquals(bytes, sdk.resolutionBytes)
            assertContentEquals(bytes, assertIs<SavedGameReadResult.Loaded>(sdk.client().read(id).getOrThrow()).version.data.copyBytes())
            assertTrue(client.resolve(conflict.id, data(3)).isFailure)
            assertEquals(1, sdk.resolutions)
        }
    }

    @Test
    fun anotherVersionDiscoveredWhileResolvingIsReturnedAndCanBeResolvedAgain() = runTest {
        val first = Game("slot", byteArrayOf(1))
        val second = Game("slot", byteArrayOf(2))
        val third = Game("slot", byteArrayOf(3))
        val sdk = FakeGameKit().apply {
            games = listOf(first, second)
            additionalVersion = third
        }
        val client = sdk.client()
        val conflict = assertIs<SavedGameReadResult.Conflict>(client.read(id).getOrThrow()).conflict
        val next = assertIs<SavedGameWriteResult.Conflict>(client.resolve(conflict.id, data(9)).getOrThrow()).conflict
        assertEquals(listOf(9, 3), next.versions.map { it.data.copyBytes().single().toInt() })
        assertEquals(listOf(first, second), sdk.resolvedVersions)
        assertIs<SavedGameWriteResult.Saved>(client.resolve(next.id, data(7)).getOrThrow())
        assertEquals(2, sdk.resolvedVersions.size)
        assertContentEquals(byteArrayOf(7), assertIs<SavedGameReadResult.Loaded>(client.read(id).getOrThrow()).version.data.copyBytes())
    }

    @Test
    fun failedResolutionRetainsVersionsForRetryAndMapsTheNativeError() = runTest {
        val sdk = FakeGameKit().apply { games = listOf(Game("slot", byteArrayOf(1)), Game("slot", byteArrayOf(2))) }
        val client = sdk.client()
        val conflict = assertIs<SavedGameReadResult.Conflict>(client.read(id).getOrThrow()).conflict
        sdk.resolveError = NSError.errorWithDomain("TestProvider", 17, null)
        val failure = assertIs<GameServicesException.ProviderFailure>(client.resolve(conflict.id, data(3)).exceptionOrNull())
        assertEquals("TestProvider:17", failure.code)
        sdk.resolveError = null
        assertIs<SavedGameWriteResult.Saved>(client.resolve(conflict.id, data(4)).getOrThrow())
        assertEquals(2, sdk.resolutions)
    }

    @Test
    fun incompleteResolutionResponseDoesNotLoseTheRetryHandle() = runTest {
        val sdk = FakeGameKit().apply { games = listOf(Game("slot", byteArrayOf(1)), Game("slot", byteArrayOf(2))) }
        val client = sdk.client()
        val conflict = assertIs<SavedGameReadResult.Conflict>(client.read(id).getOrThrow()).conflict
        sdk.emptyResolution = true
        assertTrue(client.resolve(conflict.id, data(3)).isFailure)
        sdk.emptyResolution = false
        assertIs<SavedGameWriteResult.Saved>(client.resolve(conflict.id, data(4)).getOrThrow())
    }

    @Test
    fun deletionInvalidatesPreviouslyReadConflictHandles() = runTest {
        val sdk = FakeGameKit().apply { games = listOf(Game("slot", byteArrayOf(1)), Game("slot", byteArrayOf(2))) }
        val client = sdk.client()
        val conflict = assertIs<SavedGameReadResult.Conflict>(client.read(id).getOrThrow()).conflict
        client.delete(id).getOrThrow()
        assertTrue(client.resolve(conflict.id, data(3)).isFailure)
        assertEquals(0, sdk.resolutions)
        assertIs<SavedGameReadResult.NotFound>(client.read(id).getOrThrow())
    }

    @Test
    fun fetchAndVersionReadFailuresDoNotExposePartialConflicts() = runTest {
        val failure = NSError.errorWithDomain("TestProvider", 18, null)
        val sdk = FakeGameKit().apply { fetchError = failure }
        val client = sdk.client()
        assertTrue(client.read(id).isFailure)
        sdk.fetchError = null
        sdk.games = listOf(Game("slot", byteArrayOf(1)), Game("slot", byteArrayOf(2), failure))
        assertTrue(client.read(id).isFailure)
        assertTrue(client.resolve(SavedGameConflictId("slot"), data(3)).isFailure)
        assertEquals(0, sdk.resolutions)
    }

    @Test
    fun cancellationIgnoresLateFetchAndLeavesClientUsable() = runTest {
        val sdk = FakeGameKit()
        var complete: ((List<*>?, NSError?) -> Unit)? = null
        val client = sdk.client(fetch = { complete = it })
        val request = async { client.read(id) }
        runCurrent()
        request.cancel()
        runCurrent()
        requireNotNull(complete)(listOf(Game("slot", byteArrayOf(1)), Game("slot", byteArrayOf(2))), null)
        request.join()
        assertTrue(request.isCancelled)
        assertTrue(client.resolve(SavedGameConflictId("slot"), data(3)).isFailure)
        val retry = async { client.read(id) }
        runCurrent()
        requireNotNull(complete)(emptyList<Any>(), null)
        assertIs<SavedGameReadResult.NotFound>(retry.await().getOrThrow())
    }

    @Test
    fun pendingResolutionSerializesReadsAndCancellationDoesNotConsumeTheConflict() = runTest {
        val sdk = FakeGameKit().apply { games = listOf(Game("slot", byteArrayOf(1)), Game("slot", byteArrayOf(2))); delayResolution = true }
        val client = sdk.client()
        val conflict = assertIs<SavedGameReadResult.Conflict>(client.read(id).getOrThrow()).conflict
        val resolve = async { client.resolve(conflict.id, data(3)) }
        runCurrent()
        val read = async { client.read(id) }
        runCurrent()
        assertFalse(read.isCompleted)
        assertEquals(1, sdk.fetches)
        resolve.cancel()
        runCurrent()
        assertTrue(resolve.isCancelled)
        assertIs<SavedGameReadResult.Conflict>(read.await().getOrThrow())
        requireNotNull(sdk.pendingResolution)(null, NSError.errorWithDomain("TestProvider", 19, null))
        sdk.delayResolution = false
        assertIs<SavedGameWriteResult.Saved>(client.resolve(conflict.id, data(4)).getOrThrow())
    }

    private fun data(vararg bytes: Byte) = SavedGameData.of(bytes)
}

/** GameKit returns a list of same-name versions, not Google's pair plus conflict token. */
private class FakeGameKit {
    var games = emptyList<Game>()
    var fetchError: NSError? = null
    var resolveError: NSError? = null
    var emptyResolution = false
    var additionalVersion: Game? = null
    var delayResolution = false
    var pendingResolution: ((List<*>?, NSError?) -> Unit)? = null
    var fetches = 0
    var saves = 0
    var resolutions = 0
    var resolvedVersions = emptyList<GKSavedGame>()
    var resolutionBytes: ByteArray? = null

    fun client(fetch: ((List<*>?, NSError?) -> Unit) -> Unit = { fetches++; it(games, fetchError) }) = IosSavedGamesClient(
        fetch = fetch,
        save = { bytes, name, complete ->
            saves++
            val game = Game(name, bytes.copyDataBytes())
            games = games.filterNot { it.name == name } + game
            complete(game, null)
        },
        resolve = { versions, bytes, complete ->
            resolutions++
            resolvedVersions = versions
            resolutionBytes = bytes.copyDataBytes()
            when {
                delayResolution -> pendingResolution = complete
                resolveError != null -> complete(null, resolveError)
                emptyResolution -> complete(emptyList<Any>(), null)
                else -> {
                    val name = requireNotNull(versions.first().name)
                    val resolved = listOf(Game(name, bytes.copyDataBytes())) + listOfNotNull(additionalVersion)
                    additionalVersion = null
                    games = games.filterNot { it.name == name } + resolved
                    complete(resolved, null)
                }
            }
        },
        delete = { name, complete -> games = games.filterNot { it.name == name }; complete(null) },
    )
}

private class Game(private val saveName: String, private val bytes: ByteArray, private val error: NSError? = null) : GKSavedGame() {
    override fun name(): String = saveName
    override fun loadDataWithCompletionHandler(handler: ((NSData?, NSError?) -> Unit)?) {
        val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        handler?.invoke(if (error == null) data else null, error)
    }
}

private fun NSData.copyDataBytes(): ByteArray = ByteArray(length.toInt()).also { result ->
    if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), bytes, length) }
}
