@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.savedgames

import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.GameServicesOperation
import com.mikepenz.gameservices.InternalGameServicesApi

import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesPlatform
import kotlin.jvm.JvmInline

@JvmInline
public value class SavedGameId public constructor(
    public val value: String,
) {
    init {
        require(value.isNotEmpty()) { "Saved game name must not be empty" }
    }
}

@JvmInline
public value class SavedGameConflictId public constructor(
    public val value: String,
)

public data class SavedGameMetadata public constructor(
    public val id: SavedGameId,
    public val name: String,
)

public class SavedGameData private constructor(
    private val bytes: ByteArray,
) {
    public fun copyBytes(): ByteArray = bytes.copyOf()

    public companion object {
        public fun of(bytes: ByteArray): SavedGameData = SavedGameData(bytes.copyOf())
    }
}

public data class SavedGameVersion public constructor(
    public val metadata: SavedGameMetadata,
    public val data: SavedGameData,
)

public data class SavedGameConflict public constructor(
    public val id: SavedGameConflictId,
    public val versions: List<SavedGameVersion>,
) {
    init {
        require(versions.isNotEmpty())
    }
}

public sealed interface SavedGameReadResult {
    public data object NotFound : SavedGameReadResult

    public data class Loaded public constructor(
        public val version: SavedGameVersion,
    ) : SavedGameReadResult

    public data class Conflict public constructor(
        public val conflict: SavedGameConflict,
    ) : SavedGameReadResult
}

public sealed interface SavedGameWriteResult {
    public data class Saved public constructor(
        public val metadata: SavedGameMetadata,
    ) : SavedGameWriteResult

    public data class Conflict public constructor(
        public val conflict: SavedGameConflict,
    ) : SavedGameWriteResult
}

public interface SavedGamesClient {
    public val supportedOperations: Set<GameServicesOperation>
        get() = if (isSupported) setOf(GameServicesOperation.ListSavedGames, GameServicesOperation.ReadSavedGame, GameServicesOperation.WriteSavedGame, GameServicesOperation.DeleteSavedGame, GameServicesOperation.ResolveConflict, GameServicesOperation.SelectSavedGame) - if (isSelectionPresenterSupported) emptySet() else setOf(GameServicesOperation.SelectSavedGame) else emptySet()

    public val isSupported: Boolean

    public val isSelectionPresenterSupported: Boolean

    public suspend fun listSavedGames(): Result<List<SavedGameMetadata>>

    public suspend fun read(id: SavedGameId): Result<SavedGameReadResult>

    public suspend fun write(
        id: SavedGameId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult>

    public suspend fun delete(id: SavedGameId): Result<Unit>

    public suspend fun resolve(
        conflictId: SavedGameConflictId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult>

    public suspend fun showSavedGameSelection(): Result<SavedGameMetadata?>
}

internal class UnsupportedSavedGamesClient(
    private val target: GameServicesPlatform,
    private val provider: GameServicesProvider = GameServicesProvider.None,
) : SavedGamesClient {
    override val isSupported: Boolean = false

    override val isSelectionPresenterSupported: Boolean = false

    override suspend fun listSavedGames(): Result<List<SavedGameMetadata>> = unsupported(GameServicesOperation.ListSavedGames)

    override suspend fun read(id: SavedGameId): Result<SavedGameReadResult> = unsupported(GameServicesOperation.ReadSavedGame)

    override suspend fun write(
        id: SavedGameId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult> = unsupported(GameServicesOperation.WriteSavedGame)

    override suspend fun delete(id: SavedGameId): Result<Unit> = unsupported(GameServicesOperation.DeleteSavedGame)

    override suspend fun resolve(
        conflictId: SavedGameConflictId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult> = unsupported(GameServicesOperation.ResolveConflict)

    override suspend fun showSavedGameSelection(): Result<SavedGameMetadata?> = unsupported(GameServicesOperation.SelectSavedGame)

    private fun <T> unsupported(operation: GameServicesOperation): Result<T> = Result.failure(
        if (provider == GameServicesProvider.None) GameServicesException.UnsupportedTarget(target)
        else GameServicesException.UnsupportedOperation(provider, operation),
    )
}

@InternalGameServicesApi
public suspend fun <T> writeAndCommit(write: suspend () -> Boolean, commit: suspend () -> T): T {
    check(write()) { "Writing saved game bytes to disk failed" }
    return commit()
}

@InternalGameServicesApi
public fun SavedGameId.requirePortableName() {
    require(value.length in 1..100 && value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "-._~" }) {
        "Android saved game names must contain 1-100 URL-safe ASCII characters"
    }
}

public fun createUnsupportedSavedGamesClient(
    target: GameServicesPlatform,
    provider: GameServicesProvider = GameServicesProvider.None,
): SavedGamesClient = UnsupportedSavedGamesClient(target, provider)
