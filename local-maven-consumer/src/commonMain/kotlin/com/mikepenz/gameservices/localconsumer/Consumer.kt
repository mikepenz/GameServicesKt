package com.mikepenz.gameservices.localconsumer

import com.mikepenz.gameservices.GameServices
import com.mikepenz.gameservices.achievements.AchievementsClient
import com.mikepenz.gameservices.leaderboards.LeaderboardsClient
import com.mikepenz.gameservices.savedgames.SavedGamesClient
import com.mikepenz.gameservices.social.SocialClient

internal class Consumer(
    val services: GameServices,
    val achievements: AchievementsClient,
    val leaderboards: LeaderboardsClient,
    val savedGames: SavedGamesClient,
    val social: SocialClient,
)
