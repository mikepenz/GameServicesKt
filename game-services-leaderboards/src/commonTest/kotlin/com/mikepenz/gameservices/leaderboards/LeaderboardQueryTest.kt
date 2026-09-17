package com.mikepenz.gameservices.leaderboards

import com.mikepenz.gameservices.GameServicesProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LeaderboardQueryTest {
    @Test
    fun `maps shared and provider leaderboard IDs`() {
        val common = LeaderboardId("high_score")
        val mappings = LeaderboardIdMappings.of(
            LeaderboardIdMapping(
                id = common,
                googlePlayGamesId = LeaderboardId("CgkI-google"),
                gameCenterId = LeaderboardId("game-center.high-score"),
            ),
        )

        assertEquals(LeaderboardId("CgkI-google"), mappings.providerId(GameServicesProvider.GooglePlayGames, common))
        assertEquals(LeaderboardId("game-center.high-score"), mappings.providerId(GameServicesProvider.GameCenter, common))
        assertEquals(common, mappings.commonId(GameServicesProvider.GooglePlayGames, LeaderboardId("CgkI-google")))
        assertEquals(common, mappings.commonId(GameServicesProvider.GameCenter, LeaderboardId("game-center.high-score")))
        assertEquals(LeaderboardId("unmapped"), mappings.providerId(GameServicesProvider.GameCenter, LeaderboardId("unmapped")))
    }

    @Test
    fun `rejects ambiguous leaderboard mappings`() {
        assertFailsWith<IllegalArgumentException> {
            LeaderboardIdMappings.of(
                LeaderboardIdMapping(LeaderboardId("one"), LeaderboardId("google-one"), LeaderboardId("apple-one")),
                LeaderboardIdMapping(LeaderboardId("two"), LeaderboardId("google-two"), LeaderboardId("apple-one")),
            )
        }
    }

    @Test
    fun rejectsNonPositiveRankAndLimit() {
        LeaderboardQuery(LeaderboardScope.Global, LeaderboardPeriod.AllTime, startRank = 1, limit = 1)

        assertFailsWith<IllegalArgumentException> {
            LeaderboardQuery(LeaderboardScope.Global, LeaderboardPeriod.AllTime, startRank = 0, limit = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            LeaderboardQuery(LeaderboardScope.Global, LeaderboardPeriod.AllTime, startRank = 1, limit = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            LeaderboardQuery(LeaderboardScope.Global, LeaderboardPeriod.AllTime, startRank = 1, limit = 26)
        }
    }

    @Test
    fun acceptsEveryScopeAndPeriod() {
        LeaderboardScope.entries.forEach { scope ->
            LeaderboardPeriod.entries.forEach { period ->
                LeaderboardQuery(scope, period, startRank = 1, limit = 25)
            }
        }
    }
}
