@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class, com.mikepenz.gameservices.InternalGameServicesApi::class, com.mikepenz.gameservices.ExperimentalGameServicesApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
package com.mikepenz.gameservices.playgames.nativec

import cnames.structs.PgsGamesSignInClient
import cnames.structs.PgsAchievementsClient
import cnames.structs.PgsRecallClient
import com.mikepenz.gameservices.*
import com.mikepenz.gameservices.achievements.*
import com.mikepenz.gameservices.recall.*
import com.mikepenz.gameservices.playgames.nativeinterop.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import platform.posix.free

private val activeSession = AtomicBoolean(false)

/**
 * Android C SDK session. The host retains its JNI global Activity reference until [close].
 * [javaVm] is JavaVM*, and [activity] is jobject. Construct on the Activity's UI thread.
 * SDK callbacks cannot be cancelled: cancellation waits for acknowledgement before releasing handles.
 * This SDK exposes authentication status, not a player identity, so it is not a GameServices implementation.
 */
@ExperimentalGameServicesApi
public class NativePlayGamesBackend public constructor(javaVm: COpaquePointer, private val activity: COpaquePointer) : RecallClient {
    private val operations = Mutex()
    private var closed = false
    private var signIn: CPointer<PgsGamesSignInClient>? = null
    private var achievements: CPointer<PgsAchievementsClient>? = null
    private var recall: CPointer<PgsRecallClient>? = null

    init {
        check(activeSession.compareAndSet(false, true)) { "Only one native PGS session may be active" }
        try {
            check(Pgs_initialize(javaVm.reinterpret(), activity.reinterpret()) == 0) { "PGS initialization failed" }
            signIn = requireNotNull(PgsGamesSignInClient_create(activity.reinterpret()))
            achievements = requireNotNull(PgsAchievementsClient_create(activity.reinterpret()))
            recall = requireNotNull(PgsRecallClient_create(activity.reinterpret()))
        } catch (error: Exception) {
            recall?.let { PgsRecallClient_destroy(it) }
            achievements?.let { PgsAchievementsClient_destroy(it) }
            signIn?.let { PgsGamesSignInClient_destroy(it) }
            Pgs_destroy()
            activeSession.store(false)
            throw error
        }
    }

    public suspend fun signIn(): Result<Boolean> = operation {
        awaitCallback { PgsGamesSignInClient_signIn(signIn, staticCFunction(::booleanResult), it) }
    }
    public suspend fun isAuthenticated(): Result<Boolean> = operation {
        awaitCallback { PgsGamesSignInClient_isAuthenticated(signIn, staticCFunction(::booleanResult), it) }
    }
    override suspend fun requestRecallAccess(): Result<RecallSession> = operation {
        awaitCallback { PgsRecallClient_requestRecallAccess(recall, staticCFunction(::recallResult), it) }
    }

    public fun createAchievementsClient(ids: AchievementIdMappings = AchievementIdMappings.Empty): AchievementsClient = object : AchievementsClient {
        override val isSupported: Boolean = true
        override suspend fun loadAchievements(forceReload: Boolean): Result<List<Achievement>> = operation {
            load(forceReload).map { it.copy(id = ids.commonId(GameServicesProvider.GooglePlayGames, it.id)) }
        }
        override suspend fun reportProgress(id: AchievementId, progress: AchievementProgress): Result<Unit> = operation {
            val mapped = ids.providerId(GameServicesProvider.GooglePlayGames, id)
            require('\u0000' !in mapped.value)
            val achievement = requireNotNull(load(false).firstOrNull { it.id == mapped }) { "Unknown achievement" }
            val total = achievement.steps?.total
            if (total != null) {
                val steps = progress.stepsFor(total)
                if (steps > 0) awaitCallback<Boolean> { PgsAchievementsClient_setStepsImmediate(achievements, mapped.value, steps, staticCFunction(::booleanResult), it) }
            } else {
                require(progress.percent() == 100) { "Standard achievements only accept completion" }
                awaitCallback<Unit> { PgsAchievementsClient_unlockImmediate(achievements, mapped.value, staticCFunction(::unitResult), it) }
            }
        }
        override suspend fun showAchievements(): Result<Unit> = operation {
            val launched = awaitCallback<Boolean> { PgsAchievementsClient_showAchievementsUI(achievements, activity.reinterpret(), staticCFunction(::booleanResult), it) }
            if (!launched) throw GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, "UI_NOT_LAUNCHED")
        }
    }

    /** Waits for pending SDK operations before destroying their client handles. */
    public suspend fun close(): Unit = withContext(NonCancellable) { operations.withLock {
        if (!closed) {
            closed = true
            PgsRecallClient_destroy(recall)
            PgsAchievementsClient_destroy(achievements)
            PgsGamesSignInClient_destroy(signIn)
            Pgs_destroy()
            activeSession.store(false)
        }
    } }

    private suspend fun load(forceReload: Boolean): List<Achievement> = awaitCallback {
        PgsAchievementsClient_load(achievements, forceReload, staticCFunction(::achievementsResult), it)
    }

    private suspend fun <T> operation(block: suspend () -> T): Result<T> = try {
        currentCoroutineContext().ensureActive()
        val value = operations.withLock {
            check(!closed) { "Native PGS session is closed" }
            // The SDK owns pending callback contexts until completion; keep client handles alive.
            withContext(NonCancellable) { block() }
        }
        currentCoroutineContext().ensureActive()
        Result.success(value)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
}

private suspend fun <T> awaitCallback(start: (COpaquePointer) -> Unit): T = suspendCoroutine { continuation ->
    val reference = StableRef.create(continuation)
    try { start(reference.asCPointer()) }
    catch (error: Exception) { reference.dispose(); continuation.resumeWith(Result.failure(error)) }
}

private fun statusError(status: UInt): Exception? = when (status.toInt()) {
    0 -> null
    1 -> GameServicesException.AuthenticationRequired
    3 -> GameServicesException.UserCancelled
    else -> GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, "NATIVE_PGS:$status")
}
private fun <T> complete(context: COpaquePointer?, status: UInt, value: () -> T) {
    val reference = requireNotNull(context).asStableRef<Continuation<T>>()
    val continuation = reference.get()
    reference.dispose()
    continuation.resumeWith(runCatching { statusError(status)?.let { throw it }; value() })
}
private fun booleanResult(status: UInt, value: Boolean, context: COpaquePointer?) = complete(context, status) { value }
private fun unitResult(status: UInt, context: COpaquePointer?) = complete(context, status) { Unit }
private fun recallResult(status: UInt, session: CPointer<ByteVar>?, context: COpaquePointer?) {
    try { complete(context, status) { RecallSession(requireNotNull(session).toKString()) } }
    finally { free(session) }
}
// size_t is UInt on 32-bit Android and ULong on 64-bit Android.
@Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")
private fun achievementsResult(status: UInt, values: CPointer<PgsAchievement>?, count: platform.posix.size_t, context: COpaquePointer?) = complete(context, status) {
    require(count.toULong() <= Int.MAX_VALUE.toULong())
    require(count.toULong() == 0UL || values != null)
    List(count.toInt()) { index ->
        val value = requireNotNull(values)[index]
        val unlocked = value.state == PGS_ACHIEVEMENT_STATE_UNLOCKED
        val steps = if (value.type == PGS_ACHIEVEMENT_TYPE_INCREMENTAL) AchievementSteps(value.current_steps, value.total_steps) else null
        Achievement(AchievementId(requireNotNull(value.achievement_id).toKString()), value.name?.toKString().orEmpty(), value.description?.toKString().orEmpty(), value.xp_value,
            value.state == PGS_ACHIEVEMENT_STATE_HIDDEN, if (unlocked) AchievementState.Unlocked else AchievementState.Locked,
            if (unlocked) 100 else steps?.let { (it.current.toLong() * 100 / it.total).toInt() } ?: 0, steps)
    }
}
