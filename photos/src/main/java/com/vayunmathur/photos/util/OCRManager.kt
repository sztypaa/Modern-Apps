package com.vayunmathur.photos.util

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import com.vayunmathur.library.util.SecureResultReceiver
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

class OCRManager(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    fun init() {
        // No-op for Gemma 4 via OpenAssistant as it's a remote service
    }

    suspend fun runOCR(uri: Uri): String? {
        var attempts = 0
        Log.d("OCRManager", "Starting OCR")
        while (attempts < 3) {
            attempts++
            val result = withTimeoutOrNull(60000) { // 1 minute timeout per photo
                performInference(uri)
            }
            if (result != null) {
                if (result == "BUSY") {
                    Log.d("OCRManager", "Service busy, waiting to retry... (Attempt $attempts)")
                    delay(60000) // Wait 1 minute if busy
                    continue
                }
                return if (result == "ERROR") null else result
            }
            Log.w("OCRManager", "Inference timed out for $uri")
        }
        return null
    }

    private suspend fun performInference(uri: Uri): String? {
        val tempFile = resizeImage(uri) ?: return "ERROR"
        
        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            
            val result = suspendCancellableCoroutine<String?> { continuation ->
                val schema = """
                    {
                      "type": "object",
                      "properties": {
                        "text": { "type": "string" },
                        "description": { "type": "string" }
                      },
                      "required": ["text", "description"]
                    }
                """.trimIndent()

                val userText = "Analyze this image and extract all visible text. Also provide a brief scene description for indexing purposes."

                val receiver = SecureResultReceiver(Handler(Looper.getMainLooper())) { resultCode, resultData ->
                    if (!continuation.isActive) return@SecureResultReceiver

                    if (resultCode == 0 && resultData != null) {
                        val jsonResult = resultData.getString("json_result")
                        if (jsonResult != null) {
                            try {
                                val element = json.parseToJsonElement(jsonResult).jsonObject
                                val text = element["text"]?.jsonPrimitive?.content ?: ""
                                val description = element["description"]?.jsonPrimitive?.content ?: ""
                                
                                val combined = if (text.isNotBlank() && description.isNotBlank()) {
                                    "$text\n\n$description"
                                } else if (text.isNotBlank()) {
                                    text
                                } else {
                                    description
                                }
                                
                                continuation.resume(if (combined.isNotBlank()) combined else null)
                            } catch (e: Exception) {
                                Log.e("OCRManager", "Error parsing AI result", e)
                                continuation.resume("ERROR")
                            }
                        } else {
                            continuation.resume(null)
                        }
                    } else if (resultCode == -2) { // BUSY
                        continuation.resume("BUSY")
                    } else {
                        val error = resultData?.getString("error")
                        Log.e("OCRManager", "Inference failed: $error")
                        continuation.resume("ERROR")
                    }
                }

                val intent = Intent().apply {
                    component = ComponentName(
                        "com.vayunmathur.openassistant",
                        "com.vayunmathur.openassistant.util.InferenceService"
                    )
                    putExtra("user_text", userText)
                    putParcelableArrayListExtra("image_uris", arrayListOf(contentUri))
                    putExtra("schema", schema)
                    putExtra("RECEIVER", receiver)
                    
                    context.grantUriPermission(
                        "com.vayunmathur.openassistant",
                        contentUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )

                    clipData = ClipData.newRawUri("Photo", contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                try {
                    context.startService(intent)
                } catch (e: Exception) {
                    Log.e("OCRManager", "Failed to start InferenceService", e)
                    continuation.resume("ERROR")
                }
            }
            result
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun resizeImage(uri: Uri): File? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { 
                BitmapFactory.decodeStream(it, null, options)
            }

            val maxDim = 432
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDim * 2 || options.outHeight / sampleSize > maxDim * 2) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val original = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            val scale = maxDim.toFloat() / Math.max(original.width, original.height)
            val finalWidth = (original.width * scale).toInt()
            val finalHeight = (original.height * scale).toInt()

//            val resized = Bitmap.createScaledBitmap(original, finalWidth, finalHeight, true)
//            if (resized != original) {
//                original.recycle()
//            }

            val tempFile = File(context.cacheDir, "ocr_resize_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                original.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            original.recycle()
            tempFile
        } catch (e: Exception) {
            Log.e("OCRManager", "Error resizing image: $uri", e)
            null
        }
    }

    fun release() {
        // No-op
    }
}
