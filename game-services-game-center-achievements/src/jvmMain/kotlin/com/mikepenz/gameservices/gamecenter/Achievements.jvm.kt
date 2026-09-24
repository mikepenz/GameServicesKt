@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)
package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import com.mikepenz.gameservices.achievements.*

public fun GameCenterBackend.createAchievementsClient(ids: AchievementIdMappings = AchievementIdMappings.Empty): AchievementsClient =
    object : AchievementsClient {
        override val isSupported = true
        override suspend fun loadAchievements(forceReload: Boolean): Result<List<Achievement>> = transport.call("achievements", forceReload.toString()).mapCatching { rows ->
            rows.map { row -> Achievement(ids.commonId(GameServicesProvider.GameCenter, AchievementId(row[0])), row[1], row[2], row[3].toLong(), row[4].toBooleanStrict(), AchievementState.valueOf(row[5]), row[6].toInt()) }
        }
        override suspend fun reportProgress(id: AchievementId, progress: AchievementProgress): Result<Unit> =
            transport.call("achievement", ids.providerId(GameServicesProvider.GameCenter, id).value, progress.percent().toString()).map { }
        override suspend fun showAchievements(): Result<Unit> = transport.call("showAchievements").map { }
    }
