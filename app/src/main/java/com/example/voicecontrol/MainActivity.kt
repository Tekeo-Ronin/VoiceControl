package com.example.voicecontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var audioRecorder: AudioRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioRecorder = AudioRecorder(this) { recognizedText ->
            println("Model result: $recognizedText")
        }
        setContent {
            AppTheme {
                VoiceApp(audioRecorder)
            }
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceApp(audioRecorder: AudioRecorder) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("Home") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerMenuContent(
                onMenuItemClicked = { menuItem ->
                    currentScreen = menuItem
                    scope.launch { drawerState.close() }
                }
            )
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Voice Control App") },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } }
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .padding(top = 32.dp)
                    .fillMaxSize()
            ) {
                when (currentScreen) {
                    "About App" -> AboutAppScreen()
                    "About Author" -> AboutAuthorScreen()
                    "Home" -> VoiceAppScreen(isRecording, onMicClick = {
                        isRecording = !isRecording
                        audioRecorder.toggleRecording()
                    })
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
        listOf("Home", "About App", "About Author").forEach { menuItem ->
            Text(
                text = menuItem,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onMenuItemClicked(menuItem) },
                fontSize = 18.sp,
                textAlign = TextAlign.Start,
                color = Color.Black
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
        Text(
            text = "Voice Recognition App v1.0",
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "This application allows recording and voice processing. Additional features are in development.",
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
        Text(
            text = "Imię i nazwisko: Grzegorz Kołakowski",
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Student Wojskowej Akademii Technicznej im. Jarosława Dąbrowskiego",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Kierunek studiów: Elektronika i Telekomunikacja\nSpecjalność: Systemy Cyfrowe",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun VoiceAppScreen(isRecording: Boolean, onMicClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Press to start recording",
                fontSize = 24.sp,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .background(
                        if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                    .clickable { onMicClick() }
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = "Record Button",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}
