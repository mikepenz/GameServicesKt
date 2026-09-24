@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mikepenz.gameservices.playgames

import com.mikepenz.gameservices.savedgames.*

import android.app.Activity
import android.os.ParcelFileDescriptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotContents
import com.google.android.gms.games.snapshot.SnapshotMetadata
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.mikepenz.gameservices.ProviderUiRequest
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class SnapshotLifecycleTest {
    @Test
    fun readsCloseSnapshotsWithoutLoadingTheSaveCatalog() = runTest {
        val snapshot = FakeSnapshot()
        val sdk = proxy<SnapshotsClient> { method, _ -> when (method) {
            "open" -> ImmediateTask(Result.success(SnapshotsClient.DataOrConflict<Snapshot>(snapshot.value, null)))
            "discardAndClose" -> { snapshot.closed = true; ImmediateTask(Result.success(null)) }
            else -> error("Unexpected SDK call: $method")
        } }
        val client = AndroidSavedGamesClient(sdk, {}, ProviderUiRequest())
        assertIs<SavedGameReadResult.Loaded>(client.read(SavedGameId("save")).getOrThrow())
        assertTrue(snapshot.closed)
    }

    @Test
    fun failedDiskWriteClosesSnapshotWithoutCommitting() = runTest {
        val snapshot = FakeSnapshot(writes = false)
        var commits = 0
        val sdk = proxy<SnapshotsClient> { method, _ -> when (method) {
            "getMaxDataSize" -> ImmediateTask(Result.success(1024))
            "open" -> ImmediateTask(Result.success(SnapshotsClient.DataOrConflict<Snapshot>(snapshot.value, null)))
            "discardAndClose" -> { snapshot.closed = true; ImmediateTask(Result.success(null)) }
            "commitAndClose" -> { commits++; error("Must not commit failed bytes") }
            else -> error(method)
        } }
        val client = AndroidSavedGamesClient(sdk, {}, ProviderUiRequest())
        assertTrue(client.write(SavedGameId("save"), SavedGameData.of(byteArrayOf(1))).isFailure)
        assertEquals(0, commits)
        assertTrue(snapshot.closed)
    }

    @Test
    fun resolutionCanRetryAfterProviderFailureAndClosesEveryOpenedVersion() = runTest {
        val opened = mutableListOf<FakeSnapshot>()
        var resolutions = 0
        val resolutionDescriptors = mutableListOf<ParcelFileDescriptor>()
        val sdk = proxy<SnapshotsClient> { method, args -> when (method) {
            "getMaxDataSize" -> ImmediateTask(Result.success(1024))
            "open" -> {
                val first = FakeSnapshot().also(opened::add)
                val second = FakeSnapshot().also(opened::add)
                val resolution = mock(SnapshotContents::class.java)
                val descriptor = mock(ParcelFileDescriptor::class.java).also(resolutionDescriptors::add)
                `when`(resolution.parcelFileDescriptor).thenReturn(descriptor)
                `when`(resolution.writeBytes(org.mockito.ArgumentMatchers.any())).thenReturn(true)
                val conflict = SnapshotsClient.SnapshotConflict(first.value, "conflict", second.value, resolution)
                ImmediateTask(Result.success(SnapshotsClient.DataOrConflict<Snapshot>(null, conflict)))
            }
            "discardAndClose" -> {
                opened.first { it.value === args[0] }.closed = true
                ImmediateTask(Result.success(null))
            }
            "resolveConflict" -> {
                resolutions++
                if (resolutions == 1) ImmediateTask<Any>(Result.failure(IllegalStateException("Offline")))
                else ImmediateTask(Result.success(SnapshotsClient.DataOrConflict<Snapshot>(opened.last().value, null)))
            }
            else -> error(method)
        } }
        val client = AndroidSavedGamesClient(sdk, {}, ProviderUiRequest())
        val data = SavedGameData.of(byteArrayOf(1))
        val conflict = assertIs<SavedGameWriteResult.Conflict>(client.write(SavedGameId("save"), data).getOrThrow())
        assertTrue(opened.all { it.closed })
        assertTrue(client.resolve(conflict.conflict.id, data).isFailure)
        assertIs<SavedGameWriteResult.Saved>(client.resolve(conflict.conflict.id, data).getOrThrow())
        assertEquals(2, resolutions)
        resolutionDescriptors.forEach { verify(it).close() }
        assertTrue(opened.all { it.closed })
    }

    @Test
    fun snapshotArrivingAfterCancellationIsDiscarded() = runTest {
        val snapshot = FakeSnapshot()
        val task = ImmediateTask(Result.success(SnapshotsClient.DataOrConflict<Snapshot>(snapshot.value, null)), delayed = true)
        val sdk = proxy<SnapshotsClient> { method, _ -> when (method) {
            "getMaxDataSize" -> ImmediateTask(Result.success(1024))
            "open" -> task
            "discardAndClose" -> { snapshot.closed = true; ImmediateTask(Result.success(null)) }
            else -> error(method)
        } }
        val client = AndroidSavedGamesClient(sdk, {}, ProviderUiRequest())
        val request = async { client.write(SavedGameId("save"), SavedGameData.of(byteArrayOf(1))) }
        runCurrent()
        request.cancel()
        runCurrent()
        task.finish()
        assertTrue(snapshot.closed)
    }

    @Test
    fun successfulWriteWaitsForCommitAndPreservesBytes() = runTest {
        val snapshot = FakeSnapshot()
        val commit = ImmediateTask(Result.success(snapshot.metadata), delayed = true)
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val sdk = proxy<SnapshotsClient> { method, _ -> when (method) {
            "getMaxDataSize" -> ImmediateTask(Result.success(1024))
            "open" -> ImmediateTask(Result.success(SnapshotsClient.DataOrConflict<Snapshot>(snapshot.value, null)))
            "commitAndClose" -> { started.complete(Unit); commit }
            "discardAndClose" -> { snapshot.closed = true; ImmediateTask(Result.success(null)) }
            else -> error(method)
        } }
        val client = AndroidSavedGamesClient(sdk, {}, ProviderUiRequest())
        val bytes = byteArrayOf(0, -1, 42)
        val request = async { client.write(SavedGameId("save"), SavedGameData.of(bytes)) }
        started.await()
        assertContentEquals(bytes, snapshot.written)
        assertFalse(request.isCompleted)
        snapshot.closed = true // SDK commit closes the native contents.
        commit.finish()
        assertIs<SavedGameWriteResult.Saved>(request.await().getOrThrow())
    }

    @Test
    fun failedCommitReturnsFailureAndClosesSnapshot() = runTest {
        val snapshot = FakeSnapshot()
        val failure = IllegalStateException("Commit failed")
        val sdk = proxy<SnapshotsClient> { method, _ -> when (method) {
            "getMaxDataSize" -> ImmediateTask(Result.success(1024))
            "open" -> ImmediateTask(Result.success(SnapshotsClient.DataOrConflict<Snapshot>(snapshot.value, null)))
            "commitAndClose" -> ImmediateTask<SnapshotMetadata>(Result.failure(failure))
            "discardAndClose" -> { snapshot.closed = true; ImmediateTask(Result.success(null)) }
            else -> error(method)
        } }
        val client = AndroidSavedGamesClient(sdk, {}, ProviderUiRequest())
        val result = client.write(SavedGameId("save"), SavedGameData.of(byteArrayOf(1)))
        assertTrue(generateSequence(result.exceptionOrNull()?.cause) { it.cause }.any { it === failure })
        assertTrue(snapshot.closed)
    }

    @Test
    fun cancellationDuringCommitWaitsForNativeCompletion() = runTest {
        val snapshot = FakeSnapshot()
        val commit = ImmediateTask(Result.success(snapshot.metadata), delayed = true)
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        var discards = 0
        val sdk = proxy<SnapshotsClient> { method, _ -> when (method) {
            "getMaxDataSize" -> ImmediateTask(Result.success(1024))
            "open" -> ImmediateTask(Result.success(SnapshotsClient.DataOrConflict<Snapshot>(snapshot.value, null)))
            "commitAndClose" -> { started.complete(Unit); commit }
            "discardAndClose" -> { discards++; snapshot.closed = true; ImmediateTask(Result.success(null)) }
            else -> error(method)
        } }
        val client = AndroidSavedGamesClient(sdk, {}, ProviderUiRequest())
        val request = async { client.write(SavedGameId("save"), SavedGameData.of(byteArrayOf(1))) }
        started.await()
        request.cancel()
        runCurrent()
        assertFalse(request.isCompleted)
        assertEquals(0, discards)
        snapshot.closed = true
        commit.finish()
        request.join()
        assertTrue(request.isCancelled)
        assertEquals(0, discards)
    }

    @Test
    fun cancelledConflictOpenClosesSeparateResolutionContents() = runTest {
        val first = FakeSnapshot()
        val second = FakeSnapshot()
        val descriptor = mock(ParcelFileDescriptor::class.java)
        val contents = mock(SnapshotContents::class.java)
        `when`(contents.parcelFileDescriptor).thenReturn(descriptor)
        val conflict = SnapshotsClient.SnapshotConflict(first.value, "conflict", second.value, contents)
        val task = ImmediateTask(Result.success(SnapshotsClient.DataOrConflict<Snapshot>(null, conflict)), delayed = true)
        val sdk = proxy<SnapshotsClient> { method, args -> when (method) {
            "open" -> task
            "discardAndClose" -> {
                (if (args[0] === first.value) first else second).closed = true
                ImmediateTask(Result.success(null))
            }
            else -> error(method)
        } }
        val client = AndroidSavedGamesClient(sdk, {}, ProviderUiRequest())
        val request = async { client.read(SavedGameId("save")) }
        runCurrent()
        request.cancel()
        runCurrent()
        task.finish()
        assertTrue(first.closed && second.closed)
        verify(descriptor).close()
    }

    @Test
    fun eitherVersionMergedAndEmptyBinaryPayloadsResolveAndReadBackExactly() = runTest {
        for (bytes in listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(1, 2), byteArrayOf(), byteArrayOf(0, -1, -128))) {
            val sdk = ConflictFixture()
            val client = sdk.client()
            val conflict = assertIs<SavedGameReadResult.Conflict>(client.read(SavedGameId("save")).getOrThrow()).conflict
            assertEquals(listOf(1, 2), conflict.versions.map { it.data.copyBytes().single().toInt() })
            assertIs<SavedGameWriteResult.Saved>(client.resolve(conflict.id, SavedGameData.of(bytes)).getOrThrow())
            assertContentEquals(bytes, sdk.resolutionBytes)
            val loaded = assertIs<SavedGameReadResult.Loaded>(sdk.client().read(SavedGameId("save")).getOrThrow())
            assertContentEquals(bytes, loaded.version.data.copyBytes())
            assertTrue(client.resolve(conflict.id, SavedGameData.of(bytes)).isFailure)
            assertEquals(1, sdk.resolutions)
            sdk.assertClosed()
        }
    }

    @Test
    fun conflictChangingBetweenReadAndResolveReturnsNewVersionsWithoutOverwriting() = runTest {
        val sdk = ConflictFixture()
        val client = sdk.client()
        val old = assertIs<SavedGameReadResult.Conflict>(client.read(SavedGameId("save")).getOrThrow()).conflict
        sdk.conflictId = "conflict-2"
        sdk.versions = listOf(byteArrayOf(3), byteArrayOf(4))
        val next = assertIs<SavedGameWriteResult.Conflict>(client.resolve(old.id, SavedGameData.of(byteArrayOf(9))).getOrThrow()).conflict
        assertEquals(SavedGameConflictId("conflict-2"), next.id)
        assertEquals(listOf(3, 4), next.versions.map { it.data.copyBytes().single().toInt() })
        assertEquals(0, sdk.resolutions)
        assertTrue(client.resolve(old.id, SavedGameData.of(byteArrayOf(9))).isFailure)
        assertIs<SavedGameWriteResult.Saved>(client.resolve(next.id, SavedGameData.of(byteArrayOf(3, 4))).getOrThrow())
        sdk.assertClosed()
    }

    @Test
    fun conflictAlreadyResolvedByAnotherDeviceDoesNotOverwriteItsSave() = runTest {
        val sdk = ConflictFixture()
        val client = sdk.client()
        val conflict = assertIs<SavedGameReadResult.Conflict>(client.read(SavedGameId("save")).getOrThrow()).conflict
        sdk.conflictId = null
        sdk.versions = listOf(byteArrayOf(7))
        assertTrue(client.resolve(conflict.id, SavedGameData.of(byteArrayOf(9))).isFailure)
        assertEquals(0, sdk.resolutions)
        assertContentEquals(byteArrayOf(7), assertIs<SavedGameReadResult.Loaded>(client.read(SavedGameId("save")).getOrThrow()).version.data.copyBytes())
        sdk.assertClosed()
    }

    @Test
    fun providerCanReturnAnotherConflictDuringResolution() = runTest {
        val sdk = ConflictFixture().apply { nextConflictId = "conflict-2" }
        val client = sdk.client()
        val initial = assertIs<SavedGameWriteResult.Conflict>(client.write(SavedGameId("save"), SavedGameData.of(byteArrayOf(8))).getOrThrow()).conflict
        val next = assertIs<SavedGameWriteResult.Conflict>(client.resolve(initial.id, SavedGameData.of(byteArrayOf(9))).getOrThrow()).conflict
        assertEquals(SavedGameConflictId("conflict-2"), next.id)
        assertEquals(listOf(9, 3), next.versions.map { it.data.copyBytes().single().toInt() })
        assertTrue(client.resolve(initial.id, SavedGameData.of(byteArrayOf(0))).isFailure)
        assertIs<SavedGameWriteResult.Saved>(client.resolve(next.id, SavedGameData.of(byteArrayOf(9, 3))).getOrThrow())
        assertEquals(2, sdk.resolutions)
        sdk.assertClosed()
    }

    @Test
    fun failedResolutionDiskWriteAndOversizeDataNeverReachTheProvider() = runTest {
        val sdk = ConflictFixture()
        val client = sdk.client()
        val conflict = assertIs<SavedGameReadResult.Conflict>(client.read(SavedGameId("save")).getOrThrow()).conflict
        assertTrue(client.resolve(conflict.id, SavedGameData.of(ByteArray(5))).isFailure)
        sdk.resolutionWriteSucceeds = false
        assertTrue(client.resolve(conflict.id, SavedGameData.of(byteArrayOf(3))).isFailure)
        assertEquals(0, sdk.resolutions)
        sdk.resolutionWriteSucceeds = true
        assertIs<SavedGameWriteResult.Saved>(client.resolve(conflict.id, SavedGameData.of(byteArrayOf(3))).getOrThrow())
        sdk.assertClosed()
    }

    @Test
    fun readingOneBrokenVersionClosesEverythingAndDoesNotRegisterPartialConflict() = runTest {
        val sdk = ConflictFixture().apply { failSecondRead = true }
        val client = sdk.client()
        assertTrue(client.read(SavedGameId("save")).isFailure)
        assertTrue(client.resolve(SavedGameConflictId("conflict-1"), SavedGameData.of(byteArrayOf(3))).isFailure)
        assertEquals(0, sdk.resolutions)
        sdk.assertClosed()
    }

    @Test
    fun unresolvedConflictCannotBeDeletedAndResolvedHandlesCannotBeReused() = runTest {
        val sdk = ConflictFixture()
        val client = sdk.client()
        val conflict = assertIs<SavedGameReadResult.Conflict>(client.read(SavedGameId("save")).getOrThrow()).conflict
        assertTrue(client.delete(SavedGameId("save")).isFailure)
        assertEquals(0, sdk.deletes)
        client.resolve(conflict.id, SavedGameData.of(byteArrayOf(3))).getOrThrow()
        client.delete(SavedGameId("save")).getOrThrow()
        assertTrue(client.resolve(conflict.id, SavedGameData.of(byteArrayOf(4))).isFailure)
        assertEquals(1, sdk.deletes)
        sdk.assertClosed()
    }

    @Test
    fun cancellationDuringResolutionWaitsForCompletionBeforeAllowingAnotherRead() = runTest {
        val sdk = ConflictFixture().apply { delayResolution = true }
        val client = sdk.client()
        val conflict = assertIs<SavedGameReadResult.Conflict>(client.read(SavedGameId("save")).getOrThrow()).conflict
        val request = async { client.resolve(conflict.id, SavedGameData.of(byteArrayOf(3))) }
        // The resolution writes on Dispatchers.IO; wait for its actual SDK invocation.
        sdk.resolutionStarted.await()
        val read = async { client.read(SavedGameId("save")) }
        runCurrent()
        request.cancel()
        runCurrent()
        assertFalse(request.isCompleted)
        assertFalse(read.isCompleted)
        requireNotNull(sdk.pendingResolution).finish()
        request.join()
        assertTrue(request.isCancelled)
        assertContentEquals(byteArrayOf(3), assertIs<SavedGameReadResult.Loaded>(read.await().getOrThrow()).version.data.copyBytes())
        sdk.assertClosed()
    }

    /** Google exposes a pair, a changing token, and separate writable resolution contents. */
    private class ConflictFixture {
        var versions = listOf(byteArrayOf(1), byteArrayOf(2))
        var conflictId: String? = "conflict-1"
        var nextConflictId: String? = null
        var resolutionWriteSucceeds = true
        var failSecondRead = false
        var delayResolution = false
        var resolutions = 0
        var deletes = 0
        var resolutionBytes: ByteArray? = null
        var pendingResolution: ImmediateTask<SnapshotsClient.DataOrConflict<Snapshot>>? = null
        val resolutionStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        private val opened = mutableListOf<FakeSnapshot>()
        private val descriptors = mutableListOf<ParcelFileDescriptor>()
        private val sdk = proxy<SnapshotsClient> { method, args -> when (method) {
            "getMaxDataSize" -> ImmediateTask(Result.success(4))
            "open" -> {
                assertEquals(SnapshotsClient.RESOLUTION_POLICY_MANUAL, args[2])
                ImmediateTask(Result.success(open()))
            }
            "discardAndClose" -> {
                opened.first { it.value === args[0] }.closed = true
                ImmediateTask(Result.success(null))
            }
            "resolveConflict" -> {
                assertEquals(conflictId, args[0])
                assertEquals("save", args[1])
                resolutions++
                val bytes = requireNotNull(resolutionBytes).copyOf()
                conflictId = nextConflictId
                nextConflictId = null
                versions = if (conflictId == null) listOf(bytes) else listOf(bytes, byteArrayOf(3))
                ImmediateTask(Result.success(open()), delayResolution).also {
                    pendingResolution = it
                    resolutionStarted.complete(Unit)
                }
            }
            "delete" -> { deletes++; ImmediateTask(Result.success("save")) }
            else -> error("Unexpected SDK call: $method")
        } }

        fun client() = AndroidSavedGamesClient(sdk, {}, ProviderUiRequest())

        private fun open(): SnapshotsClient.DataOrConflict<Snapshot> {
            val snapshots = versions.mapIndexed { index, bytes ->
                FakeSnapshot(bytes = bytes, failRead = failSecondRead && index == 1).also(opened::add)
            }
            val token = conflictId ?: return SnapshotsClient.DataOrConflict(snapshots.single().value, null)
            val descriptor = mock(ParcelFileDescriptor::class.java).also(descriptors::add)
            val contents = mock(SnapshotContents::class.java)
            `when`(contents.parcelFileDescriptor).thenReturn(descriptor)
            `when`(contents.writeBytes(org.mockito.ArgumentMatchers.any())).thenAnswer {
                resolutionBytes = (it.arguments[0] as ByteArray).copyOf()
                resolutionWriteSucceeds
            }
            return SnapshotsClient.DataOrConflict(null, SnapshotsClient.SnapshotConflict(snapshots[0].value, token, snapshots[1].value, contents))
        }

        fun assertClosed() {
            assertTrue(opened.all { it.closed })
            descriptors.forEach { verify(it).close() }
        }
    }

    private class FakeSnapshot(val writes: Boolean = true, val bytes: ByteArray = byteArrayOf(1), val failRead: Boolean = false) {
        var closed = false
        var written: ByteArray? = null
        val metadata = proxy<SnapshotMetadata> { method, _ -> when (method) {
            "getUniqueName", "getSnapshotId" -> "save"
            else -> error(method)
        } }
        val contents = proxy<SnapshotContents> { method, args -> when (method) {
            "isClosed" -> closed
            "writeBytes" -> { written = (args[0] as ByteArray).copyOf(); writes }
            "readFully" -> { check(!failRead) { "Read failed" }; bytes.copyOf() }
            else -> error(method)
        } }
        val value = proxy<Snapshot> { method, _ -> when (method) {
            "getMetadata" -> metadata
            "getSnapshotContents" -> if (closed) null else contents
            else -> error(method)
        } }
    }
}

private inline fun <reified T> proxy(crossinline call: (String, Array<out Any?>) -> Any?): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
        call(method.name, args.orEmpty())
    } as T

/** Only the completion-listener API used by the adapter; no Android Looper or network. */
private class ImmediateTask<T>(private val outcome: Result<T>, private val delayed: Boolean = false) : Task<T>() {
    private var listener: OnCompleteListener<T>? = null
    fun finish() { listener?.onComplete(this) }
    override fun isComplete() = !delayed
    override fun isSuccessful() = outcome.isSuccess
    override fun isCanceled() = false
    override fun getResult(): T = outcome.getOrThrow()
    override fun <X : Throwable> getResult(type: Class<X>): T = result
    override fun getException(): Exception? = outcome.exceptionOrNull() as? Exception
    override fun addOnCompleteListener(listener: OnCompleteListener<T>): Task<T> = apply {
        this.listener = listener
        if (!delayed) finish()
    }
    override fun addOnSuccessListener(listener: OnSuccessListener<in T>): Task<T> = error("unused")
    override fun addOnSuccessListener(executor: Executor, listener: OnSuccessListener<in T>): Task<T> = error("unused")
    override fun addOnSuccessListener(activity: Activity, listener: OnSuccessListener<in T>): Task<T> = error("unused")
    override fun addOnFailureListener(listener: OnFailureListener): Task<T> = error("unused")
    override fun addOnFailureListener(executor: Executor, listener: OnFailureListener): Task<T> = error("unused")
    override fun addOnFailureListener(activity: Activity, listener: OnFailureListener): Task<T> = error("unused")
}
