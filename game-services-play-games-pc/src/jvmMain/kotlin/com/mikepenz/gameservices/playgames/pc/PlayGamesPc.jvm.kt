@file:OptIn(com.mikepenz.gameservices.ExperimentalGameServicesApi::class)
package com.mikepenz.gameservices.playgames.pc

import com.mikepenz.gameservices.*
import com.mikepenz.gameservices.recall.*
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@ExperimentalGameServicesApi
public actual class PlayGamesPcBackend public actual constructor(nativeLibrary: String) : RecallClient {
    private val operations = Mutex()
    private var handle: Long
    init {
        require(System.getProperty("os.name").startsWith("Windows") && System.getProperty("os.arch") in setOf("amd64", "x86_64")) { "Windows x64 required" }
        val library = Path.of(nativeLibrary)
        require(library.isAbsolute) { "An absolute native library path is required" }
        try { PcBindings.load(library) }
        catch (error: LinkageError) { throw GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, "PC_LIBRARY_UNAVAILABLE", error) }
        handle = PcBindings.create()
        check(handle != 0L)
    }
    public actual suspend fun initialize(): Result<Unit> = call(0).map { }
    actual override suspend fun requestRecallAccess(): Result<RecallSession> = call(1).mapCatching(::RecallSession)
    public actual suspend fun close(): Unit = withContext(NonCancellable) { operations.withLock {
        if (handle != 0L) { PcBindings.close(handle); handle = 0L }
    } }
    private suspend fun call(operation: Int): Result<String> = try {
        val result = operations.withLock {
            check(handle != 0L) { "Play PC backend is closed" }
            withContext(NonCancellable) {
                suspendCoroutine<Result<String>> { continuation ->
                    val id = PcBindings.ids.incrementAndGet()
                    PcBindings.pending[id] = { continuation.resume(it) }
                    try { PcBindings.request(handle, id, operation) }
                    catch (error: Throwable) { PcBindings.pending.remove(id); continuation.resume(Result.failure(error)) }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        result
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
}

internal object PcBindings {
    val ids = AtomicLong()
    val pending = ConcurrentHashMap<Long, (Result<String>) -> Unit>()
    private var loaded: Path? = null
    @Synchronized fun load(path: Path) {
        if (loaded != null) { require(loaded == path) { "A different Play PC bridge is already loaded" }; return }
        System.load(path.resolveSibling("play_pc_sdk.dll").toString())
        System.load(path.toString())
        loaded = path
    }
    @JvmStatic external fun create(): Long
    @JvmStatic external fun request(handle: Long, id: Long, operation: Int)
    @JvmStatic external fun close(handle: Long)
    @JvmStatic fun complete(id: Long, stage: Int, code: Int, data: ByteArray) {
        pending.remove(id)?.invoke(pcFailure(stage, code)?.let { Result.failure(it) } ?: Result.success(data.decodeToString()))
    }
}
