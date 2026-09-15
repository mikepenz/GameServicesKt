package com.mikepenz.gameservices.achievements

import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesPlatform
import kotlin.jvm.JvmInline

@JvmInline
public value class AchievementId public constructor(
    public val value: String,
)

public data class Achievement public constructor(
    public val id: AchievementId,
    public val title: String,
    public val description: String,
    public val points: Long,
    public val isHidden: Boolean,
    public val state: AchievementState,
    public val completionPercent: Int,
    public val steps: AchievementSteps? = null,
) {
    init {
        require(completionPercent in 0..100)
    }
}

public enum class AchievementState {
    Locked,
    Unlocked,
}

public data class AchievementSteps public constructor(
    public val current: Int,
    public val total: Int,
) {
    init {
        require(total > 0)
        require(current in 0..total)
    }
}

public sealed interface AchievementProgress {
    public data object Unlocked : AchievementProgress

    public data class Percent public constructor(
        public val value: Int,
    ) : AchievementProgress {
        init {
            require(value in 0..100)
        }
    }

    public data class Steps public constructor(
        public val current: Int,
        public val total: Int,
    ) : AchievementProgress {
        init {
            require(total > 0)
            require(current in 0..total)
        }
    }
}

public interface AchievementsClient {
    public suspend fun loadAchievements(): Result<List<Achievement>>

    public suspend fun reportProgress(
        id: AchievementId,
        progress: AchievementProgress,
    ): Result<Unit>

    public suspend fun showAchievements(): Result<Unit>
}

internal class UnsupportedAchievementsClient(
    target: GameServicesPlatform,
) : AchievementsClient {
    private val unsupported: GameServicesException.UnsupportedTarget =
        GameServicesException.UnsupportedTarget(target)

    override suspend fun loadAchievements(): Result<List<Achievement>> = Result.failure(unsupported)

    override suspend fun reportProgress(
        id: AchievementId,
        progress: AchievementProgress,
    ): Result<Unit> = Result.failure(unsupported)

    override suspend fun showAchievements(): Result<Unit> = Result.failure(unsupported)
}
