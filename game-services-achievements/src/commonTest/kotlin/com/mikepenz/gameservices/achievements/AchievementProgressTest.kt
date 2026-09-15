package com.mikepenz.gameservices.achievements

import kotlin.test.Test
import kotlin.test.assertFailsWith

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
}
