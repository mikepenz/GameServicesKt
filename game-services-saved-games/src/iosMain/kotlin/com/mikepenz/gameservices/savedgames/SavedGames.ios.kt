package com.mikepenz.gameservices.savedgames

import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesProvider
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
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
import kotlin.coroutines.resume

public fun createSavedGamesClient(): SavedGamesClient = IosSavedGamesClient(GKLocalPlayer.localPlayer())

@OptIn(ExperimentalForeignApi::class)
private class IosSavedGamesClient(
    private val player: GKLocalPlayer,
) : SavedGamesClient {
    private val conflicts: MutableMap<String, List<GKSavedGame>> = mutableMapOf()

    override val isSelectionPresenterSupported: Boolean = false

    override suspend fun listSavedGames(): Result<List<SavedGameMetadata>> = providerResult {
        savedGames()
            .mapNotNull { game -> game.name?.let { SavedGameMetadata(SavedGameId(it), it) } }
            .distinctBy(SavedGameMetadata::id)
    }

    override suspend fun read(id: SavedGameId): Result<SavedGameReadResult> = providerResult {
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
    ): Result<SavedGameWriteResult> = providerResult {
        val versions = savedGames().filter { it.name == id.value }
        if (versions.size > 1) return@providerResult SavedGameWriteResult.Conflict(versions.toConflict(id))
        SavedGameWriteResult.Saved(save(data, id.value).toMetadata())
    }

    override suspend fun delete(id: SavedGameId): Result<Unit> = providerResult {
        require(savedGames().any { it.name == id.value }) { "Unknown saved game ${id.value}" }
        suspendCancellableCoroutine { continuation ->
            player.deleteSavedGamesWithName(id.value) { error ->
                if (continuation.isActive) {
                    if (error == null) continuation.resume(Unit)
                    else continuation.resumeWith(Result.failure(error.toGameServicesException()))
                }
            }
        }
    }

    override suspend fun resolve(
        conflictId: SavedGameConflictId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult> = providerResult {
        val versions = requireNotNull(conflicts[conflictId.value]) { "Unknown saved game conflict" }
        val resolved = resolve(versions, data)
        conflicts.remove(conflictId.value)
        val matching = resolved.filter { it.name == conflictId.value }
        if (matching.size > 1) SavedGameWriteResult.Conflict(matching.toConflict(SavedGameId(conflictId.value)))
        else SavedGameWriteResult.Saved(requireNotNull(matching.singleOrNull()).toMetadata())
    }

    override suspend fun showSavedGameSelection(): Result<SavedGameMetadata?> = Result.failure(
        GameServicesException.ProviderFailure(GameServicesProvider.GameCenter, "selection presenter unavailable"),
    )

    private suspend fun savedGames(): List<GKSavedGame> = suspendCancellableCoroutine { continuation ->
        player.fetchSavedGamesWithCompletionHandler { games, error ->
            if (continuation.isActive) {
                if (error == null) continuation.resume(games.orEmpty().filterIsInstance<GKSavedGame>())
                else continuation.resumeWith(Result.failure(error.toGameServicesException()))
            }
        }
    }

    private suspend fun save(data: SavedGameData, name: String): GKSavedGame = suspendCancellableCoroutine { continuation ->
        player.saveGameData(data.copyBytes().toNSData(), name) { game, error ->
            if (continuation.isActive) {
                if (error == null) continuation.resume(requireNotNull(game))
                else continuation.resumeWith(Result.failure(error.toGameServicesException()))
            }
        }
    }

    private suspend fun resolve(versions: List<GKSavedGame>, data: SavedGameData): List<GKSavedGame> =
        suspendCancellableCoroutine { continuation ->
            player.resolveConflictingSavedGames(versions, data.copyBytes().toNSData()) { games, error ->
                if (continuation.isActive) {
                    if (error == null) continuation.resume(games.orEmpty().filterIsInstance<GKSavedGame>())
                    else continuation.resumeWith(Result.failure(error.toGameServicesException()))
                }
            }
        }

    private suspend fun List<GKSavedGame>.toConflict(id: SavedGameId): SavedGameConflict {
        conflicts[id.value] = this
        return SavedGameConflict(
            id = SavedGameConflictId(id.value),
            versions = map { it.toVersion() },
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
                if (error == null) continuation.resume(requireNotNull(data).toByteArray())
                else continuation.resumeWith(Result.failure(error.toGameServicesException()))
            }
        }
    }
}

private suspend fun <T> providerResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: GameServicesException) {
    Result.failure(exception)
} catch (exception: Throwable) {
    Result.failure(GameServicesException.ProviderFailure(GameServicesProvider.GameCenter, exception.toString()))
}

private fun NSError.toGameServicesException(): GameServicesException = GameServicesException.ProviderFailure(
    provider = GameServicesProvider.GameCenter,
    code = "$domain:$code",
)

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { output ->
    if (output.isNotEmpty()) output.usePinned { memcpy(it.addressOf(0), bytes, length) }
}
