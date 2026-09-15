package com.mikepenz.gameservices.leaderboards

import com.mikepenz.gameservices.GameServicesPlatform

public fun createLeaderboardsClient(): LeaderboardsClient =
    UnsupportedLeaderboardsClient(GameServicesPlatform.Wasm)
