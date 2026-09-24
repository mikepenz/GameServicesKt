@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*
import platform.GameKit.GKPlayer

@InternalGameServicesApi
public actual fun GKPlayer.gameCenterPlayerId(): String = gamePlayerID
