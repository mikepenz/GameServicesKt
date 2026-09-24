@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import platform.GameKit.GKPlayer

internal actual suspend fun presentPlayerProfile(player: GKPlayer): Unit =
    throw GameServicesException.UnsupportedOperation(GameServicesProvider.GameCenter, GameServicesOperation.ShowPlayerProfile)

internal actual suspend fun GKPlayer.loadAvatarBytes(): ByteArray? =
    throw GameServicesException.UnsupportedOperation(GameServicesProvider.GameCenter, GameServicesOperation.LoadAvatar)
