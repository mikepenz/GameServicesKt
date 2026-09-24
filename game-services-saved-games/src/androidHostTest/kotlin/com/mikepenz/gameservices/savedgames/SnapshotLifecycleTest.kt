@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mikepenz.gameservices.savedgames

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

    private class FakeSnapshot(val writes: Boolean = true) {
        var closed = false
        var written: ByteArray? = null
        val metadata = proxy<SnapshotMetadata> { method, _ -> when (method) {
            "getUniqueName", "getSnapshotId" -> "save"
            else -> error(method)
        } }
        val contents = proxy<SnapshotContents> { method, args -> when (method) {
            "isClosed" -> closed
            "writeBytes" -> { written = (args[0] as ByteArray).copyOf(); writes }
            "readFully" -> byteArrayOf(1)
            else -> error(method)
        } }
        val value = proxy<Snapshot> { method, _ -> when (method) {
            "getMetadata" -> metadata
            "getSnapshotContents" -> contents
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
