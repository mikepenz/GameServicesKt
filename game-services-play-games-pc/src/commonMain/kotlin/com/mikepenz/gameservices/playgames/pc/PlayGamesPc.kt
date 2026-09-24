@file:OptIn(com.mikepenz.gameservices.ExperimentalGameServicesApi::class)
package com.mikepenz.gameservices.playgames.pc

import com.mikepenz.gameservices.*
import com.mikepenz.gameservices.recall.*

@ExperimentalGameServicesApi
public enum class PcInitializationStatus { NotInitialized, Error, ShutdownRequired, RuntimeUpdateRequired, RuntimeUnavailable, Unknown }

@ExperimentalGameServicesApi
public class PcInitializationException public constructor(public val status: PcInitializationStatus, public val code: Int) : Exception("Play PC initialization: $status ($code)")

/** One session per host. Provide the absolute path to the built gs_play_pc.dll beside play_pc_sdk.dll. */
@ExperimentalGameServicesApi
public expect class PlayGamesPcBackend public constructor(nativeLibrary: String) : RecallClient {
    public suspend fun initialize(): Result<Unit>
    override suspend fun requestRecallAccess(): Result<RecallSession>
    public suspend fun close()
}

internal fun pcFailure(stage: Int, code: Int): Exception? = when {
    code == 0 -> null
    stage == 0 -> PcInitializationException(when (code) {
        -1 -> PcInitializationStatus.NotInitialized
        1 -> PcInitializationStatus.Error
        2 -> PcInitializationStatus.ShutdownRequired
        3 -> PcInitializationStatus.RuntimeUpdateRequired
        4 -> PcInitializationStatus.RuntimeUnavailable
        else -> PcInitializationStatus.Unknown
    }, code)
    else -> GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, "PC_RECALL:$code")
}
