package com.voice.control

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.android.AudioDispatcherFactory
import be.tarsos.dsp.mfcc.MFCC
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pytorch.Tensor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow
import kotlin.math.sqrt

class AudioRecorder(
    private val activity: ComponentActivity,
    private val onRecognitionResult: (String) -> Unit
) {
    private var dispatcher: AudioDispatcher? = null
    private var isRecording = AtomicBoolean(false)
    private val commandProcessor = VoiceCommandProcessor(activity)
    private val mfccFrames = mutableListOf<FloatArray>()
    private val featureCount = 20

    private val requestPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startRecording() else showToast("Microphone access denied")
        }

    fun toggleRecording() {
        if (isRecording.get()) stopRecording() else requestAudioPermission()
    }

    private fun requestAudioPermission() {
        val permission = Manifest.permission.RECORD_AUDIO
        if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission)
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val sampleRate = 16000
        val bufferSize = 2048
        val overlap = 1536

        mfccFrames.clear()

        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, bufferSize, overlap)
        val mfcc = MFCC(bufferSize, sampleRate.toFloat(), featureCount, 40, 300f, 8000f)

        dispatcher?.addAudioProcessor(mfcc)
        dispatcher?.addAudioProcessor(object : AudioProcessor {
            override fun process(audioEvent: AudioEvent): Boolean {
                val mfccResult = mfcc.mfcc ?: return true
                mfccFrames.add(normalize(mfccResult))
                return true
            }

            override fun processingFinished() {}
        })

        isRecording.set(true)
        Thread(dispatcher).start()
    }

    private fun stopRecording() {
        dispatcher?.stop()
        isRecording.set(false)
        processAudio()
    }

    private fun processAudio() {
        val paddedMatrix = Array(20) { i ->
            if (i < mfccFrames.size) mfccFrames[i] else FloatArray(featureCount) { 0f }
        }

        val inputTensor = Tensor.fromBlob(
            paddedMatrix.flatMap { it.toList() }.toFloatArray(),
            longArrayOf(1, 20, featureCount.toLong())
        )

        CoroutineScope(Dispatchers.Main).launch {
            val result = commandProcessor.processTensor(inputTensor)
            onRecognitionResult(result)
            commandProcessor.executeCommand(result)
        }
    }

    private fun normalize(array: FloatArray): FloatArray {
        val mean = array.average().toFloat()
        val std = sqrt(array.map { (it - mean).pow(2) }.average()).toFloat().coerceAtLeast(1e-6f)
        return array.map { (it - mean) / std }.toFloatArray()
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
