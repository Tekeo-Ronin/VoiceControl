package com.voice.control.tests

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import be.tarsos.dsp.*
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.UniversalAudioInputStream
import be.tarsos.dsp.mfcc.MFCC
import java.io.*
import kotlin.math.pow
import kotlin.math.sqrt

@Composable
fun TestLocalScreen(context: Context) {
    var results by remember { mutableStateOf(listOf<String>()) }
    var isTesting by remember { mutableStateOf(false) }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Button(
                onClick = {
                    isTesting = true
                    results = listOf()
                    runTest(context) { line ->
                        results = results + line
                    }.invokeOnCompletion {
                        isTesting = false
                    }
                },
                enabled = !isTesting
            ) {
                Text("Start test")
            }

            Button(onClick = { results = listOf() }) {
                Text("Clear result")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            results.forEach { line ->
                Text(text = line)
                Divider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

fun runTest(
    context: Context,
    onResult: (String) -> Unit
): Job = CoroutineScope(Dispatchers.IO).launch {
    val assetManager = context.assets
    val files = assetManager.list("dataset")?.filter { it.endsWith(".wav") } ?: return@launch
    val model = loadModel(context)

    val labels = listOf(
        "okey_voiceapp", "turn_off_bluetooth", "turn_off_flashlight", "turn_on_bluetooth",
        "turn_on_browser", "turn_on_camera", "turn_on_flashlight", "unknown"
    )

    for (fileName in files) {
        val expected = fileName.substringBeforeLast("_").replace("_", " ")
        val predicted = try {
            val result = processFile(context, model, "dataset/$fileName")
            labels.getOrElse(result) { "unknown" }
        } catch (e: Exception) {
            Log.e("TestPage", "Error processing $fileName", e)
            "error"
        }

        val output = "Nazwa pliku: $fileName\nOczekiwany wynik: $expected\nOtrzymany wynik: $predicted"
        withContext(Dispatchers.Main) {
            onResult(output)
        }
    }
}

private fun loadModel(context: Context): Module {
    val assetName = "model.ptl"
    val file = File(context.filesDir, assetName)
    if (!file.exists() || file.length() == 0L) {
        context.assets.open(assetName).use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
    }
    return Module.load(file.absolutePath)
}

private fun processFile(context: Context, model: Module, assetPath: String): Int {
    val format = TarsosDSPAudioFormat(16000f, 16, 1, true, false)
    val stream = context.assets.open(assetPath)
    val audioStream = UniversalAudioInputStream(BufferedInputStream(stream), format)
    val dispatcher = AudioDispatcher(audioStream, 2048, 1536)
    val mfcc = MFCC(2048, 16000f, 20, 40, 300f, 8000f)
    val frames = mutableListOf<FloatArray>()
    val latch = java.util.concurrent.CountDownLatch(1)

    dispatcher.addAudioProcessor(object : AudioProcessor {
        override fun process(event: AudioEvent): Boolean {
            mfcc.process(event)
            mfcc.mfcc?.let { frames.add(normalize(it.copyOf())) }
            return true
        }

        override fun processingFinished() {
            latch.countDown()
        }
    })

    Thread(dispatcher).start()
    latch.await()

    if (frames.isEmpty()) return 7 // unknown

    val matrix = Array(20) { i -> if (i < frames.size) frames[i] else FloatArray(20) }
    val flat = matrix.flatMap { it.toList() }.toFloatArray()
    val input = Tensor.fromBlob(flat, longArrayOf(1, 20, 20))

    val output = model.forward(IValue.from(input)).toTensor()
    val scores = output.dataAsFloatArray
    return scores.indices.maxByOrNull { scores[it] } ?: 7
}

private fun normalize(arr: FloatArray): FloatArray {
    val mean = arr.average().toFloat()
    val std = sqrt(arr.map { (it - mean).pow(2) }.average()).toFloat().coerceAtLeast(1e-6f)
    return arr.map { (it - mean) / std }.toFloatArray()
}
