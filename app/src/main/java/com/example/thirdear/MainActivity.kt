package com.example.thirdear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.thirdear.ui.theme.ThirdEarTheme
import kotlinx.coroutines.launch
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
                VoskApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoskApp() {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Vosk State
    var model by remember { mutableStateOf<Model?>(null) }
    var speechService by remember { mutableStateOf<SpeechService?>(null) }
    var isListening by remember { mutableStateOf(false) }
    
    // Transcription State
    var sessionHistory by remember { mutableStateOf("") }
    var partialText by remember { mutableStateOf("") }
    var currentSessionName by remember { mutableStateOf(sessionManager.generateTimestampedName()) }
    
    // UI State
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var sessionList by remember { mutableStateOf(sessionManager.listSessions()) }
    val listState = rememberLazyListState()
    
    // Pinch-to-Zoom State
    var fontSize by remember { mutableStateOf(18f) }
    val minFontSize = 14f
    val maxFontSize = 48f

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

    // Export Logic
    fun exportSession() {
        if (isListening) toggleListening()
        
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, sessionHistory)
            putExtra(Intent.EXTRA_SUBJECT, currentSessionName)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    // Sticky Auto-scroll Logic
    LaunchedEffect(sessionHistory, partialText) {
        // Only scroll if we were at the bottom before the update
        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        val isAtBottom = lastVisibleItem?.index == listState.layoutInfo.totalItemsCount - 1
        
        if (isAtBottom && !listState.isScrollInProgress) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
        }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text("Saved Sessions", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sessionList) { session ->
                        NavigationDrawerItem(
                            label = { Text(session) },
                            selected = session == currentSessionName,
                            onClick = {
                                if (isListening) toggleListening()
                                currentSessionName = session
                                sessionHistory = sessionManager.loadSession(session)
                                partialText = ""
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                // Transcription Area with Pinch-to-Zoom
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 140.dp) // Space for bottom controls
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                fontSize = (fontSize * zoom).coerceIn(minFontSize, maxFontSize)
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    ) {
                        item {
                            Text(
                                text = sessionHistory,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp)
                            )
                            if (partialText.isNotEmpty()) {
                                Text(
                                    text = " $partialText",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                // Bottom Controls Area
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Session Title and Rename Icon (Bottom)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_edit_24),
                                contentDescription = "Rename",
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(20.dp)
                                    .clickable {
                                        renameInput = currentSessionName
                                        showRenameDialog = true
                                    },
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentSessionName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Icons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Sidebar Toggle (Bottom-Left)
                            IconButton(onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) {
                                        sessionList = sessionManager.listSessions()
                                        drawerState.open()
                                    } else {
                                        drawerState.close()
                                    }
                                }
                            }) {
                                Icon(
                                    painter = painterResource(
                                        id = if (drawerState.isClosed) R.drawable.outline_arrow_menu_open_24 
                                             else R.drawable.outline_arrow_menu_close_24
                                    ),
                                    contentDescription = "Menu"
                                )
                            }

                            // Center: Mic / Stop Button
                            Box(contentAlignment = Alignment.Center) {
                                FloatingActionButton(
                                    onClick = { toggleListening() },
                                    containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = if (isListening) "Stop" else "Start"
                                    )
                                }
                            }

                            // Right: New and Export Icons
                            Row {
                                IconButton(onClick = {
                                    if (isListening) toggleListening()
                                    sessionManager.saveSession(currentSessionName, sessionHistory)
                                    sessionHistory = ""
                                    partialText = ""
                                    currentSessionName = sessionManager.generateTimestampedName()
                                    toggleListening()
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "New Session")
                                }
                                IconButton(onClick = { exportSession() }) {
                                    Icon(Icons.Default.Share, contentDescription = "Export")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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
