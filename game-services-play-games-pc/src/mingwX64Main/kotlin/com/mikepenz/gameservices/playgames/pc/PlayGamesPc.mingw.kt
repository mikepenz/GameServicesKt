@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, com.mikepenz.gameservices.ExperimentalGameServicesApi::class)
package com.mikepenz.gameservices.playgames.pc

import com.mikepenz.gameservices.*
import com.mikepenz.gameservices.recall.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import platform.windows.*

@ExperimentalGameServicesApi
public actual class PlayGamesPcBackend public actual constructor(nativeLibrary: String) : RecallClient {
    private val operations = Mutex()
    private val library: HMODULE
    private val request: CPointer<CFunction<(COpaquePointer?, Int, COpaquePointer?, CPointer<CFunction<(COpaquePointer?, Int, Int, CPointer<ByteVar>?) -> Unit>>?) -> Unit>>
    private val destroy: CPointer<CFunction<(COpaquePointer?) -> Unit>>
    private var handle: COpaquePointer?
    init {
        require('\u0000' !in nativeLibrary && (nativeLibrary.startsWith("\\\\") || nativeLibrary.length > 2 && nativeLibrary[1] == ':' && nativeLibrary[2] in "\\/")) { "An absolute DLL path is required" }
        library = requireNotNull(LoadLibraryExW(nativeLibrary, null, 0x00000100u or 0x00001000u)) { "Play PC bridge could not load: ${GetLastError()}" }
        try {
            val create = requireNotNull(GetProcAddress(library, "gs_pc_create")).reinterpret<CFunction<() -> COpaquePointer?>>()
            request = requireNotNull(GetProcAddress(library, "gs_pc_request")).reinterpret()
            destroy = requireNotNull(GetProcAddress(library, "gs_pc_close")).reinterpret()
            handle = requireNotNull(create())
        } catch (error: Exception) { FreeLibrary(library); throw error }
    }
    public actual suspend fun initialize(): Result<Unit> = call(0).map { }
    actual override suspend fun requestRecallAccess(): Result<RecallSession> = call(1).mapCatching(::RecallSession)
    public actual suspend fun close(): Unit = withContext(NonCancellable) { operations.withLock {
        // Keep the DLL resident: an SDK callback may still be returning through its code.
        handle?.let { destroy(it); handle = null }
    } }
    private suspend fun call(operation: Int): Result<String> = try {
        val result = operations.withLock {
            val session = checkNotNull(handle) { "Play PC backend is closed" }
            withContext(NonCancellable) {
                suspendCoroutine<Result<String>> { continuation ->
                    request(session, operation, StableRef.create(continuation).asCPointer(), staticCFunction(::complete))
                }
            }
        }
        currentCoroutineContext().ensureActive()
        result
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
}
private fun complete(context: COpaquePointer?, stage: Int, code: Int, value: CPointer<ByteVar>?) {
    val ref = requireNotNull(context).asStableRef<Continuation<Result<String>>>()
    val continuation = ref.get()
    ref.dispose()
    continuation.resume(pcFailure(stage, code)?.let { Result.failure(it) } ?: runCatching { requireNotNull(value).toKString() })
}
