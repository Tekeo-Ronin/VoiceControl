package com.voice.control

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voice.control.ui.theme.VoiceControlTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import com.voice.control.AudioRecorder
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalContext



class MainActivity : ComponentActivity() {
    private lateinit var audioRecorder: AudioRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        audioRecorder = AudioRecorder(this) { recognized ->
            Toast.makeText(this, "Wynik: $recognized", Toast.LENGTH_SHORT).show()
        }

//        Thread {
//            val processor = VoiceCommandProcessor(this)
//            processor.testWithAssetFile("turn_on_browser_1.wav")
//        }.start()

        enableEdgeToEdge()

        setContent {
            VoiceControlTheme {
                VoiceAppScreen(audioRecorder)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceAppScreen(audioRecorder: AudioRecorder) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("Home") }

//    val context = LocalContext.current
//
//    LaunchedEffect(Unit) {
//        val processor = VoiceCommandProcessor(context)
//        processor.executeCommand("turn_on_flashlight")
//    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerMenuContent(
                onMenuItemClicked = { menuItem ->
                    currentScreen = menuItem
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Voice Control App") },
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
                    .padding(top = 32.dp)
                    .fillMaxSize()
            ) {
                when (currentScreen) {
                    "Home" -> VoiceRecorderScreen(isRecording) {
                        isRecording = !isRecording
                        scope.launch(Dispatchers.IO) {
                            audioRecorder?.toggleRecording()
                        }
                    }
                    "About App" -> AboutAppScreen()
                    "About Author" -> AboutAuthorScreen()
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
            text = if (isRecording) "Recording..." else "Press to start recording",
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 32.dp)
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
                contentDescription = "Record Button",
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
        Text(
            text = "Voice Recognition App v1.0",
            fontSize = 24.sp
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
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Student Wojskowej Akademii Technicznej im. Jarosława Dąbrowskiego",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Kierunek: Elektronika i Telekomunikacja\nSpecjalność: Systemy Cyfrowe",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}
