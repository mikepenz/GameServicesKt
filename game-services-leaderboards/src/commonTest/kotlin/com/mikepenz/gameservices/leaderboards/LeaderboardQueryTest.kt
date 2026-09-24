@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.leaderboards

import com.mikepenz.gameservices.GameServicesProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

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
    @Test
    fun `expanded buffers replace earlier scores and preserve tied entries`() = runTest {
        fun score(rank: Long) = LeaderboardScore(null, rank, "$rank", rank, "Anonymous")
        val pages = listOf(
            ScorePage((1L..25L).map(::score), true),
            ScorePage((1L..50L).map(::score), false),
        ).iterator()
        val query = LeaderboardQuery(LeaderboardScope.Global, LeaderboardPeriod.AllTime, 20, 25)
        assertEquals((20L..44L).toList(), collectLeaderboardScores(query) { pages.next() }.map { it.rank })
        val tiedScores = List(25) { score(1) }
        val ties = listOf(ScorePage(tiedScores, true), ScorePage(tiedScores + List(3) { score(2) }, false)).iterator()
        assertEquals(List(3) { 2L }, collectLeaderboardScores(query.copy(startRank = 2)) { ties.next() }.map { it.rank })
    }

    @Test
    fun `anonymous and unranked scores are valid but excessive queries fail`() = runTest {
        LeaderboardScore(null, 1, "1", null, "Anonymous")
        assertFailsWith<IllegalArgumentException> { LeaderboardQuery(LeaderboardScope.Global, LeaderboardPeriod.AllTime, 1001, 25) }
        var pages = 0
        assertFailsWith<IllegalStateException> {
            collectLeaderboardScores(LeaderboardQuery(LeaderboardScope.Global, LeaderboardPeriod.AllTime, 1, 25)) {
                pages++
                ScorePage(emptyList(), true)
            }
        }
        assertEquals(41, pages)
    }
}
