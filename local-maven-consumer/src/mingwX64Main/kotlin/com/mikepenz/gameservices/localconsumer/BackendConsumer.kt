@file:OptIn(com.mikepenz.gameservices.ExperimentalGameServicesApi::class)
package com.mikepenz.gameservices.localconsumer
import com.mikepenz.gameservices.playgames.pc.PlayGamesPcBackend
import com.mikepenz.gameservices.recall.RecallSession
internal suspend fun windowsRecallBackend(backend: PlayGamesPcBackend): Result<RecallSession> = backend.requestRecallAccess()
