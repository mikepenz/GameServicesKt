package com.mikepenz.gameservices.leaderboards

import com.mikepenz.gameservices.GameServicesPlatform

public fun createLeaderboardsClient(
    ids: LeaderboardIdMappings = LeaderboardIdMappings.Empty,
): LeaderboardsClient =
    UnsupportedLeaderboardsClient(GameServicesPlatform.Wasm)
