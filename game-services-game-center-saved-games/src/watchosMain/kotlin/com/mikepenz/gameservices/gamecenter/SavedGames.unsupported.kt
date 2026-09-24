@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import com.mikepenz.gameservices.savedgames.*

public fun GameCenterBackend.createSavedGamesClient(): SavedGamesClient = createUnsupportedSavedGamesClient(support.target, support.provider)
