package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.example.engine.ImageProcessor
import com.example.engine.OcrEngine
import com.example.engine.PdfEngine
import com.example.engine.ScannerCache
import com.example.model.DrawStroke
import com.example.model.FilterMode
import com.example.model.FolderModel
import com.example.model.NormalizedPoint
import com.example.model.PageModel
import com.example.model.PdfFileModel
import com.example.model.PdfMargin
import com.example.model.PdfPageLayout
import com.example.model.PdfPageSize
import com.example.model.PdfQualityPreset
import com.example.model.QuadCorners
import com.example.model.ScanDocumentModel
import com.example.model.SignaturePlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

class ScannerRepository(private val context: Context) {

    private val dbHelper = ScannerDatabaseHelper(context.applicationContext)
    private val pagesRootDir = File(context.filesDir, "pages").apply { mkdirs() }
    private val pdfsRootDir = File(context.filesDir, "pdfs").apply { mkdirs() }

    private val _folders = MutableStateFlow<List<FolderModel>>(emptyList())
    val folders: StateFlow<List<FolderModel>> = _folders.asStateFlow()

    private val _documents = MutableStateFlow<List<ScanDocumentModel>>(emptyList())
    val documents: StateFlow<List<ScanDocumentModel>> = _documents.asStateFlow()

    private val _pdfs = MutableStateFlow<List<PdfFileModel>>(emptyList())
    val pdfs: StateFlow<List<PdfFileModel>> = _pdfs.asStateFlow()

    private data class PreparedPageData(
        val orderIndex: Int,
        val rawPath: String,
        val croppedPath: String,
        val enhancedPath: String,
        val thumbPath: String,
        val corners: QuadCorners,
        val filterMode: FilterMode
    )

    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val folderList = mutableListOf<FolderModel>()
        db.rawQuery("SELECT id, name, created_at FROM folders ORDER BY name ASC", null).use { c ->
            while (c.moveToNext()) {
                folderList.add(FolderModel(c.getLong(0), c.getString(1), c.getLong(2)))
            }
        }
        val folderMap = folderList.associateBy { it.id }

        val pagesByDoc = mutableMapOf<Long, MutableList<PageModel>>()
        db.rawQuery(
            """
            SELECT id, document_id, order_index, raw_image_path, cropped_image_path, enhanced_image_path,
                   thumb_image_path, corners_serialized, rotation_degrees, flip_horizontal, filter_mode,
                   contrast_boost, brightness_offset, ocr_text
            FROM pages ORDER BY order_index ASC, id ASC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val docId = c.getLong(1)
                val mode = runCatching { FilterMode.valueOf(c.getString(10)) }.getOrDefault(FilterMode.SHARP_BW)
                val page = PageModel(
                    id = c.getLong(0),
                    documentId = docId,
                    orderIndex = c.getInt(2),
                    rawImagePath = c.getString(3),
                    croppedImagePath = c.getString(4),
                    enhancedImagePath = c.getString(5),
                    thumbImagePath = c.getString(6),
                    corners = QuadCorners.fromSerialized(c.getString(7)),
                    rotationDegrees = c.getInt(8),
                    flipHorizontal = c.getInt(9) == 1,
                    filterMode = mode,
                    contrastBoost = c.getFloat(11),
                    brightnessOffset = c.getFloat(12),
                    ocrText = c.getString(13) ?: ""
                )
                pagesByDoc.getOrPut(docId) { mutableListOf() }.add(page)
            }
        }

        val docList = mutableListOf<ScanDocumentModel>()
        db.rawQuery(
            """
            SELECT id, title, folder_id, tag, created_at, updated_at, last_pdf_id, last_pdf_size_bytes
            FROM documents ORDER BY updated_at DESC, id DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val docId = c.getLong(0)
                val folderId = if (c.isNull(2)) null else c.getLong(2)
                docList.add(
                    ScanDocumentModel(
                        id = docId,
                        title = c.getString(1),
                        folderId = folderId,
                        folderName = folderId?.let { folderMap[it]?.name },
                        tag = c.getString(3),
                        createdAt = c.getLong(4),
                        updatedAt = c.getLong(5),
                        pages = pagesByDoc[docId]?.toList() ?: emptyList(),
                        lastPdfId = if (c.isNull(6)) null else c.getLong(6),
                        lastPdfSizeBytes = if (c.isNull(7)) null else c.getLong(7)
                    )
                )
            }
        }

        val pdfList = mutableListOf<PdfFileModel>()
        db.rawQuery(
            """
            SELECT id, title, file_path, size_bytes, page_count, page_size, quality_preset,
                   is_password_protected, watermark_text, folder_name, document_id, created_at
            FROM pdf_files ORDER BY created_at DESC, id DESC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val filePath = c.getString(2)
                val file = File(filePath)
                if (!file.exists()) continue
                val pSize = runCatching { PdfPageSize.valueOf(c.getString(5)) }.getOrDefault(PdfPageSize.A4)
                val qPreset = runCatching { PdfQualityPreset.valueOf(c.getString(6)) }.getOrDefault(PdfQualityPreset.BALANCED)
                pdfList.add(
                    PdfFileModel(
                        id = c.getLong(0),
                        title = c.getString(1),
                        filePath = filePath,
                        sizeBytes = file.length(),
                        pageCount = c.getInt(4),
                        pageSize = pSize,
                        qualityPreset = qPreset,
                        isPasswordProtected = c.getInt(7) == 1,
                        watermarkText = c.getString(8),
                        folderName = c.getString(9),
                        documentId = if (c.isNull(10)) null else c.getLong(10),
                        createdAt = c.getLong(11)
                    )
                )
            }
        }

        _folders.value = folderList
        _documents.value = docList
        _pdfs.value = pdfList
    }

    suspend fun createFolder(name: String): Long = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext -1L
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("name", trimmed)
            put("created_at", System.currentTimeMillis())
        }
        val id = db.insertWithOnConflict("folders", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
        refreshAll()
        id
    }

    suspend fun deleteFolder(folderId: Long) = withContext(Dispatchers.IO) {
        dbHelper.writableDatabase.delete("folders", "id = ?", arrayOf(folderId.toString()))
        refreshAll()
    }

    suspend fun createDocumentFromImageFiles(
        imageFiles: List<File>,
        title: String? = null,
        folderId: Long? = null,
        autoDetectCorners: Boolean = true,
        defaultFilter: FilterMode = FilterMode.SHARP_BW,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        if (imageFiles.isEmpty()) return@withContext -1L
        val now = System.currentTimeMillis()
        val docTitle = title?.takeIf { it.isNotBlank() } ?: defaultDocTitle(now)
        val db = dbHelper.writableDatabase
        val docId = db.insert(
            "documents",
            null,
            ContentValues().apply {
                put("title", docTitle)
                if (folderId != null) put("folder_id", folderId) else putNull("folder_id")
                put("tag", "")
                put("created_at", now)
                put("updated_at", now)
            }
        )
        val docDir = File(pagesRootDir, "doc_$docId").apply { mkdirs() }
        val semaphore = Semaphore(4)
        val completed = AtomicInteger(0)

        val prepared = coroutineScope {
            imageFiles.mapIndexed { idx, file ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        val rawBmp = ImageProcessor.decodeFileNormalized(file.absolutePath)
                        val res = rawBmp?.let { bmp ->
                            processAndSaveBitmap(bmp, docDir, idx, autoDetectCorners, defaultFilter).also {
                                bmp.recycle()
                            }
                        }
                        synchronized(completed) {
                            onProgress(completed.incrementAndGet(), imageFiles.size)
                        }
                        res
                    }
                }
            }.awaitAll().filterNotNull()
        }

        if (prepared.isEmpty()) {
            db.delete("documents", "id = ?", arrayOf(docId.toString()))
            docDir.deleteRecursively()
            return@withContext -1L
        }

        insertPreparedPagesBatch(docId, prepared)
        refreshAll()
        docId
    }

    suspend fun createDocumentFromUris(
        uris: List<Uri>,
        folderId: Long? = null,
        autoDetectCorners: Boolean = true,
        defaultFilter: FilterMode = FilterMode.SHARP_BW,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Long = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext -1L
        val now = System.currentTimeMillis()
        val docTitle = defaultDocTitle(now)
        val db = dbHelper.writableDatabase
        val docId = db.insert(
            "documents",
            null,
            ContentValues().apply {
                put("title", docTitle)
                if (folderId != null) put("folder_id", folderId) else putNull("folder_id")
                put("tag", "")
                put("created_at", now)
                put("updated_at", now)
            }
        )
        val docDir = File(pagesRootDir, "doc_$docId").apply { mkdirs() }
        val semaphore = Semaphore(4)
        val completed = AtomicInteger(0)

        val prepared = coroutineScope {
            uris.mapIndexed { idx, uri ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        val rawBmp = ImageProcessor.decodeUriNormalized(context, uri)
                        val res = rawBmp?.let { bmp ->
                            processAndSaveBitmap(bmp, docDir, idx, autoDetectCorners, defaultFilter).also {
                                bmp.recycle()
                            }
                        }
                        synchronized(completed) {
                            onProgress(completed.incrementAndGet(), uris.size)
                        }
                        res
                    }
                }
            }.awaitAll().filterNotNull()
        }

        if (prepared.isEmpty()) {
            db.delete("documents", "id = ?", arrayOf(docId.toString()))
            docDir.deleteRecursively()
            return@withContext -1L
        }

        insertPreparedPagesBatch(docId, prepared)
        refreshAll()
        docId
    }

    suspend fun addPagesFromUris(
        documentId: Long,
        uris: List<Uri>,
        autoDetectCorners: Boolean = true,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext
        val currentPages = _documents.value.find { it.id == documentId }?.pages ?: emptyList()
        val startIndex = (currentPages.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val docDir = File(pagesRootDir, "doc_$documentId").apply { mkdirs() }
        val semaphore = Semaphore(4)
        val completed = AtomicInteger(0)

        val prepared = coroutineScope {
            uris.mapIndexed { offset, uri ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        val rawBmp = ImageProcessor.decodeUriNormalized(context, uri)
                        val res = rawBmp?.let { bmp ->
                            processAndSaveBitmap(bmp, docDir, startIndex + offset, autoDetectCorners, FilterMode.SHARP_BW).also {
                                bmp.recycle()
                            }
                        }
                        synchronized(completed) {
                            onProgress(completed.incrementAndGet(), uris.size)
                        }
                        res
                    }
                }
            }.awaitAll().filterNotNull()
        }

        if (prepared.isNotEmpty()) {
            insertPreparedPagesBatch(documentId, prepared)
            touchDocument(documentId)
            refreshAll()
        }
    }

    suspend fun addPageFromFile(
        documentId: Long,
        sourceFile: File,
        autoDetectCorners: Boolean = true
    ): Long = withContext(Dispatchers.IO) {
        val currentPages = _documents.value.find { it.id == documentId }?.pages ?: emptyList()
        val nextIndex = (currentPages.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val docDir = File(pagesRootDir, "doc_$documentId").apply { mkdirs() }
        val rawBmp = ImageProcessor.decodeFileNormalized(sourceFile.absolutePath) ?: return@withContext -1L
        val prepared = processAndSaveBitmap(rawBmp, docDir, nextIndex, autoDetectCorners, FilterMode.SHARP_BW)
        rawBmp.recycle()
        insertPreparedPagesBatch(documentId, listOf(prepared))
        touchDocument(documentId)
        refreshAll()
        nextIndex.toLong()
    }

    suspend fun replacePageWithFile(pageId: Long, newSourceFile: File) = withContext(Dispatchers.IO) {
        val page = findPageById(pageId) ?: return@withContext
        val rawBmp = ImageProcessor.decodeFileNormalized(newSourceFile.absolutePath) ?: return@withContext
        val rawFile = File(page.rawImagePath)
        ImageProcessor.saveBitmapJpeg(rawBmp, rawFile, 95)

        val corners = ImageProcessor.detectDocumentCorners(rawBmp)
        val warped = ImageProcessor.warpAndOrient(rawBmp, corners, 0, false)
        rawBmp.recycle()
        ImageProcessor.saveBitmapJpeg(warped, File(page.croppedImagePath), 95)

        val enhanced = ImageProcessor.applyEnhancement(warped, page.filterMode)
        warped.recycle()
        ImageProcessor.saveBitmapJpeg(enhanced, File(page.enhancedImagePath), 94)

        val thumbPath = page.thumbImagePath.ifEmpty {
            page.enhancedImagePath.replace("_enhanced.jpg", "_thumb.jpg")
        }
        ImageProcessor.saveThumbnail(enhanced, File(thumbPath))
        enhanced.recycle()

        invalidatePageCaches(page)

        val values = ContentValues().apply {
            put("thumb_image_path", thumbPath)
            put("corners_serialized", corners.toSerialized())
            put("rotation_degrees", 0)
            put("flip_horizontal", 0)
            put("ocr_text", "")
        }
        dbHelper.writableDatabase.update("pages", values, "id = ?", arrayOf(pageId.toString()))
        touchDocument(page.documentId)
        refreshAll()
    }

    suspend fun duplicatePage(pageId: Long): Long = withContext(Dispatchers.IO) {
        val page = findPageById(pageId) ?: return@withContext -1L
        val doc = _documents.value.find { it.id == page.documentId } ?: return@withContext -1L
        val nextIndex = (doc.pages.maxOfOrNull { it.orderIndex } ?: 0) + 1
        val docDir = File(pagesRootDir, "doc_${page.documentId}").apply { mkdirs() }
        val stamp = "${System.nanoTime()}_dup"
        val newRaw = File(docDir, "${stamp}_raw.jpg")
        val newCrop = File(docDir, "${stamp}_cropped.jpg")
        val newEnh = File(docDir, "${stamp}_enhanced.jpg")
        val newThumb = File(docDir, "${stamp}_thumb.jpg")

        File(page.rawImagePath).takeIf { it.exists() }?.copyTo(newRaw, overwrite = true)
        File(page.croppedImagePath).takeIf { it.exists() }?.copyTo(newCrop, overwrite = true)
        File(page.enhancedImagePath).takeIf { it.exists() }?.copyTo(newEnh, overwrite = true)
        File(page.thumbImagePath).takeIf { page.thumbImagePath.isNotEmpty() && it.exists() }?.copyTo(newThumb, overwrite = true)

        val newId = dbHelper.writableDatabase.insert(
            "pages",
            null,
            ContentValues().apply {
                put("document_id", page.documentId)
                put("order_index", nextIndex)
                put("raw_image_path", newRaw.absolutePath)
                put("cropped_image_path", newCrop.absolutePath)
                put("enhanced_image_path", newEnh.absolutePath)
                put("thumb_image_path", if (newThumb.exists()) newThumb.absolutePath else "")
                put("corners_serialized", page.corners.toSerialized())
                put("rotation_degrees", page.rotationDegrees)
                put("flip_horizontal", if (page.flipHorizontal) 1 else 0)
                put("filter_mode", page.filterMode.name)
                put("contrast_boost", page.contrastBoost)
                put("brightness_offset", page.brightnessOffset)
                put("ocr_text", page.ocrText)
            }
        )
        touchDocument(page.documentId)
        refreshAll()
        newId
    }

    private fun processAndSaveBitmap(
        rawBmp: Bitmap,
        docDir: File,
        orderIndex: Int,
        autoDetectCorners: Boolean,
        filterMode: FilterMode
    ): PreparedPageData {
        val stamp = "${System.nanoTime()}_$orderIndex"
        val rawFile = File(docDir, "${stamp}_raw.jpg")
        val croppedFile = File(docDir, "${stamp}_cropped.jpg")
        val enhancedFile = File(docDir, "${stamp}_enhanced.jpg")
        val thumbFile = File(docDir, "${stamp}_thumb.jpg")

        ImageProcessor.saveBitmapJpeg(rawBmp, rawFile, 95)
        val corners = if (autoDetectCorners) ImageProcessor.detectDocumentCorners(rawBmp) else QuadCorners.FULL
        val warped = ImageProcessor.warpAndOrient(rawBmp, corners, 0, false)
        ImageProcessor.saveBitmapJpeg(warped, croppedFile, 95)

        val enhanced = ImageProcessor.applyEnhancement(warped, filterMode)
        warped.recycle()
        ImageProcessor.saveBitmapJpeg(enhanced, enhancedFile, 94)
        ImageProcessor.saveThumbnail(enhanced, thumbFile)
        enhanced.recycle()

        return PreparedPageData(
            orderIndex = orderIndex,
            rawPath = rawFile.absolutePath,
            croppedPath = croppedFile.absolutePath,
            enhancedPath = enhancedFile.absolutePath,
            thumbPath = thumbFile.absolutePath,
            corners = corners,
            filterMode = filterMode
        )
    }

    private fun insertPreparedPagesBatch(documentId: Long, pages: List<PreparedPageData>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            pages.sortedBy { it.orderIndex }.forEach { p ->
                val values = ContentValues().apply {
                    put("document_id", documentId)
                    put("order_index", p.orderIndex)
                    put("raw_image_path", p.rawPath)
                    put("cropped_image_path", p.croppedPath)
                    put("enhanced_image_path", p.enhancedPath)
                    put("thumb_image_path", p.thumbPath)
                    put("corners_serialized", p.corners.toSerialized())
                    put("rotation_degrees", 0)
                    put("flip_horizontal", 0)
                    put("filter_mode", p.filterMode.name)
                    put("contrast_boost", 1.0f)
                    put("brightness_offset", 0f)
                    put("ocr_text", "")
                }
                db.insert("pages", null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun updatePageTransformAndFilter(
        pageId: Long,
        corners: QuadCorners,
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        filterMode: FilterMode,
        contrastBoost: Float = 1.0f,
        brightnessOffset: Float = 0f
    ) = withContext(Dispatchers.IO) {
        val page = findPageById(pageId) ?: return@withContext
        val rawBmp = ImageProcessor.decodeFileNormalized(page.rawImagePath) ?: return@withContext
        val warped = ImageProcessor.warpAndOrient(rawBmp, corners, rotationDegrees, flipHorizontal)
        rawBmp.recycle()
        ImageProcessor.saveBitmapJpeg(warped, File(page.croppedImagePath), 95)

        val enhanced = ImageProcessor.applyEnhancement(warped, filterMode, contrastBoost, brightnessOffset)
        warped.recycle()
        ImageProcessor.saveBitmapJpeg(enhanced, File(page.enhancedImagePath), 94)

        val thumbPath = page.thumbImagePath.ifEmpty {
            page.enhancedImagePath.replace("_enhanced.jpg", "_thumb.jpg")
        }
        ImageProcessor.saveThumbnail(enhanced, File(thumbPath))
        enhanced.recycle()

        invalidatePageCaches(page)

        val values = ContentValues().apply {
            put("thumb_image_path", thumbPath)
            put("corners_serialized", corners.toSerialized())
            put("rotation_degrees", ((rotationDegrees % 360) + 360) % 360)
            put("flip_horizontal", if (flipHorizontal) 1 else 0)
            put("filter_mode", filterMode.name)
            put("contrast_boost", contrastBoost)
            put("brightness_offset", brightnessOffset)
        }
        dbHelper.writableDatabase.update("pages", values, "id = ?", arrayOf(pageId.toString()))
        touchDocument(page.documentId)
        refreshAll()
    }

    suspend fun applyFilterAndAdjustToPage(
        pageId: Long,
        filterMode: FilterMode,
        contrastBoost: Float,
        brightnessOffset: Float
    ) = withContext(Dispatchers.IO) {
        val page = findPageById(pageId) ?: return@withContext
        val cropped = ImageProcessor.decodeFileNormalized(page.croppedImagePath) ?: return@withContext
        val enhanced = ImageProcessor.applyEnhancement(cropped, filterMode, contrastBoost, brightnessOffset)
        cropped.recycle()
        ImageProcessor.saveBitmapJpeg(enhanced, File(page.enhancedImagePath), 94)

        val thumbPath = page.thumbImagePath.ifEmpty {
            page.enhancedImagePath.replace("_enhanced.jpg", "_thumb.jpg")
        }
        ImageProcessor.saveThumbnail(enhanced, File(thumbPath))
        enhanced.recycle()

        invalidatePageCaches(page)

        val values = ContentValues().apply {
            put("thumb_image_path", thumbPath)
            put("filter_mode", filterMode.name)
            put("contrast_boost", contrastBoost)
            put("brightness_offset", brightnessOffset)
        }
        dbHelper.writableDatabase.update("pages", values, "id = ?", arrayOf(pageId.toString()))
        touchDocument(page.documentId)
        refreshAll()
    }

    suspend fun applyFilterToAllPages(documentId: Long, filterMode: FilterMode) {
        val doc = _documents.value.find { it.id == documentId } ?: return
        batchApplyFilter(documentId, doc.pages.map { it.id }.toSet(), filterMode)
    }

    suspend fun batchApplyFilter(
        documentId: Long,
        pageIds: Set<Long>,
        filterMode: FilterMode,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val doc = _documents.value.find { it.id == documentId } ?: return@withContext
        val targets = doc.pages.filter { pageIds.contains(it.id) }
        if (targets.isEmpty()) return@withContext
        val semaphore = Semaphore(4)
        val completed = AtomicInteger(0)

        coroutineScope {
            targets.map { page ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        val cropped = ImageProcessor.decodeFileNormalized(page.croppedImagePath) ?: return@withPermit
                        val enhanced = ImageProcessor.applyEnhancement(cropped, filterMode, page.contrastBoost, page.brightnessOffset)
                        cropped.recycle()
                        ImageProcessor.saveBitmapJpeg(enhanced, File(page.enhancedImagePath), 94)
                        val thumbPath = page.thumbImagePath.ifEmpty {
                            page.enhancedImagePath.replace("_enhanced.jpg", "_thumb.jpg")
                        }
                        ImageProcessor.saveThumbnail(enhanced, File(thumbPath))
                        enhanced.recycle()
                        invalidatePageCaches(page)

                        dbHelper.writableDatabase.update(
                            "pages",
                            ContentValues().apply {
                                put("thumb_image_path", thumbPath)
                                put("filter_mode", filterMode.name)
                            },
                            "id = ?",
                            arrayOf(page.id.toString())
                        )
                        synchronized(completed) {
                            onProgress(completed.incrementAndGet(), targets.size)
                        }
                    }
                }
            }.awaitAll()
        }
        touchDocument(documentId)
        refreshAll()
    }

    suspend fun batchRotatePages(
        documentId: Long,
        pageIds: Set<Long>,
        deltaDegrees: Int = 90,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val doc = _documents.value.find { it.id == documentId } ?: return@withContext
        val targets = doc.pages.filter { pageIds.contains(it.id) }
        if (targets.isEmpty()) return@withContext
        val semaphore = Semaphore(4)
        val completed = AtomicInteger(0)

        coroutineScope {
            targets.map { page ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        val newRot = (((page.rotationDegrees + deltaDegrees) % 360) + 360) % 360
                        val rawBmp = ImageProcessor.decodeFileNormalized(page.rawImagePath) ?: return@withPermit
                        val warped = ImageProcessor.warpAndOrient(rawBmp, page.corners, newRot, page.flipHorizontal)
                        rawBmp.recycle()
                        ImageProcessor.saveBitmapJpeg(warped, File(page.croppedImagePath), 95)

                        val enhanced = ImageProcessor.applyEnhancement(warped, page.filterMode, page.contrastBoost, page.brightnessOffset)
                        warped.recycle()
                        ImageProcessor.saveBitmapJpeg(enhanced, File(page.enhancedImagePath), 94)
                        val thumbPath = page.thumbImagePath.ifEmpty {
                            page.enhancedImagePath.replace("_enhanced.jpg", "_thumb.jpg")
                        }
                        ImageProcessor.saveThumbnail(enhanced, File(thumbPath))
                        enhanced.recycle()
                        invalidatePageCaches(page)

                        dbHelper.writableDatabase.update(
                            "pages",
                            ContentValues().apply {
                                put("thumb_image_path", thumbPath)
                                put("rotation_degrees", newRot)
                            },
                            "id = ?",
                            arrayOf(page.id.toString())
                        )
                        synchronized(completed) {
                            onProgress(completed.incrementAndGet(), targets.size)
                        }
                    }
                }
            }.awaitAll()
        }
        touchDocument(documentId)
        refreshAll()
    }

    suspend fun batchAutoCropPages(
        documentId: Long,
        pageIds: Set<Long>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val doc = _documents.value.find { it.id == documentId } ?: return@withContext
        val targets = doc.pages.filter { pageIds.contains(it.id) }
        if (targets.isEmpty()) return@withContext
        val semaphore = Semaphore(4)
        val completed = AtomicInteger(0)

        coroutineScope {
            targets.map { page ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        val rawBmp = ImageProcessor.decodeFileNormalized(page.rawImagePath) ?: return@withPermit
                        val corners = ImageProcessor.detectDocumentCorners(rawBmp)
                        val warped = ImageProcessor.warpAndOrient(rawBmp, corners, page.rotationDegrees, page.flipHorizontal)
                        rawBmp.recycle()
                        ImageProcessor.saveBitmapJpeg(warped, File(page.croppedImagePath), 95)

                        val enhanced = ImageProcessor.applyEnhancement(warped, page.filterMode, page.contrastBoost, page.brightnessOffset)
                        warped.recycle()
                        ImageProcessor.saveBitmapJpeg(enhanced, File(page.enhancedImagePath), 94)
                        val thumbPath = page.thumbImagePath.ifEmpty {
                            page.enhancedImagePath.replace("_enhanced.jpg", "_thumb.jpg")
                        }
                        ImageProcessor.saveThumbnail(enhanced, File(thumbPath))
                        enhanced.recycle()
                        invalidatePageCaches(page)

                        dbHelper.writableDatabase.update(
                            "pages",
                            ContentValues().apply {
                                put("thumb_image_path", thumbPath)
                                put("corners_serialized", corners.toSerialized())
                            },
                            "id = ?",
                            arrayOf(page.id.toString())
                        )
                        synchronized(completed) {
                            onProgress(completed.incrementAndGet(), targets.size)
                        }
                    }
                }
            }.awaitAll()
        }
        touchDocument(documentId)
        refreshAll()
    }

    suspend fun batchDeletePages(documentId: Long, pageIds: Set<Long>) = withContext(Dispatchers.IO) {
        val doc = _documents.value.find { it.id == documentId } ?: return@withContext
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            doc.pages.filter { pageIds.contains(it.id) }.forEach { page ->
                invalidatePageCaches(page)
                File(page.rawImagePath).delete()
                File(page.croppedImagePath).delete()
                File(page.enhancedImagePath).delete()
                if (page.thumbImagePath.isNotEmpty()) File(page.thumbImagePath).delete()
                db.delete("pages", "id = ?", arrayOf(page.id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        touchDocument(documentId)
        refreshAll()
    }

    suspend fun extractOcrForPage(pageId: Long): String = withContext(Dispatchers.IO) {
        val page = findPageById(pageId) ?: return@withContext ""
        val extracted = OcrEngine.extractTextFromFile(page.enhancedImagePath)
        dbHelper.writableDatabase.update(
            "pages",
            ContentValues().apply { put("ocr_text", extracted) },
            "id = ?",
            arrayOf(pageId.toString())
        )
        refreshAll()
        extracted
    }

    suspend fun indexDocumentOcrQuietly(documentId: Long) = withContext(Dispatchers.IO) {
        val doc = _documents.value.find { it.id == documentId } ?: return@withContext
        val unindexed = doc.pages.filter { it.ocrText.isBlank() }
        if (unindexed.isEmpty()) return@withContext
        val semaphore = Semaphore(2)
        var changed = false
        coroutineScope {
            unindexed.map { page ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        val text = OcrEngine.extractTextFromFile(page.enhancedImagePath)
                        if (text.isNotBlank()) {
                            dbHelper.writableDatabase.update(
                                "pages",
                                ContentValues().apply { put("ocr_text", text) },
                                "id = ?",
                                arrayOf(page.id.toString())
                            )
                            changed = true
                        }
                    }
                }
            }.awaitAll()
        }
        if (changed) refreshAll()
    }

    suspend fun stampSignatureOnPage(
        pageId: Long,
        strokes: List<List<NormalizedPoint>>,
        placement: SignaturePlacement,
        inkColorInt: Int
    ) = withContext(Dispatchers.IO) {
        val page = findPageById(pageId) ?: return@withContext
        val currentBmp = ImageProcessor.decodeFileNormalized(page.enhancedImagePath) ?: return@withContext
        val signed = ImageProcessor.stampSignatureOnBitmap(currentBmp, strokes, placement, inkColorInt)
        currentBmp.recycle()
        ImageProcessor.saveBitmapJpeg(signed, File(page.enhancedImagePath), 94)

        val thumbPath = page.thumbImagePath.ifEmpty {
            page.enhancedImagePath.replace("_enhanced.jpg", "_thumb.jpg")
        }
        ImageProcessor.saveThumbnail(signed, File(thumbPath))
        signed.recycle()
        invalidatePageCaches(page)

        touchDocument(page.documentId)
        refreshAll()
    }

    suspend fun drawStrokesOnPage(
        pageId: Long,
        strokes: List<DrawStroke>
    ) = withContext(Dispatchers.IO) {
        if (strokes.isEmpty()) return@withContext
        val page = findPageById(pageId) ?: return@withContext
        val currentBmp = ImageProcessor.decodeFileNormalized(page.enhancedImagePath) ?: return@withContext
        val drawn = ImageProcessor.drawStrokesOnBitmap(currentBmp, strokes)
        currentBmp.recycle()
        ImageProcessor.saveBitmapJpeg(drawn, File(page.enhancedImagePath), 94)

        val thumbPath = page.thumbImagePath.ifEmpty {
            page.enhancedImagePath.replace("_enhanced.jpg", "_thumb.jpg")
        }
        ImageProcessor.saveThumbnail(drawn, File(thumbPath))
        drawn.recycle()
        invalidatePageCaches(page)

        touchDocument(page.documentId)
        refreshAll()
    }

    suspend fun reorderPages(documentId: Long, orderedPageIds: List<Long>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            orderedPageIds.forEachIndexed { index, pageId ->
                val values = ContentValues().apply { put("order_index", index) }
                db.update("pages", values, "id = ? AND document_id = ?", arrayOf(pageId.toString(), documentId.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        touchDocument(documentId)
        refreshAll()
    }

    suspend fun deletePage(pageId: Long) = withContext(Dispatchers.IO) {
        val page = findPageById(pageId) ?: return@withContext
        invalidatePageCaches(page)
        File(page.rawImagePath).delete()
        File(page.croppedImagePath).delete()
        File(page.enhancedImagePath).delete()
        if (page.thumbImagePath.isNotEmpty()) File(page.thumbImagePath).delete()
        dbHelper.writableDatabase.delete("pages", "id = ?", arrayOf(pageId.toString()))
        touchDocument(page.documentId)
        refreshAll()
    }

    suspend fun renameDocument(documentId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return@withContext
        val values = ContentValues().apply {
            put("title", trimmed)
            put("updated_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.update("documents", values, "id = ?", arrayOf(documentId.toString()))
        refreshAll()
    }

    suspend fun updateDocumentFolderAndTag(documentId: Long, folderId: Long?, tag: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            if (folderId != null) put("folder_id", folderId) else putNull("folder_id")
            put("tag", tag.trim())
            put("updated_at", System.currentTimeMillis())
        }
        dbHelper.writableDatabase.update("documents", values, "id = ?", arrayOf(documentId.toString()))
        refreshAll()
    }

    suspend fun duplicateDocument(documentId: Long): Long = withContext(Dispatchers.IO) {
        val src = _documents.value.find { it.id == documentId } ?: return@withContext -1L
        val now = System.currentTimeMillis()
        val db = dbHelper.writableDatabase
        val newDocId = db.insert(
            "documents",
            null,
            ContentValues().apply {
                put("title", "${src.title} Copy")
                if (src.folderId != null) put("folder_id", src.folderId) else putNull("folder_id")
                put("tag", src.tag)
                put("created_at", now)
                put("updated_at", now)
            }
        )
        val docDir = File(pagesRootDir, "doc_$newDocId").apply { mkdirs() }
        src.pages.forEachIndexed { index, p ->
            val stamp = "${now}_$index"
            val newRaw = File(docDir, "${stamp}_raw.jpg")
            val newCrop = File(docDir, "${stamp}_cropped.jpg")
            val newEnh = File(docDir, "${stamp}_enhanced.jpg")
            val newThumb = File(docDir, "${stamp}_thumb.jpg")
            File(p.rawImagePath).takeIf { it.exists() }?.copyTo(newRaw, overwrite = true)
            File(p.croppedImagePath).takeIf { it.exists() }?.copyTo(newCrop, overwrite = true)
            File(p.enhancedImagePath).takeIf { it.exists() }?.copyTo(newEnh, overwrite = true)
            File(p.thumbImagePath).takeIf { p.thumbImagePath.isNotEmpty() && it.exists() }?.copyTo(newThumb, overwrite = true)

            db.insert(
                "pages",
                null,
                ContentValues().apply {
                    put("document_id", newDocId)
                    put("order_index", index)
                    put("raw_image_path", newRaw.absolutePath)
                    put("cropped_image_path", newCrop.absolutePath)
                    put("enhanced_image_path", newEnh.absolutePath)
                    put("thumb_image_path", if (newThumb.exists()) newThumb.absolutePath else "")
                    put("corners_serialized", p.corners.toSerialized())
                    put("rotation_degrees", p.rotationDegrees)
                    put("flip_horizontal", if (p.flipHorizontal) 1 else 0)
                    put("filter_mode", p.filterMode.name)
                    put("contrast_boost", p.contrastBoost)
                    put("brightness_offset", p.brightnessOffset)
                    put("ocr_text", p.ocrText)
                }
            )
        }
        refreshAll()
        newDocId
    }

    suspend fun deleteDocument(documentId: Long) = withContext(Dispatchers.IO) {
        _documents.value.find { it.id == documentId }?.pages?.forEach { invalidatePageCaches(it) }
        File(pagesRootDir, "doc_$documentId").deleteRecursively()
        dbHelper.writableDatabase.delete("documents", "id = ?", arrayOf(documentId.toString()))
        refreshAll()
    }

    suspend fun exportDocumentToPdf(
        documentId: Long,
        pdfTitle: String,
        pageSize: PdfPageSize,
        qualityPreset: PdfQualityPreset,
        pageLayout: PdfPageLayout = PdfPageLayout.SINGLE,
        margin: PdfMargin = PdfMargin.NARROW,
        watermarkText: String = "",
        includePageNumbers: Boolean = false,
        password: String = "",
        selectedPageIds: Set<Long>? = null
    ): PdfFileModel? = withContext(Dispatchers.IO) {
        val doc = _documents.value.find { it.id == documentId } ?: return@withContext null
        val pagesToExport = if (selectedPageIds.isNullOrEmpty()) doc.pages else doc.pages.filter { selectedPageIds.contains(it.id) }
        if (pagesToExport.isEmpty()) return@withContext null

        val cleanTitle = pdfTitle.trim().ifEmpty { doc.title }
        val fileName = "${sanitizeFileName(cleanTitle)}_${System.currentTimeMillis()}.pdf"
        val outFile = File(pdfsRootDir, fileName)
        val inputs = pagesToExport.map { it.enhancedImagePath to it.filterMode }
        val (bytesWritten, outputPages) = PdfEngine.buildPdfFromImages(
            imageFiles = inputs,
            outFile = outFile,
            pageSize = pageSize,
            qualityPreset = qualityPreset,
            pageLayout = pageLayout,
            marginPt = margin.marginPt,
            watermarkText = watermarkText.trim(),
            includePageNumbers = includePageNumbers,
            password = password
        )

        val pdfId = insertPdfRecord(
            title = cleanTitle,
            filePath = outFile.absolutePath,
            sizeBytes = bytesWritten,
            pageCount = outputPages,
            pageSize = pageSize,
            qualityPreset = qualityPreset,
            isProtected = password.isNotEmpty(),
            watermarkText = watermarkText.trim(),
            folderName = doc.folderName,
            documentId = documentId
        )

        dbHelper.writableDatabase.update(
            "documents",
            ContentValues().apply {
                put("last_pdf_id", pdfId)
                put("last_pdf_size_bytes", bytesWritten)
                put("updated_at", System.currentTimeMillis())
            },
            "id = ?",
            arrayOf(documentId.toString())
        )
        refreshAll()
        _pdfs.value.find { it.id == pdfId }
    }

    suspend fun importExternalPdf(uri: Uri): PdfFileModel? = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri)?.removeSuffix(".pdf") ?: "Imported_${System.currentTimeMillis()}"
        val outFile = File(pdfsRootDir, "${sanitizeFileName(displayName)}_${System.currentTimeMillis()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: return@withContext null

        val pageCount = PdfEngine.getPdfPageCount(outFile.absolutePath).coerceAtLeast(1)
        val pdfId = insertPdfRecord(
            title = displayName,
            filePath = outFile.absolutePath,
            sizeBytes = outFile.length(),
            pageCount = pageCount,
            pageSize = PdfPageSize.A4,
            qualityPreset = PdfQualityPreset.HIGH,
            isProtected = false,
            watermarkText = "",
            folderName = null,
            documentId = null
        )
        refreshAll()
        _pdfs.value.find { it.id == pdfId }
    }

    suspend fun mergePdfs(
        sourcePdfIds: List<Long>,
        mergedTitle: String,
        pageSize: PdfPageSize,
        qualityPreset: PdfQualityPreset,
        password: String = ""
    ): PdfFileModel? = withContext(Dispatchers.IO) {
        val selectedPdfs = sourcePdfIds.mapNotNull { id -> _pdfs.value.find { it.id == id } }
        if (selectedPdfs.size < 2) return@withContext null
        val cleanTitle = mergedTitle.trim().ifEmpty { "Merged PDF" }
        val outFile = File(pdfsRootDir, "${sanitizeFileName(cleanTitle)}_${System.currentTimeMillis()}.pdf")
        val (sizeBytes, totalPages) = PdfEngine.mergePdfFiles(
            sourcePdfPaths = selectedPdfs.map { it.filePath },
            outFile = outFile,
            pageSize = pageSize,
            qualityPreset = qualityPreset,
            password = password
        )
        val pdfId = insertPdfRecord(
            title = cleanTitle,
            filePath = outFile.absolutePath,
            sizeBytes = sizeBytes,
            pageCount = totalPages,
            pageSize = pageSize,
            qualityPreset = qualityPreset,
            isProtected = password.isNotEmpty(),
            watermarkText = "",
            folderName = null,
            documentId = null
        )
        refreshAll()
        _pdfs.value.find { it.id == pdfId }
    }

    suspend fun splitOrReorderPdf(
        sourcePdfId: Long,
        zeroBasedOrder: List<Int>,
        newTitle: String,
        pageSize: PdfPageSize = PdfPageSize.A4,
        qualityPreset: PdfQualityPreset = PdfQualityPreset.HIGH
    ): PdfFileModel? = withContext(Dispatchers.IO) {
        val src = _pdfs.value.find { it.id == sourcePdfId } ?: return@withContext null
        val cleanTitle = newTitle.trim().ifEmpty { "${src.title} Edited" }
        val outFile = File(pdfsRootDir, "${sanitizeFileName(cleanTitle)}_${System.currentTimeMillis()}.pdf")
        val sizeBytes = PdfEngine.extractOrReorderPdfPages(
            sourcePdfPath = src.filePath,
            zeroBasedOrder = zeroBasedOrder,
            outFile = outFile,
            pageSize = pageSize,
            qualityPreset = qualityPreset
        )
        val pdfId = insertPdfRecord(
            title = cleanTitle,
            filePath = outFile.absolutePath,
            sizeBytes = sizeBytes,
            pageCount = zeroBasedOrder.size,
            pageSize = pageSize,
            qualityPreset = qualityPreset,
            isProtected = false,
            watermarkText = "",
            folderName = src.folderName,
            documentId = null
        )
        refreshAll()
        _pdfs.value.find { it.id == pdfId }
    }

    suspend fun compressPdf(
        sourcePdfId: Long,
        qualityPreset: PdfQualityPreset
    ): Pair<PdfFileModel, Long>? = withContext(Dispatchers.IO) {
        val src = _pdfs.value.find { it.id == sourcePdfId } ?: return@withContext null
        val oldSize = src.sizeBytes
        val newTitle = "${src.title} (${qualityPreset.label})"
        val outFile = File(pdfsRootDir, "${sanitizeFileName(newTitle)}_${System.currentTimeMillis()}.pdf")
        val (newSize, pageCount) = PdfEngine.compressPdfFile(
            sourcePdfPath = src.filePath,
            outFile = outFile,
            qualityPreset = qualityPreset,
            pageSize = src.pageSize
        )
        val pdfId = insertPdfRecord(
            title = newTitle,
            filePath = outFile.absolutePath,
            sizeBytes = newSize,
            pageCount = pageCount,
            pageSize = src.pageSize,
            qualityPreset = qualityPreset,
            isProtected = false,
            watermarkText = "",
            folderName = src.folderName,
            documentId = null
        )
        refreshAll()
        val created = _pdfs.value.find { it.id == pdfId } ?: return@withContext null
        created to oldSize
    }

    suspend fun protectPdf(
        sourcePdfId: Long,
        password: String
    ): PdfFileModel? = withContext(Dispatchers.IO) {
        val src = _pdfs.value.find { it.id == sourcePdfId } ?: return@withContext null
        val newTitle = "${src.title} (Locked)"
        val outFile = File(pdfsRootDir, "${sanitizeFileName(newTitle)}_${System.currentTimeMillis()}.pdf")
        val (sizeBytes, pageCount) = PdfEngine.protectPdfFile(
            sourcePdfPath = src.filePath,
            outFile = outFile,
            password = password,
            qualityPreset = src.qualityPreset,
            pageSize = src.pageSize
        )
        val pdfId = insertPdfRecord(
            title = newTitle,
            filePath = outFile.absolutePath,
            sizeBytes = sizeBytes,
            pageCount = pageCount,
            pageSize = src.pageSize,
            qualityPreset = src.qualityPreset,
            isProtected = true,
            watermarkText = src.watermarkText,
            folderName = src.folderName,
            documentId = null
        )
        refreshAll()
        _pdfs.value.find { it.id == pdfId }
    }

    suspend fun convertPdfToScanDocument(sourcePdfId: Long): Long = withContext(Dispatchers.IO) {
        val src = _pdfs.value.find { it.id == sourcePdfId } ?: return@withContext -1L
        val tempDir = File(context.cacheDir, "pdf_to_scan_${System.currentTimeMillis()}")
        val pageFiles = PdfEngine.renderPdfPagesToImageFiles(src.filePath, tempDir, 2600)
        if (pageFiles.isEmpty()) {
            tempDir.deleteRecursively()
            return@withContext -1L
        }
        val docId = createDocumentFromImageFiles(
            imageFiles = pageFiles,
            title = src.title,
            autoDetectCorners = false,
            defaultFilter = FilterMode.ORIGINAL
        )
        tempDir.deleteRecursively()
        docId
    }

    suspend fun annotatePdfWithStrokes(
        sourcePdfId: Long,
        annotationsByPage: Map<Int, List<DrawStroke>>,
        saveAsCopy: Boolean
    ): PdfFileModel? = withContext(Dispatchers.IO) {
        val src = _pdfs.value.find { it.id == sourcePdfId } ?: return@withContext null
        val cleanTitle = if (saveAsCopy) "${src.title} Drawn" else src.title
        val targetFile = if (saveAsCopy) {
            File(pdfsRootDir, "${sanitizeFileName(cleanTitle)}_${System.currentTimeMillis()}.pdf")
        } else {
            File(src.filePath)
        }
        val tempOut = File(pdfsRootDir, "tmp_draw_${System.currentTimeMillis()}.pdf")
        val (sizeBytes, pageCount) = PdfEngine.annotatePdfPages(
            sourcePdfPath = src.filePath,
            annotationsByPage = annotationsByPage,
            outFile = tempOut,
            pageSize = src.pageSize,
            qualityPreset = src.qualityPreset
        )
        if (saveAsCopy) {
            tempOut.renameTo(targetFile)
            val newId = insertPdfRecord(
                title = cleanTitle,
                filePath = targetFile.absolutePath,
                sizeBytes = sizeBytes,
                pageCount = pageCount,
                pageSize = src.pageSize,
                qualityPreset = src.qualityPreset,
                isProtected = false,
                watermarkText = src.watermarkText,
                folderName = src.folderName,
                documentId = src.documentId
            )
            refreshAll()
            _pdfs.value.find { it.id == newId }
        } else {
            if (targetFile.exists()) targetFile.delete()
            tempOut.renameTo(targetFile)
            ScannerCache.invalidatePath(targetFile.absolutePath)
            dbHelper.writableDatabase.update(
                "pdf_files",
                ContentValues().apply {
                    put("size_bytes", sizeBytes)
                    put("page_count", pageCount)
                },
                "id = ?",
                arrayOf(sourcePdfId.toString())
            )
            refreshAll()
            _pdfs.value.find { it.id == sourcePdfId }
        }
    }

    suspend fun renamePdf(pdfId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return@withContext
        dbHelper.writableDatabase.update(
            "pdf_files",
            ContentValues().apply { put("title", trimmed) },
            "id = ?",
            arrayOf(pdfId.toString())
        )
        refreshAll()
    }

    suspend fun duplicatePdf(pdfId: Long): Long = withContext(Dispatchers.IO) {
        val src = _pdfs.value.find { it.id == pdfId } ?: return@withContext -1L
        val srcFile = File(src.filePath)
        if (!srcFile.exists()) return@withContext -1L
        val newTitle = "${src.title} Copy"
        val copyFile = File(pdfsRootDir, "${sanitizeFileName(newTitle)}_${System.currentTimeMillis()}.pdf")
        srcFile.copyTo(copyFile, overwrite = true)

        val newId = insertPdfRecord(
            title = newTitle,
            filePath = copyFile.absolutePath,
            sizeBytes = copyFile.length(),
            pageCount = src.pageCount,
            pageSize = src.pageSize,
            qualityPreset = src.qualityPreset,
            isProtected = src.isPasswordProtected,
            watermarkText = src.watermarkText,
            folderName = src.folderName,
            documentId = null
        )
        refreshAll()
        newId
    }

    suspend fun deletePdf(pdfId: Long) = withContext(Dispatchers.IO) {
        val pdf = _pdfs.value.find { it.id == pdfId }
        if (pdf != null) {
            ScannerCache.invalidatePath(pdf.filePath)
            File(pdf.filePath).delete()
        }
        dbHelper.writableDatabase.delete("pdf_files", "id = ?", arrayOf(pdfId.toString()))
        refreshAll()
    }

    private fun invalidatePageCaches(page: PageModel) {
        ScannerCache.invalidatePath(page.rawImagePath)
        ScannerCache.invalidatePath(page.croppedImagePath)
        ScannerCache.invalidatePath(page.enhancedImagePath)
        if (page.thumbImagePath.isNotEmpty()) ScannerCache.invalidatePath(page.thumbImagePath)
    }

    private fun insertPdfRecord(
        title: String,
        filePath: String,
        sizeBytes: Long,
        pageCount: Int,
        pageSize: PdfPageSize,
        qualityPreset: PdfQualityPreset,
        isProtected: Boolean,
        watermarkText: String,
        folderName: String?,
        documentId: Long?
    ): Long {
        return dbHelper.writableDatabase.insert(
            "pdf_files",
            null,
            ContentValues().apply {
                put("title", title)
                put("file_path", filePath)
                put("size_bytes", sizeBytes)
                put("page_count", pageCount)
                put("page_size", pageSize.name)
                put("quality_preset", qualityPreset.name)
                put("is_password_protected", if (isProtected) 1 else 0)
                put("watermark_text", watermarkText)
                if (folderName != null) put("folder_name", folderName) else putNull("folder_name")
                if (documentId != null) put("document_id", documentId) else putNull("document_id")
                put("created_at", System.currentTimeMillis())
            }
        )
    }

    private fun findPageById(pageId: Long): PageModel? {
        return _documents.value.asSequence().flatMap { it.pages.asSequence() }.find { it.id == pageId }
    }

    private fun touchDocument(documentId: Long) {
        dbHelper.writableDatabase.update(
            "documents",
            ContentValues().apply { put("updated_at", System.currentTimeMillis()) },
            "id = ?",
            arrayOf(documentId.toString())
        )
    }

    private fun resolveDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return null
    }

    private fun defaultDocTitle(timestamp: Long): String {
        val fmt = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
        return "Scan ${fmt.format(Date(timestamp))}"
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40).ifEmpty { "NaimScan" }
}
