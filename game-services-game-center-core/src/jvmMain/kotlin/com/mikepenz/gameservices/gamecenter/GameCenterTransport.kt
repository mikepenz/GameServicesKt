@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)
package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@InternalGameServicesApi
public class GameCenterTransport(nativeLibrary: Path?) : AutoCloseable {
    private val lock = Any()
    private var handle: Long
    private val requests = ConcurrentHashMap<Long, CancellableContinuation<Pair<Boolean, String>>>()

    init {
        if (System.getProperty("os.name") != "Mac OS X") throw GameServicesException.ProviderFailure(GameServicesProvider.GameCenter, "macOS required")
        try { NativeBindings.load(nativeLibrary) }
        catch (error: LinkageError) {
            throw GameServicesException.ProviderFailure(GameServicesProvider.GameCenter, "NATIVE_LIBRARY_UNAVAILABLE", error)
        }
        handle = NativeBindings.create()
        check(handle != 0L) { "Could not create the native Game Center session" }
    }

    public suspend fun call(operation: String, vararg args: String): Result<List<List<String>>> = try {
        val response = suspendCancellableCoroutine<Pair<Boolean, String>> { continuation ->
            synchronized(lock) {
                if (handle == 0L) {
                    continuation.cancel(CancellationException("Game Center backend is closed"))
                    return@synchronized
                }
                val id = NativeBindings.ids.incrementAndGet()
                requests[id] = continuation
                NativeBindings.pending[id] = { ok, data ->
                    requests.remove(id)
                    if (continuation.isActive) continuation.resume(ok to data)
                }
                continuation.invokeOnCancellation { NativeBindings.pending.remove(id); requests.remove(id) }
                try { NativeBindings.request(handle, id, operation.encodeToByteArray(), encodeBridgeRows(listOf(args.toList())).encodeToByteArray()) }
                catch (error: Throwable) {
                    NativeBindings.pending.remove(id); requests.remove(id)
                    continuation.resumeWith(Result.failure(error))
                }
            }
        }
        if (response.first) Result.success(decodeBridgeRows(response.second)) else Result.failure(decodeBridgeError(response.second))
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }

    override fun close(): Unit = synchronized(lock) {
        if (handle != 0L) {
            NativeBindings.close(handle)
            handle = 0L
            requests.values.toList().forEach { it.cancel(CancellationException("Game Center backend is closed")) }
            requests.clear()
        }
    }
}

internal object NativeBindings {
    val ids = AtomicLong()
    val pending = ConcurrentHashMap<Long, (Boolean, String) -> Unit>()
    private var loaded = false

    @Synchronized fun load(path: Path?) {
        if (loaded) return
        val library = path ?: run {
            val arch = when (System.getProperty("os.arch")) {
                "aarch64", "arm64" -> "arm64"
                "x86_64", "amd64" -> "x64"
                else -> throw GameServicesException.ProviderFailure(GameServicesProvider.GameCenter, "unsupported macOS architecture")
            }
            val directory = Files.createTempDirectory("gameservices-gamecenter-")
            directory.toFile().deleteOnExit()
            for (name in listOf("libgs_gamecenter.dylib", "libgs_gamecenter_jni.dylib")) {
                val destination = directory.resolve(name)
                val resource = requireNotNull(javaClass.getResourceAsStream("/native/macos-$arch/$name")) { "Missing Game Center native library: $name" }
                resource.use { Files.copy(it, destination) }
                destination.toFile().deleteOnExit()
            }
            directory.resolve("libgs_gamecenter_jni.dylib")
        }
        System.load(library.toAbsolutePath().toString())
        loaded = true
    }

    @JvmStatic external fun create(): Long
    @JvmStatic external fun request(handle: Long, id: Long, operation: ByteArray, arguments: ByteArray)
    @JvmStatic external fun close(handle: Long)
    @JvmStatic fun complete(id: Long, ok: Boolean, data: ByteArray) { pending.remove(id)?.invoke(ok, data.decodeToString()) }
}
