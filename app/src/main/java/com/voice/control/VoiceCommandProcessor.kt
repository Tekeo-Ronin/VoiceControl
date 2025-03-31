package com.voice.control

import android.content.Context
import android.util.Log
import android.widget.Toast
import be.tarsos.dsp.*
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.UniversalAudioInputStream
import be.tarsos.dsp.mfcc.MFCC
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.*
import java.util.concurrent.CountDownLatch
import kotlin.math.pow
import kotlin.math.sqrt

class VoiceCommandProcessor(private val context: Context) {

    private val model: Module = Module.load(assetFilePath(context, "model.ptl"))
    private val featureCount = 20

    private fun assetFilePath(context: Context, assetName: String): String {
        val file = File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) return file.absolutePath

        context.assets.open(assetName).use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    }

    fun testWithAssetFile(fileName: String) {
        val tempFile = File(context.cacheDir, fileName)
        context.assets.open(fileName).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        val inputStream = BufferedInputStream(FileInputStream(tempFile))
        val result = processAudioStream(inputStream)
        Log.i("VoiceProcessor", "Wynik: $result")
    }

    fun processAudioStream(inputStream: InputStream): String {
        val audioFormat = TarsosDSPAudioFormat(16000f, 16, 1, true, false)
        val bufferSize = 2048
        val overlap = 1536

        val audioStream = UniversalAudioInputStream(inputStream, audioFormat)
        val dispatcher = AudioDispatcher(audioStream, bufferSize, overlap)

        val mfcc = MFCC(bufferSize, 16000f, featureCount, 40, 300f, 8000f)
        val mfccFrames = mutableListOf<FloatArray>()
        val latch = CountDownLatch(1)

        dispatcher.addAudioProcessor(object : AudioProcessor {
            override fun process(audioEvent: AudioEvent): Boolean {
                mfcc.process(audioEvent)
                val mfccResult = mfcc.mfcc
                if (mfccResult != null) {
                    mfccFrames.add(normalize(mfccResult.copyOf()))
                }
                return true
            }

            override fun processingFinished() {
                latch.countDown()
            }
        })

        Thread(dispatcher).start()
        val completed = latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        if (!completed) {
            Log.e("VoiceProcessor", "Dispatcher timeout")
            return "unknown"
        }

        Log.i("VoiceProcessor", "Ramy MFCC: ${mfccFrames.size}")
        if (mfccFrames.isEmpty()) return "unknown"

        val paddedMatrix = Array(20) { i ->
            if (i < mfccFrames.size) mfccFrames[i] else FloatArray(featureCount) { 0f }
        }

        val inputTensor = Tensor.fromBlob(
            paddedMatrix.flatMap { it.toList() }.toFloatArray(),
            longArrayOf(1, 20, featureCount.toLong())
        )

        return processTensor(inputTensor)
    }

    fun processTensor(inputTensor: Tensor): String {
        return try {
            val output = model.forward(IValue.from(inputTensor)).toTensor()
            val outputData = output.dataAsFloatArray
            Log.i("VoiceProcessor", "Wynik surowy: ${outputData.joinToString()}")

            val predictedIndex = outputData.indices.maxByOrNull { outputData[it] } ?: -1
            return when (predictedIndex) {
                0 -> "okey_voiceapp"
                1 -> "turn_off_bluetooth"
                2 -> "turn_off_flashlight"
                3 -> "turn_on_bluetooth"
                4 -> "turn_on_browser"
                5 -> "turn_on_camera"
                6 -> "turn_on_flashlight"
                7 -> "unknown"
                else -> "unknown"
            }
        } catch (e: Exception) {
            Log.e("VoiceProcessor", "Błąd w processTensor: ${e.message}", e)
            "unknown"
        }
    }

    fun executeCommand(command: String) {
        Toast.makeText(context, "Rozpoznano komendę: $command", Toast.LENGTH_SHORT).show()

        when (command) {
            "turn_on_browser" -> openBrowser()
            "turn_on_camera" -> openCamera()
            "turn_on_flashlight" -> toggleFlashlight(true)
            "turn_off_flashlight" -> toggleFlashlight(false)
            "turn_on_bluetooth" -> toggleBluetooth(true)
            "turn_off_bluetooth" -> toggleBluetooth(false)
            "okey_voiceapp" -> {} // bez dodatkowego toastu
        }
    }

    private fun openBrowser() {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("https://www.google.com")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun openCamera() {
        val intent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun toggleBluetooth(enable: Boolean) {
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) return

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val permission = android.Manifest.permission.BLUETOOTH_CONNECT
            val granted = context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w("VoiceProcessor", "Brak uprawnień do Bluetooth (Android 12+)")
                return
            }
        }

        try {
            if (enable && !bluetoothAdapter.isEnabled) {
                bluetoothAdapter.enable()
            } else if (!enable && bluetoothAdapter.isEnabled) {
                bluetoothAdapter.disable()
            }
            Log.i("VoiceProcessor", "Bluetooth ${if (enable) "włączany" else "wyłączany"}")
        } catch (e: SecurityException) {
            Log.e("VoiceProcessor", "Brak uprawnień do Bluetooth", e)
        }
    }


    private fun toggleFlashlight(enable: Boolean) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager

        val permission = android.Manifest.permission.CAMERA
        val granted = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(permission)

        if (!granted) {
            Log.w("VoiceProcessor", "Brak uprawnień do kamery – latarka nie włączona.")
            return
        }

        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull {
                val characteristics = cameraManager.getCameraCharacteristics(it)
                val hasFlash = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                hasFlash
            }

            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enable)
                Log.i("VoiceProcessor", "Latarka ${if (enable) "włączona" else "wyłączona"}")
            } else {
                Log.w("VoiceProcessor", "Nie znaleziono aparatu z latarką")
            }
        } catch (e: Exception) {
            Log.e("VoiceProcessor", "Błąd przy zmianie stanu latarki", e)
        }
    }


    private fun normalize(array: FloatArray): FloatArray {
        val mean = array.average().toFloat()
        val std = sqrt(array.map { (it - mean).pow(2) }.average()).toFloat().coerceAtLeast(1e-6f)
        return array.map { (it - mean) / std }.toFloatArray()
    }
}
