package com.mikepenz.gameservices.sample.host

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mikepenz.gameservices.sample.GameServicesSampleApp
import com.mikepenz.gameservices.sample.createGameServicesSample

public class GameServicesValidationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val sample = createGameServicesSample(this)
        setContent(parent = null) { GameServicesSampleApp(sample) }
    }
}
