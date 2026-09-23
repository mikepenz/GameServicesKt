@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.achievements

import androidx.activity.ComponentActivity
import com.google.android.gms.games.AchievementsClient as GoogleAchievementsClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.achievement.Achievement as GoogleAchievement
import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.awaitGameServices
import com.mikepenz.gameservices.gameServicesResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    private val metadata = mutableMapOf<String, Int?>()
    private val metadataLock = Mutex()

    override suspend fun loadAchievements(forceReload: Boolean): Result<List<Achievement>> = gameServicesResult {
        metadataLock.withLock {
            val buffer = requireNotNull(achievements.load(forceReload).awaitGameServices { it.get()?.release() }.get())
            try {
                metadata.clear()
                (0 until buffer.count).map { index ->
                    val achievement = buffer.get(index)
                    metadata[achievement.achievementId] = achievement.configuredSteps()
                    achievement.toAchievement()
                }
            } finally {
                buffer.release()
            }
        }
    }

    override suspend fun reportProgress(
        id: AchievementId,
        progress: AchievementProgress,
    ): Result<Unit> = gameServicesResult { reportConfiguredProgress(id, progress) }

    override suspend fun showAchievements(): Result<Unit> = gameServicesResult {
        @Suppress("DEPRECATION")
        withContext(Dispatchers.Main.immediate) {
            activity.startActivityForResult(achievements.achievementsIntent.awaitGameServices(), 0)
        }
    }

    private suspend fun reportConfiguredProgress(id: AchievementId, progress: AchievementProgress) {
        val providerId = ids.providerId(GameServicesProvider.GooglePlayGames, id)
        val totalSteps = findAchievement(providerId)
        if (totalSteps == null) {
            require(progress.percent() == 100) { "Standard achievements only accept completion" }
            achievements.unlockImmediate(providerId.value).awaitGameServices()
        } else {
            val steps = progress.stepsFor(totalSteps)
            if (steps > 0) achievements.setStepsImmediate(providerId.value, steps).awaitGameServices()
        }
    }

    private suspend fun findAchievement(id: AchievementId): Int? = metadataLock.withLock {
        if (!metadata.containsKey(id.value)) {
            val buffer = requireNotNull(achievements.load(false).awaitGameServices { it.get()?.release() }.get())
            try {
                metadata.clear()
                for (index in 0 until buffer.count) {
                    val achievement = buffer.get(index)
                    metadata[achievement.achievementId] = achievement.configuredSteps()
                }
            } finally {
                buffer.release()
            }
        }
        require(metadata.containsKey(id.value)) { "Unknown achievement ${id.value}" }
        metadata[id.value]
    }

    private fun GoogleAchievement.configuredSteps(): Int? = configuredSteps(type == GoogleAchievement.TYPE_INCREMENTAL) { totalSteps }

    private fun GoogleAchievement.toAchievement(): Achievement = Achievement(
        id = ids.commonId(GameServicesProvider.GooglePlayGames, AchievementId(achievementId)),
        title = name,
        description = description,
        points = xpValue,
        isHidden = state == GoogleAchievement.STATE_HIDDEN,
        state = if (state == GoogleAchievement.STATE_UNLOCKED) AchievementState.Unlocked else AchievementState.Locked,
        completionPercent = if (type == GoogleAchievement.TYPE_INCREMENTAL) (currentSteps.toLong() * 100 / totalSteps).toInt() else {
            if (state == GoogleAchievement.STATE_UNLOCKED) 100 else 0
        },
        steps = if (type == GoogleAchievement.TYPE_INCREMENTAL) AchievementSteps(currentSteps, totalSteps) else null,
    )
}
