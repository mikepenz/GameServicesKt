package com.mikepenz.gameservices.achievements

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.GameKit.GKGameCenterControllerDelegateProtocol
import platform.GameKit.GKGameCenterViewController
import platform.GameKit.GKGameCenterViewControllerStateAchievements
import platform.GameKit.initWithState
import platform.GameKit.setGameCenterDelegate
import platform.UIKit.UIViewController

@Suppress("DEPRECATION_ERROR")
internal actual suspend fun presentAchievementsDashboard(
    presentingViewController: () -> UIViewController,
    delegate: GKGameCenterControllerDelegateProtocol,
) {
    withContext(Dispatchers.Main.immediate) {
        val controller = GKGameCenterViewController().initWithState(GKGameCenterViewControllerStateAchievements)
        controller.setGameCenterDelegate(delegate)
        presentingViewController().presentViewController(controller, true, null)
    }
}
