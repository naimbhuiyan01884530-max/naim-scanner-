package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.example.engine.ImageProcessor
import com.example.engine.PdfEngine
import com.example.model.FilterMode
import com.example.model.FolderModel
import com.example.model.PageModel
import com.example.model.PdfFileModel
import com.example.model.PdfPageSize
import com.example.model.PdfQualityPreset
import com.example.model.QuadCorners
import com.example.model.ScanDocumentModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                   corners_serialized, rotation_degrees, flip_horizontal, filter_mode, contrast_boost, brightness_offset
            FROM pages ORDER BY order_index ASC, id ASC
            """.trimIndent(),
            null
        ).use { c ->
            while (c.moveToNext()) {
                val docId = c.getLong(1)
                val mode = runCatching { FilterMode.valueOf(c.getString(9)) }.getOrDefault(FilterMode.SHARP_BW)
                val page = PageModel(
                    id = c.getLong(0),
                    documentId = docId,
                    orderIndex = c.getInt(2),
                    rawImagePath = c.getString(3),
                    croppedImagePath = c.getString(4),
                    enhancedImagePath = c.getString(5),
                    corners = QuadCorners.fromSerialized(c.getString(6)),
                    rotationDegrees = c.getInt(7),
                    flipHorizontal = c.getInt(8) == 1,
                    filterMode = mode,
                    contrastBoost = c.getFloat(10),
                    brightnessOffset = c.getFloat(11)
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
        defaultFilter: FilterMode = FilterMode.SHARP_BW
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val docTitle = title?.takeIf { it.isNotBlank() } ?: defaultDocTitle(now)
        val db = dbHelper.writableDatabase
        val docValues = ContentValues().apply {
            put("title", docTitle)
            if (folderId != null) put("folder_id", folderId) else putNull("folder_id")
            put("tag", "")
            put("created_at", now)
            put("updated_at", now)
        }
        val docId = db.insert("documents", null, docValues)
        imageFiles.forEachIndexed { idx, srcFile ->
            addPageInternal(docId, idx, srcFile, autoDetectCorners, defaultFilter)
        }
        refreshAll()
        docId
    }

    suspend fun createDocumentFromUris(
        uris: List<Uri>,
        folderId: Long? = null,
        autoDetectCorners: Boolean = true,
        defaultFilter: FilterMode = FilterMode.SHARP_BW
    ): Long = withContext(Dispatchers.IO) {
        val tempFiles = mutableListOf<File>()
        uris.forEachIndexed { idx, uri ->
            val bmp = ImageProcessor.decodeUriNormalized(context, uri)
            if (bmp != null) {
                val tmp = File(context.cacheDir, "import_${System.currentTimeMillis()}_$idx.jpg")
                ImageProcessor.saveBitmapJpeg(bmp, tmp, 94)
                bmp.recycle()
                tempFiles.add(tmp)
            }
        }
        if (tempFiles.isEmpty()) return@withContext -1L
        val docId = createDocumentFromImageFiles(tempFiles, null, folderId, autoDetectCorners, defaultFilter)
        tempFiles.forEach { it.delete() }
        docId
    }

    suspend fun addPagesFromUris(
        documentId: Long,
        uris: List<Uri>,
        autoDetectCorners: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val currentPages = _documents.value.find { it.id == documentId }?.pages ?: emptyList()
        var nextIndex = (currentPages.maxOfOrNull { it.orderIndex } ?: -1) + 1
        for (uri in uris) {
            val bmp = ImageProcessor.decodeUriNormalized(context, uri) ?: continue
            val tmp = File(context.cacheDir, "add_${System.currentTimeMillis()}_$nextIndex.jpg")
            ImageProcessor.saveBitmapJpeg(bmp, tmp, 94)
            bmp.recycle()
            addPageInternal(documentId, nextIndex++, tmp, autoDetectCorners, FilterMode.SHARP_BW)
            tmp.delete()
        }
        touchDocument(documentId)
        refreshAll()
    }

    suspend fun addPageFromFile(
        documentId: Long,
        sourceFile: File,
        autoDetectCorners: Boolean = true
    ): Long = withContext(Dispatchers.IO) {
        val currentPages = _documents.value.find { it.id == documentId }?.pages ?: emptyList()
        val nextIndex = (currentPages.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val pageId = addPageInternal(documentId, nextIndex, sourceFile, autoDetectCorners, FilterMode.SHARP_BW)
        touchDocument(documentId)
        refreshAll()
        pageId
    }

    suspend fun replacePageWithFile(pageId: Long, newSourceFile: File) = withContext(Dispatchers.IO) {
        val page = findPageById(pageId) ?: return@withContext
        val rawBmp = ImageProcessor.decodeFileNormalized(newSourceFile.absolutePath) ?: return@withContext
        val rawFile = File(page.rawImagePath)
        ImageProcessor.saveBitmapJpeg(rawBmp, rawFile, 94)

        val corners = ImageProcessor.detectDocumentCorners(rawBmp)
        val warped = ImageProcessor.warpAndOrient(rawBmp, corners, 0, false)
        rawBmp.recycle()
        ImageProcessor.saveBitmapJpeg(warped, File(page.croppedImagePath), 94)

        val enhanced = ImageProcessor.applyEnhancement(warped, page.filterMode)
        warped.recycle()
        ImageProcessor.saveBitmapJpeg(enhanced, File(page.enhancedImagePath), 92)
        enhanced.recycle()

        val values = ContentValues().apply {
            put("corners_serialized", corners.toSerialized())
            put("rotation_degrees", 0)
            put("flip_horizontal", 0)
        }
        dbHelper.writableDatabase.update("pages", values, "id = ?", arrayOf(pageId.toString()))
        touchDocument(page.documentId)
        refreshAll()
    }

    private fun addPageInternal(
        documentId: Long,
        orderIndex: Int,
        sourceFile: File,
        autoDetectCorners: Boolean,
        filterMode: FilterMode
    ): Long {
        val docDir = File(pagesRootDir, "doc_$documentId").apply { mkdirs() }
        val stamp = "${System.currentTimeMillis()}_$orderIndex"
        val rawFile = File(docDir, "${stamp}_raw.jpg")
        val croppedFile = File(docDir, "${stamp}_cropped.jpg")
        val enhancedFile = File(docDir, "${stamp}_enhanced.jpg")

        val rawBmp = ImageProcessor.decodeFileNormalized(sourceFile.absolutePath) ?: return -1L
        ImageProcessor.saveBitmapJpeg(rawBmp, rawFile, 94)

        val corners = if (autoDetectCorners) ImageProcessor.detectDocumentCorners(rawBmp) else QuadCorners.FULL
        val warped = ImageProcessor.warpAndOrient(rawBmp, corners, 0, false)
        rawBmp.recycle()
        ImageProcessor.saveBitmapJpeg(warped, croppedFile, 94)

        val enhanced = ImageProcessor.applyEnhancement(warped, filterMode)
        warped.recycle()
        ImageProcessor.saveBitmapJpeg(enhanced, enhancedFile, 92)
        enhanced.recycle()

        val values = ContentValues().apply {
            put("document_id", documentId)
            put("order_index", orderIndex)
            put("raw_image_path", rawFile.absolutePath)
            put("cropped_image_path", croppedFile.absolutePath)
            put("enhanced_image_path", enhancedFile.absolutePath)
            put("corners_serialized", corners.toSerialized())
            put("rotation_degrees", 0)
            put("flip_horizontal", 0)
            put("filter_mode", filterMode.name)
            put("contrast_boost", 1.0f)
            put("brightness_offset", 0f)
        }
        return dbHelper.writableDatabase.insert("pages", null, values)
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
        ImageProcessor.saveBitmapJpeg(warped, File(page.croppedImagePath), 94)

        val enhanced = ImageProcessor.applyEnhancement(warped, filterMode, contrastBoost, brightnessOffset)
        warped.recycle()
        ImageProcessor.saveBitmapJpeg(enhanced, File(page.enhancedImagePath), 92)
        enhanced.recycle()

        val values = ContentValues().apply {
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

    suspend fun applyFilterToPage(pageId: Long, filterMode: FilterMode) = withContext(Dispatchers.IO) {
        val page = findPageById(pageId) ?: return@withContext
        val cropped = ImageProcessor.decodeFileNormalized(page.croppedImagePath) ?: return@withContext
        val enhanced = ImageProcessor.applyEnhancement(cropped, filterMode, page.contrastBoost, page.brightnessOffset)
        cropped.recycle()
        ImageProcessor.saveBitmapJpeg(enhanced, File(page.enhancedImagePath), 92)
        enhanced.recycle()

        val values = ContentValues().apply { put("filter_mode", filterMode.name) }
        dbHelper.writableDatabase.update("pages", values, "id = ?", arrayOf(pageId.toString()))
        touchDocument(page.documentId)
        refreshAll()
    }

    suspend fun applyFilterToAllPages(documentId: Long, filterMode: FilterMode) = withContext(Dispatchers.IO) {
        val doc = _documents.value.find { it.id == documentId } ?: return@withContext
        doc.pages.forEach { page ->
            val cropped = ImageProcessor.decodeFileNormalized(page.croppedImagePath) ?: return@forEach
            val enhanced = ImageProcessor.applyEnhancement(cropped, filterMode, page.contrastBoost, page.brightnessOffset)
            cropped.recycle()
            ImageProcessor.saveBitmapJpeg(enhanced, File(page.enhancedImagePath), 92)
            enhanced.recycle()

            val values = ContentValues().apply { put("filter_mode", filterMode.name) }
            dbHelper.writableDatabase.update("pages", values, "id = ?", arrayOf(page.id.toString()))
        }
        touchDocument(documentId)
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
        File(page.rawImagePath).delete()
        File(page.croppedImagePath).delete()
        File(page.enhancedImagePath).delete()
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
            File(p.rawImagePath).takeIf { it.exists() }?.copyTo(newRaw, overwrite = true)
            File(p.croppedImagePath).takeIf { it.exists() }?.copyTo(newCrop, overwrite = true)
            File(p.enhancedImagePath).takeIf { it.exists() }?.copyTo(newEnh, overwrite = true)

            db.insert(
                "pages",
                null,
                ContentValues().apply {
                    put("document_id", newDocId)
                    put("order_index", index)
                    put("raw_image_path", newRaw.absolutePath)
                    put("cropped_image_path", newCrop.absolutePath)
                    put("enhanced_image_path", newEnh.absolutePath)
                    put("corners_serialized", p.corners.toSerialized())
                    put("rotation_degrees", p.rotationDegrees)
                    put("flip_horizontal", if (p.flipHorizontal) 1 else 0)
                    put("filter_mode", p.filterMode.name)
                    put("contrast_boost", p.contrastBoost)
                    put("brightness_offset", p.brightnessOffset)
                }
            )
        }
        refreshAll()
        newDocId
    }

    suspend fun deleteDocument(documentId: Long) = withContext(Dispatchers.IO) {
        File(pagesRootDir, "doc_$documentId").deleteRecursively()
        dbHelper.writableDatabase.delete("documents", "id = ?", arrayOf(documentId.toString()))
        refreshAll()
    }

    suspend fun exportDocumentToPdf(
        documentId: Long,
        pdfTitle: String,
        pageSize: PdfPageSize,
        qualityPreset: PdfQualityPreset,
        watermarkText: String,
        password: String
    ): PdfFileModel? = withContext(Dispatchers.IO) {
        val doc = _documents.value.find { it.id == documentId } ?: return@withContext null
        if (doc.pages.isEmpty()) return@withContext null

        val cleanTitle = pdfTitle.trim().ifEmpty { doc.title }
        val fileName = "${sanitizeFileName(cleanTitle)}_${System.currentTimeMillis()}.pdf"
        val outFile = File(pdfsRootDir, fileName)
        val inputs = doc.pages.map { it.enhancedImagePath to it.filterMode }
        val bytesWritten = PdfEngine.buildPdfFromImages(
            imageFiles = inputs,
            outFile = outFile,
            pageSize = pageSize,
            qualityPreset = qualityPreset,
            watermarkText = watermarkText.trim(),
            password = password
        )

        val now = System.currentTimeMillis()
        val db = dbHelper.writableDatabase
        val pdfId = db.insert(
            "pdf_files",
            null,
            ContentValues().apply {
                put("title", cleanTitle)
                put("file_path", outFile.absolutePath)
                put("size_bytes", bytesWritten)
                put("page_count", doc.pages.size)
                put("page_size", pageSize.name)
                put("quality_preset", qualityPreset.name)
                put("is_password_protected", if (password.isNotEmpty()) 1 else 0)
                put("watermark_text", watermarkText.trim())
                put("folder_name", doc.folderName)
                put("document_id", documentId)
                put("created_at", now)
            }
        )

        db.update(
            "documents",
            ContentValues().apply {
                put("last_pdf_id", pdfId)
                put("last_pdf_size_bytes", bytesWritten)
                put("updated_at", now)
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
        val now = System.currentTimeMillis()
        val pdfId = dbHelper.writableDatabase.insert(
            "pdf_files",
            null,
            ContentValues().apply {
                put("title", displayName)
                put("file_path", outFile.absolutePath)
                put("size_bytes", outFile.length())
                put("page_count", pageCount)
                put("page_size", PdfPageSize.A4.name)
                put("quality_preset", PdfQualityPreset.BALANCED.name)
                put("is_password_protected", 0)
                put("watermark_text", "")
                putNull("folder_name")
                putNull("document_id")
                put("created_at", now)
            }
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
        val now = System.currentTimeMillis()
        val pdfId = dbHelper.writableDatabase.insert(
            "pdf_files",
            null,
            ContentValues().apply {
                put("title", cleanTitle)
                put("file_path", outFile.absolutePath)
                put("size_bytes", sizeBytes)
                put("page_count", totalPages)
                put("page_size", pageSize.name)
                put("quality_preset", qualityPreset.name)
                put("is_password_protected", if (password.isNotEmpty()) 1 else 0)
                put("watermark_text", "")
                putNull("folder_name")
                putNull("document_id")
                put("created_at", now)
            }
        )
        refreshAll()
        _pdfs.value.find { it.id == pdfId }
    }

    suspend fun splitExtractPdf(
        sourcePdfId: Long,
        zeroBasedPageIndices: List<Int>,
        newTitle: String,
        pageSize: PdfPageSize = PdfPageSize.A4,
        qualityPreset: PdfQualityPreset = PdfQualityPreset.BALANCED
    ): PdfFileModel? = withContext(Dispatchers.IO) {
        val src = _pdfs.value.find { it.id == sourcePdfId } ?: return@withContext null
        val cleanTitle = newTitle.trim().ifEmpty { "${src.title} Extract" }
        val outFile = File(pdfsRootDir, "${sanitizeFileName(cleanTitle)}_${System.currentTimeMillis()}.pdf")
        val sizeBytes = PdfEngine.extractPdfPages(
            sourcePdfPath = src.filePath,
            zeroBasedIndices = zeroBasedPageIndices,
            outFile = outFile,
            pageSize = pageSize,
            qualityPreset = qualityPreset
        )
        val now = System.currentTimeMillis()
        val pdfId = dbHelper.writableDatabase.insert(
            "pdf_files",
            null,
            ContentValues().apply {
                put("title", cleanTitle)
                put("file_path", outFile.absolutePath)
                put("size_bytes", sizeBytes)
                put("page_count", zeroBasedPageIndices.distinct().size)
                put("page_size", pageSize.name)
                put("quality_preset", qualityPreset.name)
                put("is_password_protected", 0)
                put("watermark_text", "")
                put("folder_name", src.folderName)
                putNull("document_id")
                put("created_at", now)
            }
        )
        refreshAll()
        _pdfs.value.find { it.id == pdfId }
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

        val newId = dbHelper.writableDatabase.insert(
            "pdf_files",
            null,
            ContentValues().apply {
                put("title", newTitle)
                put("file_path", copyFile.absolutePath)
                put("size_bytes", copyFile.length())
                put("page_count", src.pageCount)
                put("page_size", src.pageSize.name)
                put("quality_preset", src.qualityPreset.name)
                put("is_password_protected", if (src.isPasswordProtected) 1 else 0)
                put("watermark_text", src.watermarkText)
                if (src.folderName != null) put("folder_name", src.folderName) else putNull("folder_name")
                putNull("document_id")
                put("created_at", System.currentTimeMillis())
            }
        )
        refreshAll()
        newId
    }

    suspend fun deletePdf(pdfId: Long) = withContext(Dispatchers.IO) {
        val pdf = _pdfs.value.find { it.id == pdfId }
        if (pdf != null) File(pdf.filePath).delete()
        dbHelper.writableDatabase.delete("pdf_files", "id = ?", arrayOf(pdfId.toString()))
        refreshAll()
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
