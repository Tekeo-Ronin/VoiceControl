package com.voice.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voice.control.ui.theme.VoiceControlTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.voice.control.tests.TestLocalScreen
import com.voice.control.tests.TestowanieScreen
import com.voice.control.wake_word.WakeWordScreen

class MainActivity : ComponentActivity() {
    private lateinit var audioRecorder: AudioRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestAllPermissions()

        val voiceProcessor = VoiceCommandProcessor(this)
        audioRecorder = AudioRecorder(this, voiceProcessor)

        enableEdgeToEdge()
        setContent {
            VoiceControlTheme {
                VoiceAppScreen(audioRecorder)
            }
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), 0)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAppScreen(audioRecorder: AudioRecorder) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("Home") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight(),
                drawerContainerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .padding(32.dp)
                ) {
                    Spacer(modifier = Modifier.height(64.dp))
                    listOf(
                        "Home",
                        "Wake Word",
                        "Testowanie",
                        "Testowanie Lokalne",
                        "O Aplikacji",
                        "O Autorze"
                    ).forEach { menuItem ->
                        Text(
                            text = menuItem,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clickable {
                                    currentScreen = menuItem
                                    scope.launch { drawerState.close() }
                                },
                            fontSize = 18.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Aplikacja Sterowania Głosem") },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when (currentScreen) {
                    "Home" -> VoiceRecorderScreen(isRecording) {
                        isRecording = !isRecording
                        scope.launch(Dispatchers.IO) {
                            audioRecorder.toggleRecording()
                        }
                    }

                    "Wake Word" -> WakeWordScreen()
                    "Testowanie" -> TestowanieScreen()
                    "Testowanie Lokalne" -> {
                        val context = LocalContext.current
                        TestLocalScreen(context = context)
                    }

                    "O Aplikacji" -> AboutAppScreen()
                    "O Autorze" -> AboutAuthorScreen()
                }
            }
        }
    }
}


@Composable
fun DrawerMenuContent(onMenuItemClicked: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Spacer(modifier = Modifier.height(64.dp))
        listOf("Home", "Wake Word", "Testowanie", "Testowanie Lokalne","O Aplikacji", "O Autorze").forEach { menuItem ->
            Text(
                text = menuItem,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onMenuItemClicked(menuItem) },
                fontSize = 18.sp,
                color = Color.Black
            )
        }
    }
}

@Composable
fun VoiceRecorderScreen(isRecording: Boolean, onMicClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isRecording) "Nagrywanie..." else "Naciśnij, aby rozpocząć nagrywanie",
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp).fillMaxWidth()
        )

        Box(
            modifier = Modifier
                .size(150.dp)
                .background(
                    if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
                .clickable { onMicClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = "Przycisk nagrywania",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun AboutAppScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Aplikacja Rozpoznawania Głosu v1.0", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Ta aplikacja umożliwia nagrywanie i przetwarzanie głosu. Dodatkowe funkcje są w trakcie rozwoju.",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AboutAuthorScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Imię i nazwisko: Grzegorz Kołakowski", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Student Wojskowej Akademii Technicznej im. Jarosława Dąbrowskiego",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Kierunek: Elektronika i Telekomunikacja\nSpecjalność: Systemy Cyfrowe",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}
