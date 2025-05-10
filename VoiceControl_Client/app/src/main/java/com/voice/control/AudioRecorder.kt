package com.voice.control

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

class AudioRecorder(
    private val activity: ComponentActivity,
    private val commandProcessor: VoiceCommandProcessor
) {
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val recordedData = ByteArrayOutputStream()

    private val requestPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) startRecording() else showToast("Brak dostępu do mikrofonu")
        }

    fun toggleRecording() {
        if (isRecording) stopRecording() else requestAudioPermission()
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
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showToast("Brak dostępu do mikrofonu")
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        val buffer = ByteArray(bufferSize)
        audioRecord?.startRecording()
        isRecording = true
        recordedData.reset()

        thread {
            Thread.sleep(300)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) recordedData.write(buffer, 0, read)
            }
        }
    }

    private fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val pcmData = recordedData.toByteArray()
        val trimmed = trimSilence(pcmData, threshold = 1000)
        val normalized = normalizeAudio(trimmed)
        val wavData = pcmToWav(normalized, sampleRate, 1)

        sendBytesToBackend(wavData)
    }

    private fun trimSilence(data: ByteArray, threshold: Short = 1000): ByteArray {
        val samples = ShortArray(data.size / 2)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
        var start = 0
        var end = samples.size - 1

        while (start < samples.size && abs(samples[start].toInt()) < threshold) start++
        while (end > start && abs(samples[end].toInt()) < threshold) end--

        val trimmed = if (end >= start) samples.copyOfRange(start, end + 1) else shortArrayOf()
        val trimmedBuffer = ByteBuffer.allocate(trimmed.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        trimmed.forEach { trimmedBuffer.putShort(it) }

        return trimmedBuffer.array()
    }

    private fun normalizeAudio(data: ByteArray): ByteArray {
        val samples = ShortArray(data.size / 2)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)

        var maxAmp = 0
        samples.forEach { maxAmp = max(maxAmp, abs(it.toInt())) }
        val scale = if (maxAmp == 0) 1f else 32767f / maxAmp

        val normalizedSamples = samples.map { (it * scale).toInt().coerceIn(-32768, 32767).toShort() }
        val normalizedBuffer = ByteBuffer.allocate(normalizedSamples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        normalizedSamples.forEach { normalizedBuffer.putShort(it) }

        return normalizedBuffer.array()
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * 2
        val wavSize = 36 + pcmData.size
        val buffer = ByteBuffer.allocate(44 + pcmData.size).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(wavSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1.toShort())
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((channels * 2).toShort())
        buffer.putShort(16.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(pcmData.size)
        buffer.put(pcmData)

        return buffer.array()
    }

    private fun sendBytesToBackend(bytes: ByteArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = VoiceApiClient.sendWavBytes(activity, bytes, "recorded_audio.wav")

            withContext(Dispatchers.Main) {
                commandProcessor.executeCommand(result, suppressWakeWord = true)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
