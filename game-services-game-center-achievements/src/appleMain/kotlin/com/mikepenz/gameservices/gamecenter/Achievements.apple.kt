// Apple NSInteger is 32-bit on watchosArm64; conversions stay explicit.
@file:Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD") // watchOS arm64 uses 32-bit NSInteger.
@file:OptIn(kotlinx.cinterop.UnsafeNumber::class, com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.achievements.*

import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.gamecenter.gameServicesResult
import com.mikepenz.gameservices.gamecenter.toGameServicesException
import kotlin.coroutines.resume
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.GameKit.GKAchievement
import platform.GameKit.GKAchievementDescription

public fun GameCenterBackend.createAchievementsClient(
    ids: AchievementIdMappings = AchievementIdMappings.Empty,
): AchievementsClient = AppleAchievementsClient(support.target, ids)

private class AppleAchievementsClient(
    private val target: com.mikepenz.gameservices.GameServicesPlatform,
    private val ids: AchievementIdMappings,
) : AchievementsClient {
    override val isSupported: Boolean = true

    override val supportedOperations: Set<com.mikepenz.gameservices.GameServicesOperation>
        get() = gameCenterSupportedOperations(target, gameCenterOsMajor(), super.supportedOperations)

    override suspend fun loadAchievements(forceReload: Boolean): Result<List<Achievement>> = gameServicesResult {
        val (progress, descriptions) = coroutineScope {
            val progress = async { loadProgress().associateBy { it.identifier } }
            val descriptions = async { loadDescriptions() }
            progress.await() to descriptions.await()
        }
        descriptions.map { description ->
            val achievement = progress[description.identifier]
            Achievement(
                id = ids.commonId(GameServicesProvider.GameCenter, AchievementId(requireNotNull(description.identifier))),
                title = description.title.orEmpty(),
                description = if (achievement?.completed == true) {
                    description.achievedDescription.orEmpty()
                } else {
                    description.unachievedDescription.orEmpty()
                },
                points = description.maximumPoints.toLong(),
                isHidden = description.hidden,
                state = if (achievement?.completed == true) AchievementState.Unlocked else AchievementState.Locked,
                completionPercent = achievement?.percentComplete?.toInt() ?: 0,
            )
        }
    }

    override suspend fun reportProgress(
        id: AchievementId,
        progress: AchievementProgress,
    ): Result<Unit> = gameServicesResult {
        val achievement = GKAchievement(identifier = ids.providerId(GameServicesProvider.GameCenter, id).value)
        achievement.percentComplete = progress.percent().toDouble()
        report(achievement)
    }

    @Suppress("DEPRECATION_ERROR")
    override suspend fun showAchievements(): Result<Unit> = gameServicesResult {
        requireGameCenterOperation(supportedOperations, com.mikepenz.gameservices.GameServicesOperation.ShowAchievements)
        presentAchievementsDashboard()
    }
}

private suspend fun loadProgress(): List<GKAchievement> = suspendCancellableCoroutine { continuation ->
    GKAchievement.loadAchievementsWithCompletionHandler { achievements, error ->
        if (!continuation.isActive) return@loadAchievementsWithCompletionHandler
        if (error == null) continuation.resume(achievements.orEmpty().filterIsInstance<GKAchievement>())
        else continuation.resumeWith(Result.failure(error.toGameServicesException()))
    }
}

private suspend fun loadDescriptions(): List<GKAchievementDescription> = suspendCancellableCoroutine { continuation ->
    GKAchievementDescription.loadAchievementDescriptionsWithCompletionHandler { descriptions, error ->
        if (!continuation.isActive) return@loadAchievementDescriptionsWithCompletionHandler
        if (error == null) continuation.resume(descriptions.orEmpty().filterIsInstance<GKAchievementDescription>())
        else continuation.resumeWith(Result.failure(error.toGameServicesException()))
    }
}

private suspend fun report(achievement: GKAchievement): Unit = suspendCancellableCoroutine { continuation ->
    GKAchievement.reportAchievements(listOf(achievement)) { error ->
        if (!continuation.isActive) return@reportAchievements
        if (error == null) continuation.resume(Unit)
        else continuation.resumeWith(Result.failure(error.toGameServicesException()))
    }
}

internal expect suspend fun presentAchievementsDashboard()
