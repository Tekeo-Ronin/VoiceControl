package com.voice.control

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object VoiceApiClient {
    private const val TAG = "VoiceApiClient"
    private const val SERVER_URL = "http://192.168.1.75:8000/predict/"
    private val client = OkHttpClient()

    suspend fun sendWavBytes(context: Context, wav: ByteArray, filename: String = "audio.wav"): String {
        return withContext(Dispatchers.IO) {
            try {
                val mediaType = "audio/wav".toMediaTypeOrNull()
                val requestBody = wav.toRequestBody(mediaType)

                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", filename, requestBody)
                    .build()

                val request = Request.Builder().url(SERVER_URL).post(multipartBody).build()
                val response = client.newCall(request).execute()

                val responseBody = response.body?.string()
                if (!response.isSuccessful) return@withContext "error"

                val json = JSONObject(responseBody ?: "")
                return@withContext json.optString("command", "unknown")
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}", e)
                return@withContext "error"
            }
        }
    }
}
