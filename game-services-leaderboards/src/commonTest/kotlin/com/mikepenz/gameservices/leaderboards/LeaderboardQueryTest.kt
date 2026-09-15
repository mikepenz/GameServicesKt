package com.mikepenz.gameservices.leaderboards

import kotlin.test.Test
import kotlin.test.assertFailsWith

class LeaderboardQueryTest {
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
