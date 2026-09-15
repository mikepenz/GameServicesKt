package com.mikepenz.gameservices.savedgames

import com.mikepenz.gameservices.GameServicesPlatform

public fun createSavedGamesClient(): SavedGamesClient = UnsupportedSavedGamesClient(GameServicesPlatform.JVM)
