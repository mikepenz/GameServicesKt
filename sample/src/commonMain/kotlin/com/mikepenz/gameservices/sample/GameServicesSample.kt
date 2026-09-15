package com.mikepenz.gameservices.sample

import com.mikepenz.gameservices.GameServices
import com.mikepenz.gameservices.achievements.AchievementsClient
import com.mikepenz.gameservices.leaderboards.LeaderboardsClient
import com.mikepenz.gameservices.savedgames.SavedGamesClient
import com.mikepenz.gameservices.social.SocialClient

public enum class SampleAction {
    Authenticate,
    Achievements,
    Leaderboards,
    SavedGames,
    SavedGameSelection,
    Friends,
}

public class GameServicesSample public constructor(
    public val services: GameServices,
    public val achievements: AchievementsClient,
    public val leaderboards: LeaderboardsClient,
    public val savedGames: SavedGamesClient,
    public val social: SocialClient,
) {
    public fun availableActions(): Set<SampleAction> {
        if (!services.support.isSupported) return emptySet()
        return buildSet {
            add(SampleAction.Authenticate)
            add(SampleAction.Achievements)
            add(SampleAction.Leaderboards)
            add(SampleAction.SavedGames)
            add(SampleAction.Friends)
            if (savedGames.isSelectionPresenterSupported) add(SampleAction.SavedGameSelection)
        }
    }
}
