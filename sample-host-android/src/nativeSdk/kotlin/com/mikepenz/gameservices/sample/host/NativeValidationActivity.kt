package com.mikepenz.gameservices.sample.host

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

public class NativeValidationActivity : ComponentActivity() {
    private var session: Long = 0
    private var closing by mutableStateOf(false)
    private var status by mutableStateOf("Open the C SDK backend to start testing.")
    private var achievementId by mutableStateOf("")
    private var progress by mutableStateOf("100")

    private external fun nativeOpen(): Long
    private external fun nativeRequest(session: Long, operation: Int, achievementId: String, progress: Int)
    private external fun nativeClose(session: Long)

    public fun onNativeResult(success: Boolean, message: String) {
        runOnUiThread { status = if (success) message else "Failed: $message" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Play Games C SDK", style = MaterialTheme.typography.headlineSmall)
                    Text(status)
                    Button(onClick = {
                        try { session = nativeOpen(); status = "Native backend opened" }
                        catch (error: Exception) { status = "Open failed: ${error.message}" }
                    }, enabled = session == 0L && !closing) { Text("Open backend") }
                    Button(onClick = { request(0) }, enabled = session != 0L) { Text("Check authentication") }
                    Button(onClick = { request(1) }, enabled = session != 0L) { Text("Sign in") }
                    Button(onClick = { request(2) }, enabled = session != 0L) { Text("Load achievements") }
                    OutlinedTextField(achievementId, { achievementId = it }, label = { Text("Achievement ID") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(progress, { progress = it }, label = { Text("Progress percent (0–100)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    Button(onClick = { request(3) }, enabled = session != 0L && achievementId.isNotBlank() && progress.toIntOrNull()?.let { it in 0..100 } == true) {
                        Text("Report progress")
                    }
                    Button(onClick = { request(4) }, enabled = session != 0L) { Text("Show achievements") }
                    Button(onClick = { request(5) }, enabled = session != 0L) { Text("Request Recall session") }
                    Button(onClick = ::closeBackend, enabled = session != 0L) { Text("Close backend") }
                }
            }
        }
    }

    private fun request(operation: Int) {
        status = "Waiting for C SDK callback…"
        nativeRequest(session, operation, achievementId, progress.toIntOrNull() ?: 100)
    }

    private fun closeBackend() {
        val closingSession = session
        session = 0
        closing = true
        Thread {
            nativeClose(closingSession)
            runOnUiThread { closing = false; status = "Native backend closed" }
        }.start()
    }

    override fun onDestroy() {
        val closing = session
        session = 0
        if (closing != 0L) Thread { nativeClose(closing) }.start()
        super.onDestroy()
    }

    private companion object {
        init {
            System.loadLibrary("gs_play_validation")
            System.loadLibrary("gs_native_validation_jni")
        }
    }
}
