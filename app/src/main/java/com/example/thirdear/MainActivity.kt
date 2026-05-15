package com.example.thirdear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.thirdear.ui.theme.ThirdEarTheme
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThirdEarTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VoskScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun VoskScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var model by remember { mutableStateOf<Model?>(null) }
    var speechService by remember { mutableStateOf<SpeechService?>(null) }
    var resultText by remember { mutableStateOf("Ready to listen...") }
    var partialText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasPermission = granted }
    )

    val recognitionListener = object : RecognitionListener {
        private fun parseHypothesis(hypothesis: String): String {
            return try {
                // Vosk returns JSON like {"text": "hello world"} or {"partial": "hello"}
                val json = org.json.JSONObject(hypothesis)
                if (json.has("text")) json.getString("text")
                else if (json.has("partial")) json.getString("partial")
                else ""
            } catch (e: Exception) {
                println("Vosk: Error parsing JSON: ${e.message}")
                ""
            }
        }

        override fun onPartialResult(hypothesis: String) {
            val text = parseHypothesis(hypothesis)
            if (text.isNotEmpty()) {
                partialText = text
            }
        }

        override fun onResult(hypothesis: String) {
            val text = parseHypothesis(hypothesis)
            if (text.isNotEmpty()) {
                resultText = text
                partialText = ""
            }
        }

        override fun onFinalResult(hypothesis: String) {
            val text = parseHypothesis(hypothesis)
            if (text.isNotEmpty()) {
                resultText = text
            }
            partialText = ""
            isListening = false
        }

        override fun onError(exception: Exception) {
            resultText = "Error: ${exception.message}"
            isListening = false
        }

        override fun onTimeout() {
            isListening = false
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && model == null) {
            println("Vosk: Permission granted, starting model unpack...")
            StorageService.unpack(context, "model-en-us", "model",
                { loadedModel ->
                    println("Vosk: Model unpacked successfully!")
                    model = loadedModel
                },
                { exception ->
                    println("Vosk: Failed to unpack model: ${exception.message}")
                    resultText = "Failed to unpack model: ${exception.message}"
                }
            )
        } else if (!hasPermission) {
            println("Vosk: Waiting for microphone permission...")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechService?.stop()
            speechService?.shutdown()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (model == null) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading model...")
        } else {
            Text(
                text = "Full Result:",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = resultText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Partial:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = partialText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (isListening) {
                        speechService?.stop()
                        speechService = null
                        isListening = false
                    } else {
                        try {
                            val rec = Recognizer(model, 16000.0f)
                            val service = SpeechService(rec, 16000.0f)
                            service.startListening(recognitionListener)
                            speechService = service
                            isListening = true
                        } catch (e: IOException) {
                            resultText = "Failed to start recognizer: ${e.message}"
                        }
                    }
                }
            ) {
                Text(if (isListening) "Stop Listening" else "Start Listening")
            }
        }
    }
}
