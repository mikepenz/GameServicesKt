package com.mikepenz.gameservices.social

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.images.ImageManager
import com.google.android.gms.games.FriendsResolutionRequiredException
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.Player
import com.google.android.gms.games.PlayerBuffer
import com.google.android.gms.games.PlayersClient
import com.google.android.gms.tasks.Task
import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.GameServicesProvider
import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.PlayerIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

public fun createSocialClient(activity: ComponentActivity): SocialClient {
    val consentResults = Channel<ActivityResult>(Channel.BUFFERED)
    val consentLauncher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        consentResults.trySend(it)
    }
    val profileLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    return AndroidSocialClient(
        players = PlayGames.getPlayersClient(activity),
        images = ImageManager.create(activity),
        consentResults = consentResults,
        launchConsent = consentLauncher::launch,
        launchProfile = profileLauncher::launch,
    )
}

private class AndroidSocialClient(
    private val players: PlayersClient,
    private val images: ImageManager,
    private val consentResults: Channel<ActivityResult>,
    private val launchConsent: (IntentSenderRequest) -> Unit,
    private val launchProfile: (android.content.Intent) -> Unit,
) : SocialClient {
    private val mutableFriendsAccessState = MutableStateFlow(FriendsAccessState.Unknown)
    override val isSupported: Boolean = true
    override val friendsAccessState: StateFlow<FriendsAccessState> = mutableFriendsAccessState

    override suspend fun requestFriendsAccess(): Result<FriendsAccessState> = providerResult {
        when (mutableFriendsAccessState.value) {
            FriendsAccessState.Denied,
            FriendsAccessState.Restricted,
            -> return@providerResult mutableFriendsAccessState.value
            else -> Unit
        }
        try {
            loadFriendsBuffer().release()
            FriendsAccessState.Granted.also { mutableFriendsAccessState.value = it }
        } catch (exception: FriendsResolutionRequiredException) {
            mutableFriendsAccessState.value = FriendsAccessState.ConsentRequired
            launchConsent(IntentSenderRequest.Builder(exception.resolution.intentSender).build())
            if (consentResults.receive().resultCode != Activity.RESULT_OK) {
                FriendsAccessState.Denied.also { mutableFriendsAccessState.value = it }
            } else {
                loadFriendsBuffer().release()
                FriendsAccessState.Granted.also { mutableFriendsAccessState.value = it }
            }
        }
    }

    override suspend fun loadFriends(): Result<List<PlayerProfile>> = providerResult {
        try {
            loadFriendsBuffer().use { buffer ->
                (0 until buffer.count).map { buffer.get(it).toProfile() }
            }.also { mutableFriendsAccessState.value = FriendsAccessState.Granted }
        } catch (exception: FriendsResolutionRequiredException) {
            mutableFriendsAccessState.value = FriendsAccessState.ConsentRequired
            throw GameServicesException.PermissionRequired
        }
    }

    override suspend fun loadAvatar(playerId: PlayerId): Result<AvatarBytes?> = providerResult {
        val player = requireNotNull(players.loadPlayer(playerId.value, false).await().get())
        val uri = player.hiResImageUri ?: player.iconImageUri ?: return@providerResult null
        AvatarBytes.of(images.load(uri).toPng())
    }

    override suspend fun showPlayerProfile(playerId: PlayerId): Result<Unit> = providerResult {
        launchProfile(players.getCompareProfileIntent(playerId.value).await())
    }

    private suspend fun loadFriendsBuffer(): PlayerBuffer = requireNotNull(players.loadFriends(FRIENDS_PAGE_SIZE, false).await().get())

    private fun Player.toProfile(): PlayerProfile = PlayerProfile(
        PlayerIdentity(PlayerId(playerId), displayName),
    )

    private inline fun PlayerBuffer.use(block: (PlayerBuffer) -> List<PlayerProfile>): List<PlayerProfile> = try {
        block(this)
    } finally {
        release()
    }

    private companion object {
        const val FRIENDS_PAGE_SIZE: Int = 100
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (continuation.isActive) {
            if (task.isSuccessful) continuation.resume(task.result)
            else continuation.resumeWith(Result.failure(task.exception ?: IllegalStateException("Play Games task failed")))
        }
    }
}

private suspend fun ImageManager.load(uri: android.net.Uri): Drawable = suspendCancellableCoroutine { continuation ->
    loadImage({ _, drawable, _ ->
        if (continuation.isActive) {
            if (drawable != null) continuation.resume(drawable)
            else continuation.resumeWith(Result.failure(IllegalStateException("Play Games image is unavailable")))
        }
    }, uri)
}

private fun Drawable.toPng(): ByteArray = ByteArrayOutputStream().use { output ->
    val bitmap = (this as? BitmapDrawable)?.bitmap ?: Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        setBounds(0, 0, bitmap.width, bitmap.height)
        draw(Canvas(bitmap))
    }
    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
    output.toByteArray()
}

private suspend fun <T> providerResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (exception: GameServicesException) {
    Result.failure(exception)
} catch (exception: ApiException) {
    Result.failure(GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, exception.statusCode.toString()))
} catch (exception: Throwable) {
    Result.failure(GameServicesException.ProviderFailure(GameServicesProvider.GooglePlayGames, exception.javaClass.name))
}
