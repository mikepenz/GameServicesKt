package com.mikepenz.gameservices.sample.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.mikepenz.gameservices.gamecenter.GameCenterBackend
import com.mikepenz.gameservices.sample.GameServicesSampleApp
import com.mikepenz.gameservices.sample.createGameServicesSample

fun main() = application {
    val backend = remember { GameCenterBackend() }
    DisposableEffect(backend) { onDispose { backend.close() } }
    val sample = remember(backend) { createGameServicesSample(backend) }
    Window(onCloseRequest = ::exitApplication, title = "GameServicesKt · Game Center") {
        GameServicesSampleApp(sample)
    }
}
