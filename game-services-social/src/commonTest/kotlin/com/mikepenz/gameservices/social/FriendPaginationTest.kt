package com.mikepenz.gameservices.social

import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.PlayerIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class FriendPaginationTest {
    @Test
    fun loadsBeyondFirstPageWithoutDuplicatingCumulativeResults() = runTest {
        fun friends(count: Int) = (1..count).map { PlayerProfile(PlayerIdentity(PlayerId("$it"), "$it")) }
        val pages = listOf(FriendPage(friends(100), true), FriendPage(friends(101), false)).iterator()
        assertEquals(friends(101), collectFriends { pages.next() })
    }

    @Test
    fun stopsARepeatedProviderPage() = runTest {
        assertFailsWith<IllegalStateException> { collectFriends { FriendPage(emptyList(), true) } }
    }
}
