package com.example.voicecontrol

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.widget.Toast
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.pytorch.Tensor
import java.io.File
import be.tarsos.dsp.mfcc.MFCC
import com.arthenica.ffmpegkit.FFmpegKit
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.io.TarsosDSPAudioFormat

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
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(activity)
            } else {
                MediaRecorder()
            }.apply {
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

            CoroutineScope(Dispatchers.IO).launch {
                val mfccFeatures = extractMFCCFromAudio(audioFile)

                // Формуємо тензор для моделі
                val mfccTensorShape = longArrayOf(1, (mfccFeatures.size / 20).toLong(), 20)
                val inputTensor = Tensor.fromBlob(mfccFeatures, mfccTensorShape)

                val recognizedCommand = commandProcessor.processAudio(inputTensor)
                withContext(Dispatchers.Main) {
                    onRecognitionResult("Recognized: $recognizedCommand")
                    commandProcessor.executeCommand(recognizedCommand)
                }
            }
        } catch (e: Exception) {
            showToast("Error stopping recording: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun extractMFCCFromAudio(audioFile: File): FloatArray {
        val pcmFile = File(activity.filesDir, "audio.pcm")
        if (pcmFile.exists()) {
            pcmFile.delete()
        }

        val command = "-i ${audioFile.absolutePath} -af loudnorm -f s16le -ar 16000 -ac 1 ${pcmFile.absolutePath}"
        val session = FFmpegKit.execute(command)
        if (!session.returnCode.isSuccess) {
            throw RuntimeException("FFmpeg conversion failed: ${session.returnCode}")
        }

        val mfccProcessor = MFCC(512, 20)
        val mfccList = mutableListOf<Float>()
        val audioFormat = TarsosDSPAudioFormat(16000f, 16, 1, true, false)

        pcmFile.inputStream().use { inputStream ->
            val buffer = ByteArray(2048)
            val samples = mutableListOf<Short>()
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                for (i in 0 until bytesRead / 2) {
                    val sample = ((buffer[2 * i].toInt() and 0xFF) or (buffer[2 * i + 1].toInt() shl 8)).toShort()
                    samples.add(sample)
                }
            }
            val floatSamples = samples.map { it / 32768.0f }.toFloatArray()
            val windowSize = 512
            val stepSize = 256
            var currentIndex = 0
            while (currentIndex + windowSize < floatSamples.size) {
                val sampleBlock = floatSamples.copyOfRange(currentIndex, currentIndex + windowSize)
                val audioEvent = AudioEvent(audioFormat).apply { setFloatBuffer(sampleBlock) }
                mfccProcessor.process(audioEvent)
                mfccList.addAll(mfccProcessor.mfcc.toList())
                currentIndex += stepSize
            }
        }
        return mfccList.toFloatArray()
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}
