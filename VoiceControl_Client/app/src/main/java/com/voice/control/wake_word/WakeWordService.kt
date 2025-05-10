package com.voice.control.wake_word

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.voice.control.MainActivity
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import com.voice.control.VoiceApiClient

class WakeWordService : Service() {

    private var audioRecord: AudioRecord? = null
    private var isRunning = false
    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    override fun onCreate() {
        super.onCreate()
        if (isRunning) return
        startForegroundService()
        startWakeWordLoop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        val channelId = "wake_word_service"
        val channelName = "Wake Word Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            getSystemService(NotificationManager::class.java).createNotificationChannel(chan)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Wake Word Aktywny")
            .setContentText("Usługa nasłuchuje 'okay voice app'")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        startForeground(1, notification)
    }

    private fun startWakeWordLoop() {
        isRunning = true

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("WakeWordService", "Brak pozwolenia na mikrofon")
            stopSelf()
            return
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()

        CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(bufferSize)
            val collected = ByteArrayOutputStream()

            while (isRunning) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    collected.write(buffer, 0, read)

                    if (collected.size() >= sampleRate * 2) { // 1 секунда
                        val pcmData = collected.toByteArray()
                        collected.reset()

                        val trimmed = trimSilence(pcmData)
                        val normalized = normalizeAudio(trimmed)
                        val wav = pcmToWav(normalized, sampleRate, 1)

                        val result = VoiceApiClient.sendWavBytes(this@WakeWordService, wav, "wake_word.wav")
                        if (result == "okey_voiceapp") {
                            Log.i("WakeWordService", "Wake word detected!")
                            showWakeNotification()
                            delay(3000)
                        }
                    }
                }
            }
        }
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

    private fun showWakeNotification() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        startActivity(intent)
    }



    override fun onDestroy() {
        isRunning = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        super.onDestroy()
    }
}
