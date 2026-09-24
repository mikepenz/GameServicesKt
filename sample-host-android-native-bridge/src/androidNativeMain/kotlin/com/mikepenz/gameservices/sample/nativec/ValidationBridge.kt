@file:OptIn(
    com.mikepenz.gameservices.ExperimentalGameServicesApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
)
package com.mikepenz.gameservices.sample.nativec

import com.mikepenz.gameservices.achievements.AchievementId
import com.mikepenz.gameservices.achievements.AchievementProgress
import com.mikepenz.gameservices.playgames.nativec.NativePlayGamesBackend
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.native.CName

private class Session(vm: COpaquePointer, activity: COpaquePointer) {
    val backend = NativePlayGamesBackend(vm, activity)
    val achievements = backend.createAchievementsClient()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun request(operation: Int, id: String, progress: Int): String = when (operation) {
        0 -> "Authenticated: ${backend.isAuthenticated().getOrThrow()}"
        1 -> "Signed in: ${backend.signIn().getOrThrow()}"
        2 -> achievements.loadAchievements(true).getOrThrow().joinToString("\n") {
            "${it.id.value}: ${it.title} (${it.completionPercent}%)"
        }.ifEmpty { "No achievements returned" }
        3 -> {
            achievements.reportProgress(AchievementId(id), AchievementProgress.Percent(progress)).getOrThrow()
            "Achievement progress submitted"
        }
        4 -> { achievements.showAchievements().getOrThrow(); "Achievements UI launched" }
        5 -> { backend.requestRecallAccess().getOrThrow(); "Recall session received" }
        else -> error("Unknown native operation")
    }
}

@CName("gs_validation_open")
fun open(vm: COpaquePointer, activity: COpaquePointer): COpaquePointer? =
    runCatching { StableRef.create(Session(vm, activity)).asCPointer() }.getOrNull()

@CName("gs_validation_close")
fun close(handle: COpaquePointer) {
    val ref = handle.asStableRef<Session>()
    val session = ref.get()
    runBlocking {
        session.scope.coroutineContext[Job]?.children?.toList()?.joinAll()
        session.backend.close()
    }
    ref.dispose()
}

@CName("gs_validation_request")
fun request(
    handle: COpaquePointer,
    operation: Int,
    id: CPointer<ByteVar>,
    progress: Int,
    context: COpaquePointer?,
    callback: CPointer<CFunction<(COpaquePointer?, Int, CPointer<ByteVar>?) -> Unit>>,
) {
    val session = handle.asStableRef<Session>().get()
    val achievementId = id.toKString()
    session.scope.launch {
        val result = runCatching { session.request(operation, achievementId, progress) }
        val message = result.getOrElse { it.message ?: it::class.simpleName ?: "Native operation failed" }
        memScoped { callback(context, if (result.isSuccess) 1 else 0, message.cstr.ptr) }
    }
}
