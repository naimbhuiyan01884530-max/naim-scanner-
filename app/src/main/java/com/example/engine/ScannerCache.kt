package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

/**
 * Bounded in-memory LRU cache for fast page switching, cropper opening, and PDF rendering,
 * paired with a shared high-throughput Coil ImageLoader.
 */
object ScannerCache {

    private val maxCacheKb = ((Runtime.getRuntime().maxMemory() / 1024L) / 8L).toInt().coerceIn(8 * 1024, 64 * 1024)

    private val bitmapCache = object : LruCache<String, Bitmap>(maxCacheKb) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    @Volatile
    private var sharedImageLoader: ImageLoader? = null

    fun getBitmap(path: String, maxLongEdge: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val key = "${path}_${maxLongEdge}_${file.lastModified()}"
        val cached = bitmapCache.get(key) ?: return null
        return if (!cached.isRecycled) cached else {
            bitmapCache.remove(key)
            null
        }
    }

    fun putBitmap(path: String, maxLongEdge: Int, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val file = File(path)
        val key = "${path}_${maxLongEdge}_${file.lastModified()}"
        bitmapCache.put(key, bitmap)
    }

    fun getPdfPageBitmap(pdfPath: String, pageIndex: Int, targetWidth: Int): Bitmap? {
        val file = File(pdfPath)
        if (!file.exists()) return null
        val key = "pdf_${pdfPath}_${pageIndex}_${targetWidth}_${file.lastModified()}"
        val cached = bitmapCache.get(key) ?: return null
        return if (!cached.isRecycled) cached else {
            bitmapCache.remove(key)
            null
        }
    }

    fun putPdfPageBitmap(pdfPath: String, pageIndex: Int, targetWidth: Int, bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val file = File(pdfPath)
        val key = "pdf_${pdfPath}_${pageIndex}_${targetWidth}_${file.lastModified()}"
        bitmapCache.put(key, bitmap)
    }

    fun invalidatePath(path: String) {
        if (path.isBlank()) return
        val snapshot = bitmapCache.snapshot().keys.toList()
        for (key in snapshot) {
            if (key.startsWith(path) || key.startsWith("pdf_$path")) {
                bitmapCache.remove(key)
            }
        }
    }

    fun clear() {
        bitmapCache.evictAll()
    }

    fun imageLoader(context: Context): ImageLoader {
        sharedImageLoader?.let { return it }
        return synchronized(this) {
            sharedImageLoader ?: ImageLoader.Builder(context.applicationContext)
                .memoryCache {
                    MemoryCache.Builder(context.applicationContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(context.cacheDir, "coil_scanner_cache"))
                        .maxSizeBytes(96L * 1024L * 1024L)
                        .build()
                }
                .crossfade(120)
                .build()
                .also { sharedImageLoader = it }
        }
    }
}
