@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.social

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.common.data.DataBufferUtils
import com.google.android.gms.common.images.ImageManager
import com.google.android.gms.games.FriendsResolutionRequiredException
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.Player
import com.google.android.gms.games.PlayerBuffer
import com.google.android.gms.games.PlayersClient
import com.mikepenz.gameservices.GameServicesException
import com.mikepenz.gameservices.PlayerId
import com.mikepenz.gameservices.PlayerIdentity
import com.mikepenz.gameservices.ProviderUiRequest
import com.mikepenz.gameservices.awaitGameServices
import com.mikepenz.gameservices.gameServicesResult
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public fun createSocialClient(activity: ComponentActivity): SocialClient {
    val consentResults = ProviderUiRequest<ActivityResult>()
    val consentLauncher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        consentResults.complete(it)
    }
    activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) consentResults.close()
    })
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
    private val consentResults: ProviderUiRequest<ActivityResult>,
    private val launchConsent: (IntentSenderRequest) -> Unit,
    private val launchProfile: (android.content.Intent) -> Unit,
) : SocialClient {
    private val friendsPaging = Mutex()
    private val mutableFriendsAccessState = MutableStateFlow(FriendsAccessState.Unknown)
    override val isSupported: Boolean = true
    override val friendsAccessState: StateFlow<FriendsAccessState> = mutableFriendsAccessState

    override suspend fun requestFriendsAccess(): Result<FriendsAccessState> = gameServicesResult {
        when (mutableFriendsAccessState.value) {
            FriendsAccessState.Denied,
            FriendsAccessState.Restricted,
            -> return@gameServicesResult mutableFriendsAccessState.value
            else -> Unit
        }
        try {
            friendsPaging.withLock { loadFriendsBuffer().release() }
            FriendsAccessState.Granted.also { mutableFriendsAccessState.value = it }
        } catch (exception: FriendsResolutionRequiredException) {
            mutableFriendsAccessState.value = FriendsAccessState.ConsentRequired
            val result = withContext(Dispatchers.Main.immediate) {
                consentResults.launchAndAwait { launchConsent(IntentSenderRequest.Builder(exception.resolution.intentSender).build()) }
            }
            if (result.resultCode != Activity.RESULT_OK) {
                FriendsAccessState.Denied.also { mutableFriendsAccessState.value = it }
            } else {
                friendsPaging.withLock { loadFriendsBuffer().release() }
                FriendsAccessState.Granted.also { mutableFriendsAccessState.value = it }
            }
        }
    }

    override suspend fun loadFriends(): Result<List<PlayerProfile>> = gameServicesResult {
        try {
            friendsPaging.withLock {
                var buffer = loadFriendsBuffer()
                var first = true
                try {
                    collectFriends {
                        if (!first) {
                            val next = requireNotNull(players.loadMoreFriends(FRIENDS_PAGE_SIZE)
                                .awaitGameServices { it.get()?.release() }.get())
                            buffer.release()
                            buffer = next
                        }
                        first = false
                        FriendPage((0 until buffer.count).map { buffer.get(it).toProfile() }, DataBufferUtils.hasNextPage(buffer))
                    }
                } finally {
                    buffer.release()
                }
            }.also { mutableFriendsAccessState.value = FriendsAccessState.Granted }
        } catch (exception: FriendsResolutionRequiredException) {
            mutableFriendsAccessState.value = FriendsAccessState.ConsentRequired
            throw GameServicesException.PermissionRequired
        }
    }

    override suspend fun loadAvatar(playerId: PlayerId): Result<AvatarBytes?> = gameServicesResult {
        val player = requireNotNull(players.loadPlayer(playerId.value, false).awaitGameServices().get())
        val uri = player.hiResImageUri ?: player.iconImageUri ?: return@gameServicesResult null
        val drawable = withContext(Dispatchers.Main.immediate) { images.load(uri) }
        withContext(Dispatchers.Default) { AvatarBytes.of(drawable.toPng()) }
    }

    override suspend fun showPlayerProfile(playerId: PlayerId): Result<Unit> = gameServicesResult {
        withContext(Dispatchers.Main.immediate) { launchProfile(players.getCompareProfileIntent(playerId.value).awaitGameServices()) }
    }

    private suspend fun loadFriendsBuffer(): PlayerBuffer = requireNotNull(players.loadFriends(FRIENDS_PAGE_SIZE, false).awaitGameServices { it.get()?.release() }.get())

    private fun Player.toProfile(): PlayerProfile = PlayerProfile(
        PlayerIdentity(PlayerId(playerId), displayName),
    )

    private companion object {
        const val FRIENDS_PAGE_SIZE: Int = 100
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
