package com.mikepenz.gameservices.achievements

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class AchievementProgressTest {
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
}
