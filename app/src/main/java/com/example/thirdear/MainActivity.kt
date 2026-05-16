package com.example.thirdear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
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

class CurvedNotchShape(private val notchRadius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radiusPx = with(density) { notchRadius.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f)
            val curveWidth = radiusPx * 1.8f
            val notchStart = size.width / 2 - radiusPx
            val notchEnd = size.width / 2 + radiusPx
            
            lineTo(notchStart - curveWidth / 2, 0f)
            
            cubicTo(
                x1 = notchStart, y1 = 0f,
                x2 = notchStart, y2 = radiusPx * 1.2f,
                x3 = size.width / 2, y3 = radiusPx * 1.2f
            )
            cubicTo(
                x1 = notchEnd, y1 = radiusPx * 1.2f,
                x2 = notchEnd, y2 = 0f,
                x3 = notchEnd + curveWidth / 2, y3 = 0f
            )
            
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoskApp() {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }
    val sessionDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val settingsDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
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
    var sortType by remember { mutableStateOf(sessionManager.loadSortType()) }
    var sessionList by remember { mutableStateOf(sessionManager.listSessions(sortType)) }
    val listState = rememberLazyListState()
    
    // Settings State
    var fontSize by remember { mutableFloatStateOf(sessionManager.loadFontSize()) }
    
    val minFontSize = 14f
    val maxFontSize = 48f
    
    // Deletion State
    var sessionToDelete by remember { mutableStateOf<String?>(null) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasPermission = granted }
    )

    // Keep Screen On logic
    DisposableEffect(isListening) {
        if (isListening) {
            (context as? ComponentActivity)?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            (context as? ComponentActivity)?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {}
    }

    // Helper for parsing Vosk JSON
    fun parseHypothesis(hypothesis: String, key: String): String {
        return try {
            val json = JSONObject(hypothesis)
            if (json.has(key)) json.getString(key) else ""
        } catch (e: Exception) { 
            "" 
        }
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
                    sessionManager.saveSession(currentSessionName, sessionHistory)
                }
                partialText = ""
                isListening = false
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
    fun stopListening() {
        speechService?.stop()
        speechService = null
        isListening = false
    }

    fun toggleListening() {
        if (isListening) {
            stopListening()
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

    // MAGNETIC Sticky Auto-scroll Logic
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf true
            
            val lastItem = visibleItems.lastOrNull() ?: return@derivedStateOf true
            
            // Calculate distance to the physical bottom of the list
            val lastItemBottom = lastItem.offset + lastItem.size
            val viewportBottom = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
            val distanceToBottom = lastItemBottom - viewportBottom
            
            val thresholdPx = with(context.resources.displayMetrics) { 50 * density } // 50dp threshold
            val atLastIndex = lastItem.index == layoutInfo.totalItemsCount - 1
            
            atLastIndex && distanceToBottom <= thresholdPx
        }
    }

    LaunchedEffect(sessionHistory, partialText) {
        if (isNearBottom) {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems > 0) {
                listState.animateScrollToItem(index = totalItems - 1)
            }
        }
    }

    // Initialization & Persistence
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        
        // Load latest session on launch
        sessionManager.getLatestSessionName()?.let { latest ->
            currentSessionName = latest
            sessionHistory = sessionManager.loadSession(latest)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && model == null) {
            StorageService.unpack(context, "model-en-us", "model",
                { loadedModel -> model = loadedModel },
                { e -> println("Vosk: Unpack failed: ${e.message}") }
            )
        }
    }

    // ModalNavigationDrawer at the TOP level to ensure it catches edge-swipes
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        ModalNavigationDrawer(
            drawerState = sessionDrawerState,
            gesturesEnabled = true,
            drawerContent = {
                ModalDrawerSheet {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Saved Sessions", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { scope.launch { sessionDrawerState.close() } }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                        
                        // Sort Dropdown
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Sort: $sortType")
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                listOf("A to Z", "Most Recent", "Oldest to newest").forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = {
                                            sortType = type
                                            sessionManager.saveSortType(type)
                                            sessionList = sessionManager.listSessions(type)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sessionList) { session ->
                            NavigationDrawerItem(
                                label = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(session, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { sessionToDelete = session }) {
                                            Icon(Icons.Default.Delete, "Delete", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                },
                                selected = session == currentSessionName,
                                onClick = {
                                    if (isListening) toggleListening()
                                    currentSessionName = session
                                    sessionHistory = sessionManager.loadSession(session)
                                    partialText = ""
                                    scope.launch { sessionDrawerState.close() }
                                },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                            )
                        }
                    }
                }
            }
        ) {
            // Nested Right-aligned drawer for Settings
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModalNavigationDrawer(
                    drawerState = settingsDrawerState,
                    drawerContent = {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Settings", style = MaterialTheme.typography.titleMedium)
                                    IconButton(onClick = { scope.launch { settingsDrawerState.close() } }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close")
                                    }
                                }
                                HorizontalDivider()
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Text Size", fontWeight = FontWeight.Bold)
                                    Text("Size: ${fontSize.toInt()} sp", style = MaterialTheme.typography.labelMedium)
                                    Slider(
                                        value = fontSize,
                                        onValueChange = { 
                                            fontSize = it
                                            sessionManager.saveFontSize(it)
                                        },
                                        valueRange = minFontSize..maxFontSize
                                    )
                                }
                            }
                        }
                    }
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Scaffold { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .padding(innerPadding)
                                    .fillMaxSize()
                            ) {
                                // Transcription Area
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            var totalDrag = 0f
                                            detectHorizontalDragGestures(
                                                onDragStart = { totalDrag = 0f },
                                                onHorizontalDrag = { _, dragAmount ->
                                                    totalDrag += dragAmount
                                                    if (totalDrag > 100f && sessionDrawerState.isClosed) {
                                                        scope.launch {
                                                            sessionList = sessionManager.listSessions(sortType)
                                                            sessionDrawerState.open()
                                                        }
                                                        totalDrag = 0f // Reset after opening
                                                    } else if (totalDrag < -100f && settingsDrawerState.isClosed) {
                                                        scope.launch {
                                                            settingsDrawerState.open()
                                                        }
                                                        totalDrag = 0f
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp)
                                    ) {
                                        item {
                                            Spacer(Modifier.height(16.dp))
                                            if (sessionHistory.isNotEmpty()) {
                                                SelectionContainer {
                                                    Text(
                                                        text = sessionHistory,
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            fontSize = fontSize.sp,
                                                            lineHeight = (fontSize * 1.4f).sp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        if (partialText.isNotEmpty()) {
                                            item {
                                                SelectionContainer {
                                                    Text(
                                                        text = " $partialText",
                                                        style = MaterialTheme.typography.bodyLarge.copy(
                                                            fontSize = fontSize.sp,
                                                            lineHeight = (fontSize * 1.4f).sp
                                                        ),
                                                        color = MaterialTheme.colorScheme.secondary
                                                    )
                                                }
                                            }
                                        }
                                        item {
                                            Spacer(Modifier.height(180.dp))
                                        }
                                    }
                                }

                                // Custom Bottom Curved Notch Toolbar
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .clip(CurvedNotchShape(45.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f)
                                                .padding(horizontal = 24.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Left Stack (Sidebar & Settings)
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                IconButton(
                                                    modifier = Modifier.size(56.dp),
                                                    onClick = {
                                                        scope.launch {
                                                            sessionList = sessionManager.listSessions(sortType)
                                                            sessionDrawerState.open()
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = R.drawable.outline_arrow_menu_open_24),
                                                        contentDescription = "Menu",
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                                IconButton(
                                                    modifier = Modifier.size(56.dp),
                                                    onClick = {
                                                        scope.launch { settingsDrawerState.open() }
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Settings,
                                                        contentDescription = "Settings",
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.width(80.dp))

                                            // Right Stack (New & Share)
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                IconButton(
                                                    modifier = Modifier.size(56.dp),
                                                    onClick = {
                                                        if (isListening) {
                                                            stopListening()
                                                        }
                                                        sessionManager.saveSession(currentSessionName, sessionHistory)
                                                        sessionHistory = ""
                                                        partialText = ""
                                                        currentSessionName = sessionManager.generateTimestampedName()
                                                        // MIC OFF by default for new session
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Add,
                                                        contentDescription = "New Session",
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                                IconButton(
                                                    modifier = Modifier.size(56.dp),
                                                    onClick = { exportSession() }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Share,
                                                        contentDescription = "Export",
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Bottom Center Session Title (Click to rename)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surface)
                                                .padding(bottom = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = currentSessionName,
                                                modifier = Modifier.clickable {
                                                    renameInput = currentSessionName
                                                    showRenameDialog = true
                                                },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                // Floating Mic Button (Centered on the Notch)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 45.dp)
                                ) {
                                    val micBgColor = when {
                                        isListening -> Color.Black // Black when recording
                                        sessionHistory.isNotEmpty() -> Color.Red // Solid Red when paused/stopped
                                        else -> Color.Gray.copy(alpha = 0.2f) // 20% Grey when inactive
                                    }
                                    
                                    val micIcon = if (isListening) Icons.Default.Stop else Icons.Default.Mic
                                    val micTint = when {
                                        isListening -> Color.White // White icon when recording (on black)
                                        sessionHistory.isNotEmpty() -> Color.Black // Black icon when paused (on red)
                                        else -> MaterialTheme.colorScheme.onSurface // Default when inactive
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(micBgColor)
                                            .clickable { toggleListening() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = micIcon,
                                            contentDescription = if (isListening) "Stop" else "Start",
                                            modifier = Modifier.size(40.dp),
                                            tint = micTint
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogs
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
                    sessionList = sessionManager.listSessions(sortType)
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

    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete Session") },
            text = { Text("Are you sure you want to permanently delete this session record?") },
            confirmButton = {
                TextButton(onClick = {
                    sessionToDelete?.let {
                        sessionManager.deleteSession(it)
                        if (it == currentSessionName) {
                            sessionHistory = ""
                            partialText = ""
                            currentSessionName = sessionManager.generateTimestampedName()
                        }
                        sessionList = sessionManager.listSessions(sortType)
                    }
                    sessionToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
