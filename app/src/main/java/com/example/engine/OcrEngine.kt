package com.example.engine

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 100% offline bundled on-device text extraction (ML Kit Latin Text Recognition).
 * Fails safely in headless JVM/Robolectric test environments without crashing.
 */
object OcrEngine {

    private val recognizer by lazy {
        runCatching {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }.getOrNull()
    }

    suspend fun extractTextFromFile(imagePath: String): String = withContext(Dispatchers.Default) {
        val client = recognizer ?: return@withContext ""
        val bmp = ImageProcessor.decodeFileNormalized(imagePath, 1600) ?: return@withContext ""
        try {
            recognizeBitmap(client, bmp)
        } finally {
            bmp.recycle()
        }
    }

    private suspend fun recognizeBitmap(
        client: com.google.mlkit.vision.text.TextRecognizer,
        bitmap: Bitmap
    ): String = suspendCancellableCoroutine { cont ->
        runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            client.process(image)
                .addOnSuccessListener { result ->
                    if (cont.isActive) {
                        cont.resume(result.text.trim())
                    }
                }
                .addOnFailureListener {
                    if (cont.isActive) {
                        cont.resume("")
                    }
                }
        }.onFailure {
            if (cont.isActive) {
                cont.resume("")
            }
        }
    }
}
