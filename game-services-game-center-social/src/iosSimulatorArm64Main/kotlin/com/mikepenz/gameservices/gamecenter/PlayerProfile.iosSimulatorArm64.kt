package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.social.*

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.GameKit.GKGameCenterControllerDelegateProtocol
import platform.GameKit.GKGameCenterViewController
import platform.GameKit.GKPlayer
import platform.GameKit.initWithPlayer
import platform.GameKit.setGameCenterDelegate
import platform.UIKit.UIViewController

@Suppress("DEPRECATION_ERROR")
internal actual suspend fun presentPlayerProfile(
    presentingViewController: () -> UIViewController,
    delegate: GKGameCenterControllerDelegateProtocol,
    player: GKPlayer,
) {
    withContext(Dispatchers.Main.immediate) {
        val controller = GKGameCenterViewController().initWithPlayer(player)
        controller.setGameCenterDelegate(delegate)
        presentingViewController().presentViewController(controller, true, null)
    }
}
