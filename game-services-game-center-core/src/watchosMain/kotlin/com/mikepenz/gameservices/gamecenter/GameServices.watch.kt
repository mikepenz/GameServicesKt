@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import platform.GameKit.GKLocalPlayer

/** watchOS authenticates through the account configured on the device, without a system presenter. */
@Suppress("FunctionName")
public fun GameCenterBackend(): GameCenterBackend = GameCenterBackend(
    NativeGameServices<Unit>(
        present = {},
        installHandler = { complete -> GKLocalPlayer.localPlayer().authenticateHandler = { error -> complete(null, error) } },
        currentPlayer = ::currentGameCenterPlayer,
        target = GameServicesPlatform.WatchOS,
    ),
)
