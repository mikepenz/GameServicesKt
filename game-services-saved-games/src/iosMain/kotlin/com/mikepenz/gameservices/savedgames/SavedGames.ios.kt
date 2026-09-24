@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.savedgames

import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.gameServicesResult
import com.mikepenz.gameservices.toGameServicesException
import kotlin.coroutines.resume
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.GameKit.GKLocalPlayer
import platform.GameKit.GKSavedGame
import platform.GameKit.deleteSavedGamesWithName
import platform.GameKit.fetchSavedGamesWithCompletionHandler
import platform.GameKit.resolveConflictingSavedGames
import platform.GameKit.saveGameData
import platform.posix.memcpy

public fun createSavedGamesClient(): SavedGamesClient {
    val player = GKLocalPlayer.localPlayer()
    return IosSavedGamesClient(
        fetch = { player.fetchSavedGamesWithCompletionHandler(it) },
        save = { data, name, complete -> player.saveGameData(data, name, complete) },
        resolve = { games, data, complete -> player.resolveConflictingSavedGames(games, data, complete) },
        delete = { name, complete -> player.deleteSavedGamesWithName(name, complete) },
    )
}

@OptIn(ExperimentalForeignApi::class)
internal class IosSavedGamesClient(
    private val fetch: ((List<*>?, NSError?) -> Unit) -> Unit,
    private val save: (NSData, String, (GKSavedGame?, NSError?) -> Unit) -> Unit,
    private val resolve: (List<GKSavedGame>, NSData, (List<*>?, NSError?) -> Unit) -> Unit,
    private val delete: (String, (NSError?) -> Unit) -> Unit,
) : SavedGamesClient {
    private val operations = Mutex()
    private suspend fun <T> savedGameResult(block: suspend () -> T): Result<T> = gameServicesResult { operations.withLock { block() } }

    private val conflicts: MutableMap<String, List<GKSavedGame>> = mutableMapOf()

    override val isSupported: Boolean = true
    override val isSelectionPresenterSupported: Boolean = false

    override suspend fun listSavedGames(): Result<List<SavedGameMetadata>> = savedGameResult {
        savedGames()
            .mapNotNull { game -> game.name?.let { SavedGameMetadata(SavedGameId(it), it) } }
            .distinctBy(SavedGameMetadata::id)
    }

    override suspend fun read(id: SavedGameId): Result<SavedGameReadResult> = savedGameResult {
        val versions = savedGames().filter { it.name == id.value }
        when (versions.size) {
            0 -> SavedGameReadResult.NotFound
            1 -> SavedGameReadResult.Loaded(versions.single().toVersion())
            else -> SavedGameReadResult.Conflict(versions.toConflict(id))
        }
    }

    override suspend fun write(
        id: SavedGameId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult> = savedGameResult {
        val versions = savedGames().filter { it.name == id.value }
        if (versions.size > 1) return@savedGameResult SavedGameWriteResult.Conflict(versions.toConflict(id))
        SavedGameWriteResult.Saved(save(data, id.value).toMetadata())
    }

    override suspend fun delete(id: SavedGameId): Result<Unit> = savedGameResult {
        require(savedGames().any { it.name == id.value }) { "Unknown saved game ${id.value}" }
        suspendCancellableCoroutine { continuation ->
            delete(id.value) { error ->
                if (continuation.isActive) {
                    if (error == null) continuation.resume(Unit)
                    else continuation.resumeWith(Result.failure(error.toGameServicesException()))
                }
            }
        }
        conflicts.remove(id.value)
    }

    override suspend fun resolve(
        conflictId: SavedGameConflictId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult> = savedGameResult {
        val versions = requireNotNull(conflicts[conflictId.value]) { "Unknown saved game conflict" }
        val resolved = resolve(versions, data)
        val matching = resolved.filter { it.name == conflictId.value }
        if (matching.size > 1) SavedGameWriteResult.Conflict(matching.toConflict(SavedGameId(conflictId.value)))
        else {
            val metadata = requireNotNull(matching.singleOrNull()).toMetadata()
            conflicts.remove(conflictId.value)
            SavedGameWriteResult.Saved(metadata)
        }
    }

    override suspend fun showSavedGameSelection(): Result<SavedGameMetadata?> = Result.failure(
        GameServicesException.ProviderFailure(GameServicesProvider.GameCenter, "selection presenter unavailable"),
    )

    private suspend fun savedGames(): List<GKSavedGame> = suspendCancellableCoroutine { continuation ->
        fetch { games, error ->
            if (continuation.isActive) {
                if (error == null) continuation.resume(games.orEmpty().filterIsInstance<GKSavedGame>())
                else continuation.resumeWith(Result.failure(error.toGameServicesException()))
            }
        }
    }

    private suspend fun save(data: SavedGameData, name: String): GKSavedGame = suspendCancellableCoroutine { continuation ->
        save(data.copyBytes().toNSData(), name) { game, error ->
            if (continuation.isActive) {
                if (error == null) continuation.resumeWith(runCatching { requireNotNull(game) })
                else continuation.resumeWith(Result.failure(error.toGameServicesException()))
            }
        }
    }

    private suspend fun resolve(versions: List<GKSavedGame>, data: SavedGameData): List<GKSavedGame> =
        suspendCancellableCoroutine { continuation ->
            resolve(versions, data.copyBytes().toNSData()) { games, error ->
                if (continuation.isActive) {
                    if (error == null) continuation.resume(games.orEmpty().filterIsInstance<GKSavedGame>())
                    else continuation.resumeWith(Result.failure(error.toGameServicesException()))
                }
            }
        }

    private suspend fun List<GKSavedGame>.toConflict(id: SavedGameId): SavedGameConflict {
        val versions = map { it.toVersion() }
        conflicts[id.value] = this
        return SavedGameConflict(
            id = SavedGameConflictId(id.value),
            versions = versions,
        )
    }

    private suspend fun GKSavedGame.toVersion(): SavedGameVersion = SavedGameVersion(toMetadata(), SavedGameData.of(loadData()))

    private fun GKSavedGame.toMetadata(): SavedGameMetadata = SavedGameMetadata(
        id = SavedGameId(requireNotNull(name)),
        name = requireNotNull(name),
    )

    private suspend fun GKSavedGame.loadData(): ByteArray = suspendCancellableCoroutine { continuation ->
        loadDataWithCompletionHandler { data, error ->
            if (continuation.isActive) {
                if (error == null) continuation.resumeWith(runCatching { requireNotNull(data).toByteArray() })
                else continuation.resumeWith(Result.failure(error.toGameServicesException()))
            }
        }
    }
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { output ->
    if (output.isNotEmpty()) output.usePinned { memcpy(it.addressOf(0), bytes, length) }
}
