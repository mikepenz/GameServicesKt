@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

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
import platform.GameKit.GKGameCenterControllerDelegateProtocol
import platform.GameKit.GKGameCenterViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject

public fun GameCenterBackend.createAchievementsClient(
    ids: AchievementIdMappings = AchievementIdMappings.Empty,
): AchievementsClient = IosAchievementsClient(presentingViewController, ids)

private class IosAchievementsClient(
    private val presentingViewController: () -> UIViewController,
    private val ids: AchievementIdMappings,
) : AchievementsClient {
    override val isSupported: Boolean = true

    private val gameCenterDelegate = AchievementsGameCenterDelegate()

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
                points = description.maximumPoints,
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
        presentAchievementsDashboard(presentingViewController, gameCenterDelegate)
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

private class AchievementsGameCenterDelegate : NSObject(), GKGameCenterControllerDelegateProtocol {
    override fun gameCenterViewControllerDidFinish(gameCenterViewController: GKGameCenterViewController) {
        gameCenterViewController.dismissViewControllerAnimated(true, null)
    }
}

internal expect suspend fun presentAchievementsDashboard(
    presentingViewController: () -> UIViewController,
    delegate: GKGameCenterControllerDelegateProtocol,
)
