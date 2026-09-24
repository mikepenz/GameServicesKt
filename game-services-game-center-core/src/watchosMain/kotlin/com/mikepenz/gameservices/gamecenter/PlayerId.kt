@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*
import platform.GameKit.GKPlayer

@Suppress("DEPRECATION")
@InternalGameServicesApi
public actual fun GKPlayer.gameCenterPlayerId(): String = requireNotNull(playerID)
