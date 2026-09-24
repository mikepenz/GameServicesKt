@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)
package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import com.mikepenz.gameservices.savedgames.*
import kotlin.io.encoding.Base64

public fun GameCenterBackend.createSavedGamesClient(): SavedGamesClient = object : SavedGamesClient {
    override val isSupported = true
    override val isSelectionPresenterSupported = false
    override suspend fun listSavedGames(): Result<List<SavedGameMetadata>> = transport.call("saves").mapCatching { rows -> rows.map { SavedGameMetadata(SavedGameId(it[0]), it[1]) } }
    override suspend fun read(id: SavedGameId): Result<SavedGameReadResult> = transport.call("read", id.value).mapCatching { rows ->
        when (rows.first().first()) {
            "missing" -> SavedGameReadResult.NotFound
            "loaded" -> SavedGameReadResult.Loaded(rows[1].version())
            "conflict" -> SavedGameReadResult.Conflict(rows.conflict())
            else -> error("Invalid native save result")
        }
    }
    override suspend fun write(id: SavedGameId, data: SavedGameData): Result<SavedGameWriteResult> = transport.call("write", id.value, Base64.encode(data.copyBytes())).mapCatching { it.writeResult() }
    override suspend fun delete(id: SavedGameId): Result<Unit> = transport.call("delete", id.value).map { }
    override suspend fun resolve(conflictId: SavedGameConflictId, data: SavedGameData): Result<SavedGameWriteResult> = transport.call("resolve", conflictId.value, Base64.encode(data.copyBytes())).mapCatching { it.writeResult() }
    override suspend fun showSavedGameSelection(): Result<SavedGameMetadata?> = Result.failure(GameServicesException.UnsupportedOperation(GameServicesProvider.GameCenter, GameServicesOperation.SelectSavedGame))
}

private fun List<String>.version(): SavedGameVersion = SavedGameVersion(SavedGameMetadata(SavedGameId(this[0]), this[1]), SavedGameData.of(Base64.decode(this[2])))
private fun List<List<String>>.conflict(): SavedGameConflict = SavedGameConflict(SavedGameConflictId(first()[1]), drop(1).map { it.version() })
private fun List<List<String>>.writeResult(): SavedGameWriteResult = when (first().first()) {
    "saved" -> SavedGameWriteResult.Saved(SavedGameMetadata(SavedGameId(this[1][0]), this[1][1]))
    "conflict" -> SavedGameWriteResult.Conflict(conflict())
    else -> error("Invalid native write result")
}
