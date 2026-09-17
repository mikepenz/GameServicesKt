package com.mikepenz.gameservices.achievements

import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.GameKit.GKAchievement
import platform.GameKit.GKAchievementDescription
import platform.GameKit.GKGameCenterControllerDelegateProtocol
import platform.GameKit.GKGameCenterViewController
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.NSObject
import kotlin.coroutines.resume

public fun createAchievementsClient(
    presentingViewController: () -> UIViewController,
    ids: AchievementIdMappings = AchievementIdMappings.Empty,
): AchievementsClient = IosAchievementsClient(presentingViewController, ids)

private class IosAchievementsClient(
    private val presentingViewController: () -> UIViewController,
    private val ids: AchievementIdMappings,
) : AchievementsClient {
    override val isSupported: Boolean = true

    private val gameCenterDelegate = AchievementsGameCenterDelegate()

    override suspend fun loadAchievements(forceReload: Boolean): Result<List<Achievement>> = providerResult {
        val progress = loadProgress().associateBy { it.identifier }
        loadDescriptions().map { description ->
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
    ): Result<Unit> = providerResult {
        val achievement = GKAchievement(identifier = ids.providerId(GameServicesProvider.GameCenter, id).value)
        achievement.percentComplete = progress.percent().toDouble()
        report(achievement)
    }

    @Suppress("DEPRECATION_ERROR")
    override suspend fun showAchievements(): Result<Unit> = providerResult {
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

private suspend fun <T> providerResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: GameServicesException) {
    Result.failure(exception)
} catch (exception: Throwable) {
    Result.failure(GameServicesException.ProviderFailure(GameServicesProvider.GameCenter, exception.toString()))
}

private fun NSError.toGameServicesException(): GameServicesException = GameServicesException.ProviderFailure(
    provider = GameServicesProvider.GameCenter,
    code = "$domain:$code",
)

private class AchievementsGameCenterDelegate : NSObject(), GKGameCenterControllerDelegateProtocol {
    override fun gameCenterViewControllerDidFinish(gameCenterViewController: GKGameCenterViewController) {
        gameCenterViewController.dismissViewControllerAnimated(true, null)
    }
}

internal expect fun presentAchievementsDashboard(
    presentingViewController: () -> UIViewController,
    delegate: GKGameCenterControllerDelegateProtocol,
)
