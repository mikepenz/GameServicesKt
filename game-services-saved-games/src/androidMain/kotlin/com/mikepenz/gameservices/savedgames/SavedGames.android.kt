package com.mikepenz.gameservices.savedgames

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadata
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Task
import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

public fun createSavedGamesClient(activity: ComponentActivity): SavedGamesClient {
    val selectionResults = Channel<ActivityResult>(Channel.BUFFERED)
    return AndroidSavedGamesClient(
        snapshots = PlayGames.getSnapshotsClient(activity),
        selectionLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            selectionResults.trySend(it)
        },
        selectionResults = selectionResults,
    )
}

private class AndroidSavedGamesClient(
    private val snapshots: SnapshotsClient,
    private val selectionLauncher: ActivityResultLauncher<android.content.Intent>,
    private val selectionResults: Channel<ActivityResult>,
) : SavedGamesClient {
    private val conflicts: MutableMap<String, SnapshotsClient.SnapshotConflict> = mutableMapOf()

    override val isSelectionPresenterSupported: Boolean = true

    override suspend fun listSavedGames(): Result<List<SavedGameMetadata>> = providerResult {
        val buffer = requireNotNull(snapshots.load(false).await().get())
        try {
            (0 until buffer.count).map { buffer.get(it).toMetadata() }
        } finally {
            buffer.release()
        }
    }

    override suspend fun read(id: SavedGameId): Result<SavedGameReadResult> = providerResult {
        if (!contains(id)) return@providerResult SavedGameReadResult.NotFound
        val result = open(id, false)
        if (result.isConflict) {
            SavedGameReadResult.Conflict(requireNotNull(result.conflict).toConflict())
        } else {
            val snapshot = requireNotNull(result.data)
            SavedGameReadResult.Loaded(
                SavedGameVersion(snapshot.metadata.toMetadata(), SavedGameData.of(snapshot.snapshotContents.readFully())),
            )
        }
    }

    override suspend fun write(
        id: SavedGameId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult> = providerResult {
        val result = open(id, true)
        if (result.isConflict) return@providerResult SavedGameWriteResult.Conflict(requireNotNull(result.conflict).toConflict())
        val snapshot = requireNotNull(result.data)
        snapshot.snapshotContents.writeBytes(data.copyBytes())
        SavedGameWriteResult.Saved(
            snapshots.commitAndClose(snapshot, SnapshotMetadataChange.Builder().build()).await().toMetadata(),
        )
    }

    override suspend fun delete(id: SavedGameId): Result<Unit> = providerResult {
        val result = open(id, false)
        check(!result.isConflict) { "Saved game ${id.value} must be resolved before deletion" }
        snapshots.delete(requireNotNull(result.data).metadata).await()
    }

    override suspend fun resolve(
        conflictId: SavedGameConflictId,
        data: SavedGameData,
    ): Result<SavedGameWriteResult> = providerResult {
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
    }

    override suspend fun showSavedGameSelection(): Result<SavedGameMetadata?> = providerResult {
        selectionLauncher.launch(
            snapshots.getSelectSnapshotIntent("Saved games", true, true, SnapshotsClient.DISPLAY_LIMIT_NONE).await(),
        )
        val result = selectionResults.receive()
        if (result.resultCode != Activity.RESULT_OK) return@providerResult null
        SnapshotsClient.getSnapshotFromBundle(requireNotNull(result.data?.extras))?.toMetadata()
    }

    private suspend fun open(
        id: SavedGameId,
        createIfNotFound: Boolean,
    ): SnapshotsClient.DataOrConflict<Snapshot> =
        snapshots.open(id.value, createIfNotFound, SnapshotsClient.RESOLUTION_POLICY_MANUAL).await()

    private suspend fun contains(id: SavedGameId): Boolean {
        val metadata = requireNotNull(snapshots.load(false).await().get())
        try {
            return (0 until metadata.count).any { metadata.get(it).uniqueName == id.value }
        } finally {
            metadata.release()
        }
    }

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

private suspend fun <T> providerResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: GameServicesException) {
    Result.failure(exception)
} catch (exception: Throwable) {
    Result.failure(GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, exception.javaClass.name))
}
