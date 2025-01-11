package com.example.voicecontrol

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import org.pytorch.Tensor

class AudioRecorder(
    private val activity: ComponentActivity,
    private val onRecognitionResult: (String) -> Unit
) {
    private var recorder: MediaRecorder? = null
    private lateinit var audioFile: File
    private var isRecording = false

    private val requestPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startRecording()
            } else {
                showToast("Microphone access denied")
            }
        }

    private val commandProcessor = VoiceCommandProcessor(activity)

    fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            requestAudioPermission()
        }
    }

    private fun requestAudioPermission() {
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        try {
            audioFile = File(activity.filesDir, "recorded_audio.wav")
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(audioFile.absolutePath)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                prepare()
                start()
            }
            isRecording = true
            showToast("Recording started")
        } catch (e: Exception) {
            isRecording = false
            showToast("Error starting recording: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            isRecording = false
            showToast("Recording finished")

            val dummyAudioData = FloatArray(100 * 20) { 0.0f }
            val inputTensor = Tensor.fromBlob(dummyAudioData, longArrayOf(1, 100, 20))

            val recognizedCommand = commandProcessor.processAudio(inputTensor)

            onRecognitionResult("Recognized: $recognizedCommand")
            commandProcessor.executeCommand(recognizedCommand)

        } catch (e: Exception) {
            showToast("Error stopping recording: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
