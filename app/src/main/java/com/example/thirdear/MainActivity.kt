package com.example.thirdear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.thirdear.ui.theme.ThirdEarTheme
import org.json.JSONObject
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
    val sessionManager = remember { SessionManager(context) }
    
    // Vosk State
    var model by remember { mutableStateOf<Model?>(null) }
    var speechService by remember { mutableStateOf<SpeechService?>(null) }
    var isListening by remember { mutableStateOf(false) }
    
    // Transcription State
    var sessionHistory by remember { mutableStateOf("") }
    var partialText by remember { mutableStateOf("") }
    var currentSessionName by remember { mutableStateOf(sessionManager.generateTimestampedName()) }
    
    // UI State
    val scrollState = rememberScrollState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var showSessionMenu by remember { mutableStateOf(false) }
    var sessionList by remember { mutableStateOf(sessionManager.listSessions()) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasPermission = granted }
    )

    // Helper for parsing Vosk JSON
    fun parseHypothesis(hypothesis: String, key: String): String {
        return try {
            val json = JSONObject(hypothesis)
            if (json.has(key)) json.getString(key) else ""
        } catch (e: Exception) { "" }
    }

    // Recognition Logic
    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onPartialResult(hypothesis: String) {
                partialText = parseHypothesis(hypothesis, "partial")
            }

            override fun onResult(hypothesis: String) {
                val text = parseHypothesis(hypothesis, "text")
                if (text.isNotEmpty()) {
                    sessionHistory += (if (sessionHistory.isEmpty()) "" else " ") + text
                    partialText = ""
                    // Auto-save on every result to prevent data loss
                    sessionManager.saveSession(currentSessionName, sessionHistory)
                }
            }

            override fun onFinalResult(hypothesis: String) {
                val text = parseHypothesis(hypothesis, "text")
                if (text.isNotEmpty()) {
                    sessionHistory += (if (sessionHistory.isEmpty()) "" else " ") + text
                }
                partialText = ""
                isListening = false
                sessionManager.saveSession(currentSessionName, sessionHistory)
            }

            override fun onError(e: Exception) {
                println("Vosk Error: ${e.message}")
                isListening = false
            }

            override fun onTimeout() {
                isListening = false
            }
        }
    }

    // Start/Stop Toggle
    fun toggleListening() {
        if (isListening) {
            speechService?.stop()
            speechService = null
            isListening = false
        } else {
            model?.let {
                try {
                    val rec = Recognizer(it, 16000.0f)
                    val service = SpeechService(rec, 16000.0f)
                    service.startListening(recognitionListener)
                    speechService = service
                    isListening = true
                } catch (e: IOException) {
                    println("Vosk: Failed to start recognizer")
                }
            }
        }
    }

    // Auto-scroll when text changes
    LaunchedEffect(sessionHistory, partialText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Initialization
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && model == null) {
            StorageService.unpack(context, "model-en-us", "model",
                { loadedModel -> model = loadedModel },
                { e -> println("Vosk: Unpack failed: ${e.message}") }
            )
        }
    }

    // Layout
    Column(modifier = modifier.fillMaxSize()) {
        // Top Bar / Session Header
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "Logo",
                        modifier = Modifier.size(32.dp).padding(end = 8.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = currentSessionName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            actions = {
                IconButton(onClick = { 
                    sessionList = sessionManager.listSessions()
                    showSessionMenu = true 
                }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Sessions")
                }
                DropdownMenu(
                    expanded = showSessionMenu,
                    onDismissRequest = { showSessionMenu = false }
                ) {
                    Text("Saved Sessions", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
                    sessionList.forEach { session ->
                        DropdownMenuItem(
                            text = { Text(session) },
                            onClick = {
                                // Stop current if listening
                                if (isListening) toggleListening()
                                
                                // Load session
                                currentSessionName = session
                                sessionHistory = sessionManager.loadSession(session)
                                partialText = ""
                                showSessionMenu = false
                            }
                        )
                    }
                }
            }
        )

        // Transcription Area (ScrollView)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Column {
                Text(
                    text = sessionHistory,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (partialText.isNotEmpty()) {
                    Text(
                        text = " $partialText",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Bottom Controls
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Start/Stop
                Button(
                    onClick = { toggleListening() },
                    colors = if (isListening) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) 
                             else ButtonDefaults.buttonColors()
                ) {
                    Text(if (isListening) "Stop Listening" else "Start Listening")
                }

                // New Session
                Button(onClick = {
                    if (isListening) {
                        speechService?.stop()
                        speechService = null
                        isListening = false
                    }
                    // Save current one last time
                    sessionManager.saveSession(currentSessionName, sessionHistory)
                    
                    // Clear and Reset
                    sessionHistory = ""
                    partialText = ""
                    currentSessionName = sessionManager.generateTimestampedName()
                    
                    // Restart automatically
                    toggleListening()
                }) {
                    Text("New")
                }

                // Rename
                Button(onClick = {
                    renameInput = currentSessionName
                    showRenameDialog = true
                }) {
                    Text("Rename")
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Session") },
            text = {
                TextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    sessionManager.renameSession(currentSessionName, renameInput)
                    currentSessionName = renameInput
                    showRenameDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Minimal Opt-in for Experimental APIs
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(title: @Composable () -> Unit, actions: @Composable RowScope.() -> Unit) {
    CenterAlignedTopAppBar(title = title, actions = actions)
}
