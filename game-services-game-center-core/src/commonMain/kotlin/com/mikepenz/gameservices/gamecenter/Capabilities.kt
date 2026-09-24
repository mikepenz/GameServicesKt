package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

/** OS-version checks are shared with tests; runtime account permissions remain separate. */
@InternalGameServicesApi
public fun gameCenterSupportedOperations(target: GameServicesPlatform, majorVersion: Long, operations: Set<GameServicesOperation>): Set<GameServicesOperation> {
    val unavailable = when (target) {
        GameServicesPlatform.WatchOS -> setOf(GameServicesOperation.ShowAchievements, GameServicesOperation.ShowLeaderboards,
            GameServicesOperation.ShowLeaderboard, GameServicesOperation.ShowPlayerProfile, GameServicesOperation.LoadAvatar)
        else -> if (majorVersion < if (target == GameServicesPlatform.MacOS) 15 else 18)
            setOf(GameServicesOperation.ShowLeaderboard, GameServicesOperation.ShowPlayerProfile) else emptySet()
    }
    return operations - unavailable
}

@InternalGameServicesApi
public fun requireGameCenterOperation(operations: Set<GameServicesOperation>, operation: GameServicesOperation) {
    if (operation !in operations) throw GameServicesException.UnsupportedOperation(GameServicesProvider.GameCenter, operation)
}
