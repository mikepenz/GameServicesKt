package com.mikepenz.gameservices.savedgames

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
) : SavedGamesClient {
    override val isSupported: Boolean = false

    override val isSelectionPresenterSupported: Boolean = false

    override suspend fun listSavedGames(): Result<List<SavedGameMetadata>> = unsupported()

    override suspend fun read(id: SavedGameId): Result<SavedGameReadResult> = unsupported()

    override suspend fun write(
        id: SavedGameId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult> = unsupported()

    override suspend fun delete(id: SavedGameId): Result<Unit> = unsupported()

    override suspend fun resolve(
        conflictId: SavedGameConflictId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult> = unsupported()

    override suspend fun showSavedGameSelection(): Result<SavedGameMetadata?> = unsupported()

    private fun <T> unsupported(): Result<T> = Result.failure(
        GameServicesException.UnsupportedTarget(target),
    )
}

internal suspend fun <T> writeAndCommit(write: suspend () -> Boolean, commit: suspend () -> T): T {
    check(write()) { "Writing saved game bytes to disk failed" }
    return commit()
}

internal fun SavedGameId.requirePortableName() {
    require(value.length in 1..100 && value.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "-._~" }) {
        "Android saved game names must contain 1-100 URL-safe ASCII characters"
    }
}
