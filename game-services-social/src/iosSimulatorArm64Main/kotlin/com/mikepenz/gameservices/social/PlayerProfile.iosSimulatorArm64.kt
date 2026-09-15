package com.mikepenz.gameservices.social

import platform.GameKit.GKGameCenterControllerDelegateProtocol
import platform.GameKit.GKGameCenterViewController
import platform.GameKit.GKPlayer
import platform.GameKit.initWithPlayer
import platform.GameKit.setGameCenterDelegate
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Suppress("DEPRECATION_ERROR")
internal actual fun presentPlayerProfile(
    presentingViewController: () -> UIViewController,
    delegate: GKGameCenterControllerDelegateProtocol,
    player: GKPlayer,
) {
    dispatch_async(dispatch_get_main_queue()) {
        val controller = GKGameCenterViewController().initWithPlayer(player)
        controller.setGameCenterDelegate(delegate)
        presentingViewController().presentViewController(controller, true, null)
    }
}
