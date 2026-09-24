@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.playgames

import com.mikepenz.gameservices.savedgames.*

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.GamesClientStatusCodes
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotContents
import com.google.android.gms.games.snapshot.SnapshotMetadata
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.mikepenz.gameservices.ProviderUiRequest
import com.mikepenz.gameservices.playgames.awaitGameServices
import com.mikepenz.gameservices.playgames.gameServicesResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public fun PlayGamesBackend.createSavedGamesClient(): SavedGamesClient {
    val selection = ProviderUiRequest<ActivityResult>()
    activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) selection.close()
    })
    val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult(), selection::complete)
    return AndroidSavedGamesClient(PlayGames.getSnapshotsClient(activity), launcher::launch, selection)
}

internal class AndroidSavedGamesClient(
    private val snapshots: SnapshotsClient,
    private val selectionLauncher: (android.content.Intent) -> Unit,
    private val selection: ProviderUiRequest<ActivityResult>,
) : SavedGamesClient {
    // Retain names, never open native files. Reopen and verify the conflict before resolving.
    private val conflicts = mutableMapOf<String, SavedGameId>()
    private val operations = Mutex()
    private var maxDataSize: Int? = null
    override val isSupported: Boolean = true
    override val isSelectionPresenterSupported: Boolean = true

    override suspend fun listSavedGames(): Result<List<SavedGameMetadata>> = gameServicesResult { metadata() }

    override suspend fun read(id: SavedGameId): Result<SavedGameReadResult> = gameServicesResult {
        operations.withLock {
            val opened = try {
                open(id, false)
            } catch (error: ApiException) {
                if (error.statusCode == GamesClientStatusCodes.SNAPSHOT_NOT_FOUND) return@withLock SavedGameReadResult.NotFound
                throw error
            }
            try {
                if (opened.isConflict) SavedGameReadResult.Conflict(requireNotNull(opened.conflict).toConflict())
                else SavedGameReadResult.Loaded(requireNotNull(opened.data).toVersion())
            } finally {
                close(opened)
            }
        }
    }

    override suspend fun write(id: SavedGameId, data: SavedGameData): Result<SavedGameWriteResult> = gameServicesResult {
        operations.withLock {
            id.requirePortableName()
            val bytes = checkedBytes(data)
            val opened = open(id, true)
            try {
                if (opened.isConflict) return@withLock SavedGameWriteResult.Conflict(requireNotNull(opened.conflict).toConflict())
                val snapshot = requireNotNull(opened.data)
                writeAndCommit(
                    write = { withContext(Dispatchers.IO) { snapshot.snapshotContents.writeBytes(bytes) } },
                    commit = {
                        withContext(NonCancellable) {
                            SavedGameWriteResult.Saved(snapshots.commitAndClose(snapshot, SnapshotMetadataChange.EMPTY_CHANGE)
                                .awaitGameServices().toMetadata())
                        }
                    },
                )
            } finally {
                close(opened)
            }
        }
    }

    override suspend fun delete(id: SavedGameId): Result<Unit> = gameServicesResult {
        operations.withLock {
            val opened = open(id, false)
            try {
                check(!opened.isConflict) { "Read and resolve the saved game conflict before deletion" }
                snapshots.delete(requireNotNull(opened.data).metadata).awaitGameServices()
                conflicts.entries.removeAll { it.value == id }
            } finally {
                close(opened)
            }
        }
    }

    override suspend fun resolve(conflictId: SavedGameConflictId, data: SavedGameData): Result<SavedGameWriteResult> = gameServicesResult {
        operations.withLock {
            val id = requireNotNull(conflicts[conflictId.value]) { "Unknown conflict; read the saved game again" }
            val bytes = checkedBytes(data)
            val opened = open(id, false)
            try {
                check(opened.isConflict) { "Conflict is no longer current; read the saved game again" }
                val conflict = requireNotNull(opened.conflict)
                if (conflict.conflictId != conflictId.value) {
                    return@withLock SavedGameWriteResult.Conflict(conflict.toConflict())
                }
                withContext(NonCancellable) {
                    val resolved = writeAndCommit(
                        write = { withContext(Dispatchers.IO) { conflict.resolutionSnapshotContents.writeBytes(bytes) } },
                        commit = {
                            snapshots.resolveConflict(conflict.conflictId, conflict.snapshot.metadata.snapshotId,
                                SnapshotMetadataChange.EMPTY_CHANGE, conflict.resolutionSnapshotContents)
                                .awaitGameServices(::discard)
                        },
                    )
                    try {
                        if (resolved.isConflict) SavedGameWriteResult.Conflict(requireNotNull(resolved.conflict).toConflict())
                        else SavedGameWriteResult.Saved(requireNotNull(resolved.data).metadata.toMetadata()).also {
                            conflicts.remove(conflictId.value)
                        }
                    } finally {
                        close(resolved)
                    }
                }
            } finally {
                close(opened)
            }
        }
    }

    override suspend fun showSavedGameSelection(): Result<SavedGameMetadata?> = gameServicesResult {
        val intent = snapshots.getSelectSnapshotIntent("Saved games", false, true, SnapshotsClient.DISPLAY_LIMIT_NONE).awaitGameServices()
        val result = withContext(Dispatchers.Main.immediate) { selection.launchAndAwait { selectionLauncher(intent) } }
        if (result.resultCode != Activity.RESULT_OK) null
        else result.data?.extras?.let(SnapshotsClient::getSnapshotFromBundle)?.toMetadata()
    }

    private suspend fun metadata(): List<SavedGameMetadata> {
        val buffer = requireNotNull(snapshots.load(false).awaitGameServices { it.get()?.release() }.get())
        return try { (0 until buffer.count).map { buffer.get(it).toMetadata() } } finally { buffer.release() }
    }

    private suspend fun checkedBytes(data: SavedGameData): ByteArray {
        val maximum = maxDataSize ?: snapshots.maxDataSize.awaitGameServices().also { maxDataSize = it }
        return data.copyBytes().also { require(it.size <= maximum) { "Saved game exceeds the provider limit of $maximum bytes" } }
    }

    private suspend fun open(id: SavedGameId, create: Boolean): SnapshotsClient.DataOrConflict<Snapshot> {
        id.requirePortableName()
        return snapshots.open(id.value, create, SnapshotsClient.RESOLUTION_POLICY_MANUAL).awaitGameServices(::discard)
    }

    private fun SnapshotsClient.DataOrConflict<Snapshot>.versions(): List<Snapshot> =
        if (isConflict) requireNotNull(conflict).let { listOf(it.snapshot, it.conflictingSnapshot) }
        else listOfNotNull(data)

    private fun Snapshot.hasOpenContents(): Boolean {
        // SDK 22 annotates this non-null, but SnapshotEntity returns null after close.
        val contents: SnapshotContents? = snapshotContents
        return contents?.isClosed == false
    }

    private fun discard(opened: SnapshotsClient.DataOrConflict<Snapshot>) {
        opened.versions().filter { it.hasOpenContents() }.forEach { snapshot ->
            runCatching { snapshots.discardAndClose(snapshot) }
        }
        runCatching { opened.closeResolutionContents() }
    }

    private suspend fun close(opened: SnapshotsClient.DataOrConflict<Snapshot>) = withContext(NonCancellable) {
        var failure: Exception? = null
        for (snapshot in opened.versions().filter { it.hasOpenContents() }) {
            try {
                snapshots.discardAndClose(snapshot).awaitGameServices()
            } catch (error: Exception) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        try {
            opened.closeResolutionContents()
        } catch (error: Exception) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        failure?.let { throw it }
    }

    private fun SnapshotsClient.DataOrConflict<Snapshot>.closeResolutionContents() {
        if (isConflict) conflict?.resolutionSnapshotContents?.let { contents ->
            if (!contents.isClosed) contents.parcelFileDescriptor.close()
        }
    }

    private suspend fun Snapshot.toVersion(): SavedGameVersion = SavedGameVersion(metadata.toMetadata(),
        withContext(Dispatchers.IO) { SavedGameData.of(snapshotContents.readFully()) })

    private suspend fun SnapshotsClient.SnapshotConflict.toConflict(): SavedGameConflict {
        val id = SavedGameId(snapshot.metadata.uniqueName)
        val versions = listOf(snapshot.toVersion(), conflictingSnapshot.toVersion())
        conflicts.entries.removeAll { it.value == id }
        conflicts[conflictId] = id
        return SavedGameConflict(SavedGameConflictId(conflictId), versions)
    }

    private fun SnapshotMetadata.toMetadata(): SavedGameMetadata = SavedGameMetadata(SavedGameId(uniqueName), uniqueName)
}
