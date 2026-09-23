package com.mikepenz.gameservices.achievements

import com.mikepenz.gameservices.GameServicesProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AchievementProgressTest {
    @Test
    fun `maps shared and provider achievement IDs`() {
        val common = AchievementId("dragon_slayer")
        val mappings = AchievementIdMappings.of(
            AchievementIdMapping(
                id = common,
                googlePlayGamesId = AchievementId("CgkI-google"),
                gameCenterId = AchievementId("game-center.dragon-slayer"),
            ),
        )

        assertEquals(AchievementId("CgkI-google"), mappings.providerId(GameServicesProvider.GooglePlayGames, common))
        assertEquals(AchievementId("game-center.dragon-slayer"), mappings.providerId(GameServicesProvider.GameCenter, common))
        assertEquals(common, mappings.commonId(GameServicesProvider.GooglePlayGames, AchievementId("CgkI-google")))
        assertEquals(common, mappings.commonId(GameServicesProvider.GameCenter, AchievementId("game-center.dragon-slayer")))
        assertEquals(AchievementId("unmapped"), mappings.providerId(GameServicesProvider.GooglePlayGames, AchievementId("unmapped")))
    }

    @Test
    fun `rejects ambiguous achievement mappings`() {
        assertFailsWith<IllegalArgumentException> {
            AchievementIdMappings.of(
                AchievementIdMapping(AchievementId("one"), AchievementId("google-one"), AchievementId("apple-one")),
                AchievementIdMapping(AchievementId("two"), AchievementId("google-one"), AchievementId("apple-two")),
            )
        }
    }

    @Test
    fun validatesPercentAndSteps() {
        AchievementProgress.Percent(0)
        AchievementProgress.Percent(100)
        AchievementProgress.Steps(current = 2, total = 2)

        assertFailsWith<IllegalArgumentException> { AchievementProgress.Percent(-1) }
        assertFailsWith<IllegalArgumentException> { AchievementProgress.Percent(101) }
        assertFailsWith<IllegalArgumentException> { AchievementProgress.Steps(current = 1, total = 0) }
        assertFailsWith<IllegalArgumentException> { AchievementProgress.Steps(current = 3, total = 2) }
    }

    @Test
    fun `converts progress without exceeding configured steps`() {
        assertEquals(0, AchievementProgress.Percent(0).stepsFor(17))
        assertEquals(8, AchievementProgress.Percent(50).stepsFor(17))
        assertEquals(17, AchievementProgress.Percent(100).stepsFor(17))
        assertEquals(6, AchievementProgress.Steps(2, 5).stepsFor(17))
        assertEquals(40, AchievementProgress.Steps(2, 5).percent())
    }
    @Test
    fun `standard achievements never access incremental metadata`() {
        assertEquals(null, configuredSteps(false) { error("Standard SDK getter must not be called") })
        assertEquals(10, configuredSteps(true) { 10 })
    }

    @Test
    fun `large progress values do not overflow`() {
        assertEquals(100, AchievementProgress.Steps(30_000_000, 30_000_000).percent())
        assertEquals(10_000, AchievementProgress.Steps(1_000_000, 1_000_000).stepsFor(10_000))
        assertEquals(Int.MAX_VALUE, AchievementProgress.Percent(100).stepsFor(Int.MAX_VALUE))
    }
}
