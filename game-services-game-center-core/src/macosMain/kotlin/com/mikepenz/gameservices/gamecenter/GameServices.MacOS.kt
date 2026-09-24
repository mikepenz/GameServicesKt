@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import platform.AppKit.*
import platform.GameKit.GKLocalPlayer
import platform.GameKit.setAuthenticateHandler

/** Create once for the app with a currently valid authentication presenter. */
@Suppress("FunctionName")
public fun GameCenterBackend(presentingViewController: () -> NSViewController): GameCenterBackend = GameCenterBackend(
    NativeGameServices(
        present = { presentingViewController().presentViewControllerAsModalWindow(it) },
        installHandler = { GKLocalPlayer.localPlayer().setAuthenticateHandler(it) },
        currentPlayer = ::currentGameCenterPlayer,
        target = GameServicesPlatform.MacOS,
    ),
)
