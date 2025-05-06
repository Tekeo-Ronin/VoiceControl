package com.voice.control

import android.content.Context
import android.util.Log
import android.widget.Toast

class VoiceCommandProcessor(private val context: Context) {

    fun executeCommand(command: String) {
        Toast.makeText(context, "Rozpoznano komendę: $command", Toast.LENGTH_SHORT).show()

        when (command) {
            "turn_on_browser" -> openBrowser()
            "turn_on_camera" -> openCamera()
            "turn_on_flashlight" -> toggleFlashlight(true)
            "turn_off_flashlight" -> toggleFlashlight(false)
            "turn_on_bluetooth" -> toggleBluetooth(true)
            "turn_off_bluetooth" -> toggleBluetooth(false)
            "okey_voiceapp" -> {}
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
            Log.i("VoiceProcessor", "Bluetooth ${if (enable) "włączony" else "wyłączony"}")
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
            Log.w("VoiceProcessor", "Brak uprawnień do aparatu — latarka nie została włączona.")
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
}
