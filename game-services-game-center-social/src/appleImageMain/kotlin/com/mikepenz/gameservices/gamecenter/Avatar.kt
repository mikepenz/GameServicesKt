@file:OptIn(com.mikepenz.gameservices.InternalGameServicesApi::class)

package com.mikepenz.gameservices.gamecenter

import com.mikepenz.gameservices.*

import platform.UIKit.*
import platform.GameKit.*
import platform.Foundation.NSData
import platform.posix.memcpy
import kotlinx.cinterop.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun GKPlayer.loadAvatarBytes(): ByteArray? = suspendCancellableCoroutine { continuation ->
    loadPhotoForSize(GKPhotoSizeNormal) { image, error ->
        if (continuation.isActive) {
            if (error != null) continuation.resumeWith(Result.failure(error.toGameServicesException()))
            else continuation.resumeWith(runCatching {
                image?.let { image ->
                    val data = requireNotNull(UIImagePNGRepresentation(image))
                    require(data.length <= Int.MAX_VALUE.toULong()) { "Avatar exceeds Kotlin array size" }
                    ByteArray(data.length.toInt()).also { output ->
                        if (output.isNotEmpty()) output.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                    }
                }
            })
        }
    }
}
