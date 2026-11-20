package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File

object ShareUtils {

    fun sharePdf(context: Context, filePath: String, title: String) {
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF"))
    }

    fun openPdfExternally(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Open PDF"))
        }
    }

    /**
     * Zero-permission save to Downloads/NaimScanner on Android 10+ (API 29+).
     * Returns false on API < 29 so UI can open SAF CreateDocument picker seamlessly.
     */
    fun savePdfToDownloads(context: Context, filePath: String, title: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        val cleanName = title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").ifBlank { "NaimScan" }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$cleanName.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/NaimScanner")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrDefault(false)
    }

    fun copyPdfToUri(context: Context, filePath: String, destinationUri: Uri): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false
        return runCatching {
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            true
        }.getOrDefault(false)
    }

    fun saveImageToGallery(context: Context, imageFile: File, title: String): Boolean {
        if (!imageFile.exists()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val cleanName = title.replace(Regex("[^a-zA-Z0-9._ -]"), "_").ifBlank { "NaimPage" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$cleanName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NaimScanner")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                imageFile.inputStream().use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrDefault(false)
    }
}
