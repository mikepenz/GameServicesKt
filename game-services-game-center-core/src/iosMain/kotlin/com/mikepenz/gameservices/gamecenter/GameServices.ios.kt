@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import platform.UIKit.UIViewController
import platform.GameKit.GKLocalPlayer
import platform.GameKit.setAuthenticateHandler

/** Create once for the app with a currently valid authentication presenter. */
@Suppress("FunctionName")
public fun GameCenterBackend(presentingViewController: () -> UIViewController): GameCenterBackend = GameCenterBackend(
    NativeGameServices(
        present = { presentingViewController().presentViewController(it, true, null) },
        installHandler = { GKLocalPlayer.localPlayer().setAuthenticateHandler(it) },
        currentPlayer = ::currentGameCenterPlayer,
        target = GameServicesPlatform.IOS,
    ),
)
