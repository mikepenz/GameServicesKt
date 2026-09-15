package com.mikepenz.gameservices.savedgames

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadata
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Task
import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

public fun createSavedGamesClient(activity: ComponentActivity): SavedGamesClient = AndroidSavedGamesClient(activity)

private class AndroidSavedGamesClient(
    activity: ComponentActivity,
) : SavedGamesClient {
    private val snapshots: SnapshotsClient = PlayGames.getSnapshotsClient(activity)
    private val conflicts: MutableMap<String, SnapshotsClient.SnapshotConflict> = mutableMapOf()
    private val selectionResults = Channel<androidx.activity.result.ActivityResult>(Channel.BUFFERED)
    private val selectionLauncher = activity.activityResultRegistry.register(
        "game-services-saved-games-${hashCode()}",
        activity,
        ActivityResultContracts.StartActivityForResult(),
    ) { selectionResults.trySend(it) }

    override val isSelectionPresenterSupported: Boolean = true

    override suspend fun listSavedGames(): Result<List<SavedGameMetadata>> = runCatching {
        val buffer = requireNotNull(snapshots.load(false).await().get())
        try {
            (0 until buffer.count).map { buffer.get(it).toMetadata() }
        } finally {
            buffer.release()
        }
    }.asProviderResult()

    override suspend fun read(id: SavedGameId): Result<SavedGameReadResult> = open(id, false).mapCatching { result ->
        if (result.isConflict) {
            SavedGameReadResult.Conflict(requireNotNull(result.conflict).toConflict())
        } else {
            val snapshot = requireNotNull(result.data)
            SavedGameReadResult.Loaded(
                SavedGameVersion(snapshot.metadata.toMetadata(), SavedGameData.of(snapshot.snapshotContents.readFully())),
            )
        }
    }.asProviderResult()

    override suspend fun write(
        id: SavedGameId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult> = open(id, true).mapCatching { result ->
        if (result.isConflict) return@mapCatching SavedGameWriteResult.Conflict(requireNotNull(result.conflict).toConflict())
        val snapshot = requireNotNull(result.data)
        snapshot.snapshotContents.writeBytes(data.copyBytes())
        SavedGameWriteResult.Saved(
            snapshots.commitAndClose(snapshot, SnapshotMetadataChange.Builder().build()).await().toMetadata(),
        )
    }.asProviderResult()

    override suspend fun delete(id: SavedGameId): Result<Unit> = open(id, false).mapCatching { result ->
        check(!result.isConflict) { "Saved game ${id.value} must be resolved before deletion" }
        snapshots.delete(requireNotNull(result.data).metadata).await()
        Unit
    }.asProviderResult()

    override suspend fun resolve(
        conflictId: SavedGameConflictId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult> = runCatching {
        val conflict = requireNotNull(conflicts.remove(conflictId.value)) { "Unknown saved game conflict" }
        val contents = conflict.resolutionSnapshotContents
        contents.writeBytes(data.copyBytes())
        val result = snapshots.resolveConflict(
            conflictId.value,
            conflict.snapshot.metadata.snapshotId,
            SnapshotMetadataChange.Builder().build(),
            contents,
        ).await()
        if (result.isConflict) {
            SavedGameWriteResult.Conflict(requireNotNull(result.conflict).toConflict())
        } else {
            SavedGameWriteResult.Saved(requireNotNull(result.data).metadata.toMetadata())
        }
    }.asProviderResult()

    override suspend fun showSavedGameSelection(): Result<SavedGameMetadata?> = runCatching {
        selectionLauncher.launch(
            snapshots.getSelectSnapshotIntent("Saved games", true, true, SnapshotsClient.DISPLAY_LIMIT_NONE).await(),
        )
        val result = selectionResults.receive()
        if (result.resultCode != Activity.RESULT_OK) return@runCatching null
        SnapshotsClient.getSnapshotFromBundle(requireNotNull(result.data?.extras))?.toMetadata()
    }.asProviderResult()

    private suspend fun open(
        id: SavedGameId,
        createIfNotFound: Boolean,
    ): Result<SnapshotsClient.DataOrConflict<Snapshot>> = runCatching {
        snapshots.open(id.value, createIfNotFound, SnapshotsClient.RESOLUTION_POLICY_MANUAL).await()
    }.asProviderResult()

    private fun SnapshotsClient.SnapshotConflict.toConflict(): SavedGameConflict {
        conflicts[conflictId] = this
        return SavedGameConflict(
            id = SavedGameConflictId(conflictId),
            versions = listOf(snapshot, conflictingSnapshot).map { version ->
                SavedGameVersion(
                    version.metadata.toMetadata(),
                    SavedGameData.of(version.snapshotContents.readFully()),
                )
            },
        )
    }

    private fun SnapshotMetadata.toMetadata(): SavedGameMetadata = SavedGameMetadata(
        id = SavedGameId(uniqueName),
        name = uniqueName,
    )
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWith(Result.failure(task.exception ?: IllegalStateException("Play Games task failed")))
        }
    }
}

private fun <T> Result<T>.asProviderResult(): Result<T> = fold(
    onSuccess = Result.Companion::success,
    onFailure = { throwable ->
        Result.failure(
            if (throwable is GameServicesException) throwable else {
                GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, throwable.javaClass.name)
            },
        )
    },
)
