@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)
package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import com.mikepenz.gameservices.achievements.*
import com.mikepenz.gameservices.leaderboards.*
import com.mikepenz.gameservices.savedgames.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import platform.AppKit.NSViewController
import kotlin.io.encoding.Base64
import kotlin.native.CName
import kotlin.experimental.ExperimentalNativeApi

private class Session {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val backend by lazy { GameCenterBackend { NSViewController() } }
    val achievements by lazy { backend.createAchievementsClient() }
    val leaderboards by lazy { backend.createLeaderboardsClient() }
    val saves by lazy { backend.createSavedGamesClient() }
    val social by lazy { backend.createSocialClient() }

    suspend fun request(op: String, a: List<String>): List<List<String>> = when (op) {
        "state" -> backend.authenticationState.first { encodeBridgeRows(it.rows()) != a[0] }.rows()
        "authenticate" -> backend.refreshAuthentication().getOrThrow()?.let { listOf(listOf(it.id.value, it.displayName)) } ?: emptyList()
        "achievements" -> achievements.loadAchievements(a[0].toBooleanStrict()).getOrThrow().map { listOf(it.id.value, it.title, it.description, it.points.toString(), it.isHidden.toString(), it.state.name, it.completionPercent.toString()) }
        "achievement" -> { achievements.reportProgress(AchievementId(a[0]), AchievementProgress.Percent(a[1].toInt())).getOrThrow(); emptyList() }
        "showAchievements" -> { achievements.showAchievements().getOrThrow(); emptyList() }
        "leaderboards" -> leaderboards.loadLeaderboards().getOrThrow().map { listOf(it.id.value, it.title) }
        "score" -> { leaderboards.submitScore(LeaderboardId(a[0]), a[1].toLong()).getOrThrow(); emptyList() }
        "scores" -> leaderboards.loadScores(LeaderboardId(a[0]), LeaderboardQuery(LeaderboardScope.valueOf(a[1]), LeaderboardPeriod.valueOf(a[2]), a[3].toInt(), a[4].toInt())).getOrThrow().map { it.row() }
        "currentScore" -> leaderboards.loadCurrentPlayerScore(LeaderboardId(a[0]), LeaderboardScope.valueOf(a[1]), LeaderboardPeriod.valueOf(a[2])).getOrThrow()?.let { listOf(it.row()) } ?: emptyList()
        "showLeaderboards" -> { leaderboards.showLeaderboards().getOrThrow(); emptyList() }
        "showLeaderboard" -> { leaderboards.showLeaderboard(LeaderboardId(a[0])).getOrThrow(); emptyList() }
        "saves" -> saves.listSavedGames().getOrThrow().map { listOf(it.id.value, it.name) }
        "read" -> when (val result = saves.read(SavedGameId(a[0])).getOrThrow()) {
            SavedGameReadResult.NotFound -> listOf(listOf("missing"))
            is SavedGameReadResult.Loaded -> listOf(listOf("loaded"), result.version.row())
            is SavedGameReadResult.Conflict -> result.conflict.rows()
        }
        "write" -> saves.write(SavedGameId(a[0]), SavedGameData.of(Base64.decode(a[1]))).getOrThrow().rows()
        "resolve" -> saves.resolve(SavedGameConflictId(a[0]), SavedGameData.of(Base64.decode(a[1]))).getOrThrow().rows()
        "delete" -> { saves.delete(SavedGameId(a[0])).getOrThrow(); emptyList() }
        "friendsAccess" -> listOf(listOf(social.requestFriendsAccess().getOrThrow().name))
        "friends" -> social.loadFriends().getOrThrow().map { listOf(it.identity.id.value, it.identity.displayName) }
        "avatar" -> social.loadAvatar(PlayerId(a[0])).getOrThrow()?.let { listOf(listOf(Base64.encode(it.copyBytes()))) } ?: emptyList()
        "profile" -> { social.showPlayerProfile(PlayerId(a[0])).getOrThrow(); emptyList() }
        else -> throw IllegalArgumentException("Unknown bridge operation")
    }
}

private fun AuthenticationState.rows(): List<List<String>> = when (this) {
    is AuthenticationState.Authenticated -> listOf(listOf(player.id.value, player.displayName))
    AuthenticationState.Authenticating -> listOf(listOf("authenticating"))
    AuthenticationState.Unauthenticated, AuthenticationState.Unsupported -> emptyList()
}

private fun LeaderboardScore.row() = listOf(player?.id?.value.orEmpty(), player?.displayName.orEmpty(), value.toString(), formattedValue, rank?.toString().orEmpty(), displayName)
private fun SavedGameVersion.row() = listOf(metadata.id.value, metadata.name, Base64.encode(data.copyBytes()))
private fun SavedGameConflict.rows() = listOf(listOf("conflict", id.value)) + versions.map { it.row() }
private fun SavedGameWriteResult.rows(): List<List<String>> = when (this) {
    is SavedGameWriteResult.Saved -> listOf(listOf("saved"), listOf(metadata.id.value, metadata.name))
    is SavedGameWriteResult.Conflict -> conflict.rows()
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("gs_gamecenter_create")
fun bridgeCreate(): COpaquePointer = StableRef.create(Session()).asCPointer()

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
@CName("gs_gamecenter_close")
fun bridgeClose(handle: COpaquePointer) {
    val ref = handle.asStableRef<Session>()
    ref.get().scope.cancel()
    ref.dispose()
}

@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class, DelicateCoroutinesApi::class)
@CName("gs_gamecenter_request")
fun bridgeRequest(handle: COpaquePointer, operation: CPointer<ByteVar>, arguments: CPointer<ByteVar>, context: COpaquePointer?, callback: CPointer<CFunction<(COpaquePointer?, Int, CPointer<ByteVar>?) -> Unit>>) {
    val session = handle.asStableRef<Session>().get()
    val op = operation.toKString()
    val args = arguments.toKString()
    // This scope is owned by the native bridge session and cancelled by bridgeClose.
    session.scope.launch(start = CoroutineStart.ATOMIC) {
        val result = try { Result.success(encodeBridgeRows(session.request(op, decodeBridgeRows(args).single()))) }
        catch (error: Exception) { Result.failure(error) }
        val response = result.getOrElse { bridgeError(it) }
        memScoped { callback(context, if (result.isSuccess) 1 else 0, response.cstr.ptr) }
    }
}
