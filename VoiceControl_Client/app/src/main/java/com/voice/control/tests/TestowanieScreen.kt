package com.voice.control.tests

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import okhttp3.RequestBody.Companion.toRequestBody
import android.util.Log
import com.voice.control.VoiceApiClient




@Composable
fun TestowanieScreen() {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val results = remember { mutableStateMapOf<String, String>() }

    val assetManager = context.assets
    val fileNames = remember {
        assetManager.list("dataset")?.map { "dataset/$it" }?.sorted() ?: emptyList()
    }
    var isTestStarted by remember { mutableStateOf(false) }

    fun extractExpectedCommand(fileName: String): String {
        val baseName = fileName.substringAfterLast('/').substringBeforeLast('.')
        val parts = baseName.split('_')
        return if (parts.size > 2) {
            parts.dropLast(1).joinToString("_")
        } else {
            baseName
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = {
                isTestStarted = true
                scope.launch {
                    for (fileName in fileNames) {
                        val result = sendFileToBackend(context, fileName)
                        results[fileName] = result
                    }
                }
            }) {
                Text("Start test")
            }
            Button(onClick = {
                isTestStarted = false
                results.clear()
            }) {
                Text("Clear results")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isTestStarted) {
            fileNames.forEach { fileName ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Nazwa pliku: $fileName", fontSize = 18.sp)
                    Text("Oczekiwany wynik: ${extractExpectedCommand(fileName)}", fontSize = 16.sp)
                    Text("Otrzymany wynik: ${results[fileName] ?: "---"}", fontSize = 16.sp)
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

suspend fun sendFileToBackend(context: Context, fileName: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val assetManager = context.assets
            val inputStream = assetManager.open(fileName)
            val bytes = inputStream.readBytes()
            VoiceApiClient.sendWavBytes(context, bytes, filename = fileName.substringAfterLast('/'))
        } catch (e: Exception) {
            Log.e("VoiceControl", "Error: ${e.message}", e)
            return@withContext "Błąd: ${e.message}"
        }
    }
}


