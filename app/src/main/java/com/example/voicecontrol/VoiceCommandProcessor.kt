package com.example.voicecontrol

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream

class VoiceCommandProcessor(private val activity: ComponentActivity) {

    private lateinit var model: Module

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val assetName = "model.pt"
            val modelFile = File(activity.filesDir, assetName)
            activity.assets.open(assetName).use { input ->
                FileOutputStream(modelFile).use { output ->
                    input.copyTo(output)
                }
            }
            model = Module.load(modelFile.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(activity, "Failed to load model: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun processAudio(inputTensor: Tensor): String {
        val outputTensor = model.forward(IValue.from(inputTensor)).toTensor()
        val predictions = outputTensor.dataAsFloatArray

        return interpretCommand(predictions)
    }

    private fun interpretCommand(predictions: FloatArray): String {
        val maxPredictionIndex = predictions.indexOfFirst { it == predictions.maxOrNull() }

        return when (maxPredictionIndex) {
            0 -> "Okey Voiceapp"
            1 -> "Turn off Bluetooth"
            2 -> "Turn off flashlight"
            3 -> "Turn on Bluetooth"
            4 -> "Turn on browser"
            5 -> "Turn on camera"
            6 -> "Turn on flashlight"
            else -> "Unknown command"
        }
    }

    fun executeCommand(command: String) {
        when (command) {
            "Turn on flashlight" -> toggleFlashlight(true)
            "Turn off flashlight" -> toggleFlashlight(false)
            "Turn on Bluetooth" -> toggleBluetooth(true)
            "Turn off Bluetooth" -> toggleBluetooth(false)
            "Turn on browser" -> openBrowser()
            "Turn on camera" -> openCamera()
            else -> Toast.makeText(activity, "Unknown command: $command", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFlashlight(turnOn: Boolean) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            try {
                val cameraManager = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, turnOn)

                Toast.makeText(activity, if (turnOn) "Flashlight turned on" else "Flashlight turned off", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(activity, "Error accessing flashlight: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        } else {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    private fun toggleBluetooth(enable: Boolean) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Toast.makeText(activity, "Bluetooth is not supported", Toast.LENGTH_SHORT).show()
            } else {
                if (enable && !bluetoothAdapter.isEnabled) {
                    bluetoothAdapter.enable()
                    Toast.makeText(activity, "Bluetooth turned on", Toast.LENGTH_SHORT).show()
                } else if (!enable && bluetoothAdapter.isEnabled) {
                    bluetoothAdapter.disable()
                    Toast.makeText(activity, "Bluetooth turned off", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 2)
        }
    }

    private fun openBrowser() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://www.google.com"))
        activity.startActivity(intent)
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        activity.startActivity(intent)
    }
}
