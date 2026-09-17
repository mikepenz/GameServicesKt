package com.mikepenz.gameservices.achievements

import androidx.activity.ComponentActivity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.AchievementsClient as GoogleAchievementsClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.achievement.Achievement as GoogleAchievement
import com.google.android.gms.tasks.Task
import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

public fun createAchievementsClient(
    activity: ComponentActivity,
    ids: AchievementIdMappings = AchievementIdMappings.Empty,
): AchievementsClient = AndroidAchievementsClient(PlayGames.getAchievementsClient(activity), activity, ids)

private class AndroidAchievementsClient(
    private val achievements: GoogleAchievementsClient,
    private val activity: ComponentActivity,
    private val ids: AchievementIdMappings,
) : AchievementsClient {
    override val isSupported: Boolean = true

    override suspend fun loadAchievements(forceReload: Boolean): Result<List<Achievement>> = providerResult {
        val buffer = requireNotNull(achievements.load(forceReload).await().get())
        try {
            (0 until buffer.count).map { buffer.get(it).toAchievement() }
        } finally {
            buffer.release()
        }
    }

    override suspend fun reportProgress(
        id: AchievementId,
        progress: AchievementProgress,
    ): Result<Unit> = providerResult { reportConfiguredProgress(id, progress) }

    override suspend fun showAchievements(): Result<Unit> = providerResult {
        @Suppress("DEPRECATION")
        activity.startActivityForResult(achievements.achievementsIntent.await(), 0)
    }

    private suspend fun reportConfiguredProgress(id: AchievementId, progress: AchievementProgress) {
        val providerId = ids.providerId(GameServicesProvider.GooglePlayGames, id)
        val (type, totalSteps) = findAchievement(providerId)
        if (type == GoogleAchievement.TYPE_STANDARD) {
            require(progress.percent() == 100) { "Standard achievements only accept completion" }
            achievements.unlock(providerId.value)
        } else {
            val steps = progress.stepsFor(totalSteps)
            if (steps > 0) achievements.setSteps(providerId.value, steps)
        }
    }

    private suspend fun findAchievement(id: AchievementId): Pair<Int, Int> {
        val buffer = requireNotNull(achievements.load(false).await().get())
        try {
            return (0 until buffer.count)
                .asSequence()
                .map(buffer::get)
                .firstOrNull { it.achievementId == id.value }
                ?.let { it.type to it.totalSteps }
                ?: throw IllegalArgumentException("Unknown achievement ${id.value}")
        } finally {
            buffer.release()
        }
    }

    private fun GoogleAchievement.toAchievement(): Achievement = Achievement(
        id = ids.commonId(GameServicesProvider.GooglePlayGames, AchievementId(achievementId)),
        title = name,
        description = description,
        points = xpValue,
        isHidden = state == GoogleAchievement.STATE_HIDDEN,
        state = if (state == GoogleAchievement.STATE_UNLOCKED) AchievementState.Unlocked else AchievementState.Locked,
        completionPercent = if (type == GoogleAchievement.TYPE_INCREMENTAL) currentSteps * 100 / totalSteps else {
            if (state == GoogleAchievement.STATE_UNLOCKED) 100 else 0
        },
        steps = if (type == GoogleAchievement.TYPE_INCREMENTAL) AchievementSteps(currentSteps, totalSteps) else null,
    )
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWith(Result.failure(task.exception ?: IllegalStateException("Play Games task failed")))
        }
    }
}

private suspend fun <T> providerResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: GameServicesException) {
    Result.failure(exception)
} catch (exception: ApiException) {
    Result.failure(GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, exception.statusCode.toString()))
} catch (exception: Throwable) {
    Result.failure(GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, exception.javaClass.name))
}
