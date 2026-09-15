package com.mikepenz.gameservices.achievements

import platform.GameKit.GKGameCenterControllerDelegateProtocol
import platform.GameKit.GKGameCenterViewController
import platform.GameKit.GKGameCenterViewControllerStateAchievements
import platform.GameKit.initWithState
import platform.GameKit.setGameCenterDelegate
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Suppress("DEPRECATION_ERROR")
internal actual fun presentAchievementsDashboard(
    presentingViewController: () -> UIViewController,
    delegate: GKGameCenterControllerDelegateProtocol,
) {
    dispatch_async(dispatch_get_main_queue()) {
        val controller = GKGameCenterViewController().initWithState(GKGameCenterViewControllerStateAchievements)
        controller.setGameCenterDelegate(delegate)
        presentingViewController().presentViewController(controller, true, null)
    }
}
