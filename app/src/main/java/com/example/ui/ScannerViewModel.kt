package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ScannerRepository
import com.example.engine.ImageProcessor
import com.example.engine.PdfEngine
import com.example.model.FilterMode
import com.example.model.FolderModel
import com.example.model.NormalizedPoint
import com.example.model.PdfFileModel
import com.example.model.PdfMargin
import com.example.model.PdfPageLayout
import com.example.model.PdfPageSize
import com.example.model.PdfQualityPreset
import com.example.model.QuadCorners
import com.example.model.ScanDocumentModel
import com.example.model.SignaturePlacement
import com.example.util.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class VaultTab { SCANS, PDFS }

data class ScannerUiState(
    val activeTab: VaultTab = VaultTab.SCANS,
    val searchQuery: String = "",
    val selectedFolderId: Long? = null,
    val folders: List<FolderModel> = emptyList(),
    val documents: List<ScanDocumentModel> = emptyList(),
    val pdfs: List<PdfFileModel> = emptyList(),
    val isBusy: Boolean = false,
    val busyMessage: String = "",
    val busyProgress: Float? = null,
    val recentPdfResult: PdfFileModel? = null,
    val statusMessage: String? = null
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScannerRepository(application)

    private val activeTab = MutableStateFlow(VaultTab.SCANS)
    private val searchQuery = MutableStateFlow("")
    private val selectedFolderId = MutableStateFlow<Long?>(null)
    private val busyState = MutableStateFlow<Triple<Boolean, String, Float?>>(Triple(false, "", null))
    private val recentPdfResult = MutableStateFlow<PdfFileModel?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)

    private data class FilterCriteria(
        val tab: VaultTab,
        val query: String,
        val folderId: Long?,
        val busy: Triple<Boolean, String, Float?>,
        val recentPdf: PdfFileModel?,
        val message: String?
    )

    private val filterFlow = combine(
        activeTab,
        searchQuery,
        selectedFolderId,
        busyState,
        recentPdfResult
    ) { tab, query, folderId, busy, recentPdf ->
        FilterCriteria(tab, query, folderId, busy, recentPdf, null)
    }.combine(statusMessage) { criteria, msg ->
        criteria.copy(message = msg)
    }

    val uiState: StateFlow<ScannerUiState> = combine(
        filterFlow,
        repository.folders,
        repository.documents,
        repository.pdfs
    ) { criteria, folders, docs, pdfs ->
        val q = criteria.query.trim().lowercase()
        val selectedFolderName = criteria.folderId?.let { id -> folders.find { it.id == id }?.name }

        val filteredDocs = docs.filter { doc ->
            val matchesFolder = criteria.folderId == null || doc.folderId == criteria.folderId
            val matchesQuery = q.isEmpty() ||
                doc.title.lowercase().contains(q) ||
                doc.tag.lowercase().contains(q) ||
                (doc.folderName?.lowercase()?.contains(q) == true) ||
                doc.pages.any { it.ocrText.lowercase().contains(q) }
            matchesFolder && matchesQuery
        }

        val filteredPdfs = pdfs.filter { pdf ->
            val matchesFolder = selectedFolderName == null || pdf.folderName == selectedFolderName
            val matchesQuery = q.isEmpty() ||
                pdf.title.lowercase().contains(q) ||
                (pdf.folderName?.lowercase()?.contains(q) == true)
            matchesFolder && matchesQuery
        }

        ScannerUiState(
            activeTab = criteria.tab,
            searchQuery = criteria.query,
            selectedFolderId = criteria.folderId,
            folders = folders,
            documents = filteredDocs,
            pdfs = filteredPdfs,
            isBusy = criteria.busy.first,
            busyMessage = criteria.busy.second,
            busyProgress = criteria.busy.third,
            recentPdfResult = criteria.recentPdf,
            statusMessage = criteria.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ScannerUiState()
    )

    init {
        viewModelScope.launch {
            repository.refreshAll()
        }
    }

    fun setTab(tab: VaultTab) {
        activeTab.value = tab
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectFolder(folderId: Long?) {
        selectedFolderId.value = folderId
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    fun clearRecentPdfResult() {
        recentPdfResult.value = null
    }

    fun showMessage(msg: String) {
        statusMessage.value = msg
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val id = repository.createFolder(name)
            if (id <= 0L) {
                statusMessage.value = "Folder already exists"
            }
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            if (selectedFolderId.value == folderId) selectedFolderId.value = null
            repository.deleteFolder(folderId)
        }
    }

    fun createDocumentFromCapturedFiles(
        files: List<File>,
        autoDetectCorners: Boolean,
        onCreated: (Long) -> Unit
    ) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            runBusy("Processing ${files.size} page(s)…", 0f) {
                val docId = repository.createDocumentFromImageFiles(
                    imageFiles = files,
                    folderId = selectedFolderId.value,
                    autoDetectCorners = autoDetectCorners
                ) { done, total ->
                    busyState.value = Triple(true, "Processing $done / $total…", done.toFloat() / total.coerceAtLeast(1))
                }
                files.forEach { it.delete() }
                if (docId > 0) {
                    onCreated(docId)
                    launchOcrIndexingQuietly(docId)
                }
            }
        }
    }

    fun createDocumentFromGallery(uris: List<Uri>, onCreated: (Long) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            runBusy("Importing 0 / ${uris.size}…", 0f) {
                val docId = repository.createDocumentFromUris(
                    uris = uris,
                    folderId = selectedFolderId.value
                ) { done, total ->
                    busyState.value = Triple(true, "Importing $done / $total…", done.toFloat() / total.coerceAtLeast(1))
                }
                if (docId > 0) {
                    onCreated(docId)
                    launchOcrIndexingQuietly(docId)
                } else {
                    statusMessage.value = "Could not load selected image"
                }
            }
        }
    }

    fun addPagesFromGallery(documentId: Long, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            runBusy("Adding 0 / ${uris.size}…", 0f) {
                repository.addPagesFromUris(documentId, uris) { done, total ->
                    busyState.value = Triple(true, "Adding $done / $total…", done.toFloat() / total.coerceAtLeast(1))
                }
                launchOcrIndexingQuietly(documentId)
            }
        }
    }

    fun addPageFromCameraFile(documentId: Long, file: File, autoDetectCorners: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            runBusy("Adding page…") {
                repository.addPageFromFile(documentId, file, autoDetectCorners)
                file.delete()
                onDone()
                launchOcrIndexingQuietly(documentId)
            }
        }
    }

    fun retakePageFromCameraFile(pageId: Long, file: File, onDone: () -> Unit) {
        viewModelScope.launch {
            runBusy("Updating page…") {
                repository.replacePageWithFile(pageId, file)
                file.delete()
                onDone()
            }
        }
    }

    fun duplicatePage(pageId: Long) {
        viewModelScope.launch {
            val newId = repository.duplicatePage(pageId)
            if (newId > 0) statusMessage.value = "Page duplicated"
        }
    }

    fun updatePageTransformAndFilter(
        pageId: Long,
        corners: QuadCorners,
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        filterMode: FilterMode,
        contrastBoost: Float = 1.0f,
        brightnessOffset: Float = 0f,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            runBusy("Enhancing page…") {
                repository.updatePageTransformAndFilter(
                    pageId = pageId,
                    corners = corners,
                    rotationDegrees = rotationDegrees,
                    flipHorizontal = flipHorizontal,
                    filterMode = filterMode,
                    contrastBoost = contrastBoost,
                    brightnessOffset = brightnessOffset
                )
                onDone()
            }
        }
    }

    fun applyFilterAndAdjust(
        pageId: Long,
        mode: FilterMode,
        contrastBoost: Float,
        brightnessOffset: Float
    ) {
        viewModelScope.launch {
            runBusy("Applying ${mode.label}…") {
                repository.applyFilterAndAdjustToPage(pageId, mode, contrastBoost, brightnessOffset)
            }
        }
    }

    fun applyFilterToAll(documentId: Long, mode: FilterMode) {
        viewModelScope.launch {
            runBusy("Updating pages…", 0f) {
                repository.applyFilterToAllPages(documentId, mode)
            }
        }
    }

    fun batchApplyFilter(documentId: Long, pageIds: Set<Long>, mode: FilterMode) {
        if (pageIds.isEmpty()) return
        viewModelScope.launch {
            runBusy("Filtering ${pageIds.size} page(s)…", 0f) {
                repository.batchApplyFilter(documentId, pageIds, mode) { done, total ->
                    busyState.value = Triple(true, "Filtering $done / $total…", done.toFloat() / total.coerceAtLeast(1))
                }
            }
        }
    }

    fun batchRotatePages(documentId: Long, pageIds: Set<Long>) {
        if (pageIds.isEmpty()) return
        viewModelScope.launch {
            runBusy("Rotating ${pageIds.size} page(s)…", 0f) {
                repository.batchRotatePages(documentId, pageIds, 90) { done, total ->
                    busyState.value = Triple(true, "Rotating $done / $total…", done.toFloat() / total.coerceAtLeast(1))
                }
            }
        }
    }

    fun batchAutoCropPages(documentId: Long, pageIds: Set<Long>) {
        if (pageIds.isEmpty()) return
        viewModelScope.launch {
            runBusy("Auto-cropping ${pageIds.size} page(s)…", 0f) {
                repository.batchAutoCropPages(documentId, pageIds) { done, total ->
                    busyState.value = Triple(true, "Auto-cropping $done / $total…", done.toFloat() / total.coerceAtLeast(1))
                }
            }
        }
    }

    fun batchDeletePages(documentId: Long, pageIds: Set<Long>) {
        if (pageIds.isEmpty()) return
        viewModelScope.launch {
            repository.batchDeletePages(documentId, pageIds)
            statusMessage.value = "Removed ${pageIds.size} page(s)"
        }
    }

    fun extractOcrForPage(pageId: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            runBusy("Reading text offline…") {
                val text = repository.extractOcrForPage(pageId)
                onResult(text)
            }
        }
    }

    private fun launchOcrIndexingQuietly(documentId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.indexDocumentOcrQuietly(documentId) }
        }
    }

    fun stampSignatureOnPage(
        pageId: Long,
        strokes: List<List<NormalizedPoint>>,
        placement: SignaturePlacement,
        inkColorInt: Int
    ) {
        viewModelScope.launch {
            runBusy("Signing page…") {
                repository.stampSignatureOnPage(pageId, strokes, placement, inkColorInt)
            }
        }
    }

    fun savePageToGallery(imagePath: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = ShareUtils.saveImageToGallery(getApplication(), File(imagePath), title)
            statusMessage.value = if (saved) "Saved to Pictures/NaimScanner" else "Use PDF export to share"
        }
    }

    fun quickRotatePage(pageId: Long, currentCorners: QuadCorners, newRotation: Int, flipH: Boolean, mode: FilterMode, contrast: Float, brightness: Float) {
        viewModelScope.launch {
            runBusy("Rotating…") {
                repository.updatePageTransformAndFilter(
                    pageId = pageId,
                    corners = currentCorners,
                    rotationDegrees = newRotation,
                    flipHorizontal = flipH,
                    filterMode = mode,
                    contrastBoost = contrast,
                    brightnessOffset = brightness
                )
            }
        }
    }

    fun movePage(documentId: Long, fromIndex: Int, toIndex: Int) {
        val doc = uiState.value.documents.find { it.id == documentId } ?: return
        if (fromIndex !in doc.pages.indices || toIndex !in doc.pages.indices || fromIndex == toIndex) return
        val mutable = doc.pages.map { it.id }.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        viewModelScope.launch {
            repository.reorderPages(documentId, mutable)
        }
    }

    fun reorderAllPages(documentId: Long, orderedIds: List<Long>) {
        viewModelScope.launch {
            repository.reorderPages(documentId, orderedIds)
        }
    }

    fun deletePage(pageId: Long) {
        viewModelScope.launch {
            repository.deletePage(pageId)
        }
    }

    fun renameDocument(documentId: Long, title: String) {
        viewModelScope.launch {
            repository.renameDocument(documentId, title)
        }
    }

    fun updateDocumentFolderAndTag(documentId: Long, folderId: Long?, tag: String) {
        viewModelScope.launch {
            repository.updateDocumentFolderAndTag(documentId, folderId, tag)
        }
    }

    fun duplicateDocument(documentId: Long) {
        viewModelScope.launch {
            runBusy("Duplicating…") {
                repository.duplicateDocument(documentId)
            }
        }
    }

    fun deleteDocument(documentId: Long) {
        viewModelScope.launch {
            repository.deleteDocument(documentId)
        }
    }

    fun exportPdf(
        documentId: Long,
        title: String,
        pageSize: PdfPageSize,
        qualityPreset: PdfQualityPreset,
        pageLayout: PdfPageLayout,
        margin: PdfMargin,
        watermarkText: String,
        includePageNumbers: Boolean,
        password: String,
        selectedPageIds: Set<Long>? = null
    ) {
        viewModelScope.launch {
            runBusy("Building ${qualityPreset.label} PDF…") {
                val result = repository.exportDocumentToPdf(
                    documentId = documentId,
                    pdfTitle = title,
                    pageSize = pageSize,
                    qualityPreset = qualityPreset,
                    pageLayout = pageLayout,
                    margin = margin,
                    watermarkText = watermarkText,
                    includePageNumbers = includePageNumbers,
                    password = password,
                    selectedPageIds = selectedPageIds
                )
                if (result != null) {
                    recentPdfResult.value = result
                } else {
                    statusMessage.value = "Failed to export PDF"
                }
            }
        }
    }

    fun importExternalPdf(uri: Uri) {
        viewModelScope.launch {
            runBusy("Importing PDF…") {
                val result = repository.importExternalPdf(uri)
                if (result != null) {
                    activeTab.value = VaultTab.PDFS
                    statusMessage.value = "Imported ${result.title}"
                } else {
                    statusMessage.value = "Could not import PDF"
                }
            }
        }
    }

    fun mergePdfs(
        pdfIds: List<Long>,
        title: String,
        pageSize: PdfPageSize,
        qualityPreset: PdfQualityPreset,
        password: String
    ) {
        viewModelScope.launch {
            runBusy("Merging PDFs…") {
                runCatching {
                    repository.mergePdfs(pdfIds, title, pageSize, qualityPreset, password)
                }.onSuccess { result ->
                    if (result != null) {
                        activeTab.value = VaultTab.PDFS
                        recentPdfResult.value = result
                    } else {
                        statusMessage.value = "Select at least 2 PDFs"
                    }
                }.onFailure {
                    statusMessage.value = "Could not merge password-locked PDF"
                }
            }
        }
    }

    fun splitOrReorderPdf(
        sourcePdfId: Long,
        zeroBasedOrder: List<Int>,
        title: String,
        onDone: (PdfFileModel?) -> Unit
    ) {
        viewModelScope.launch {
            runBusy("Saving ${zeroBasedOrder.size} page(s)…") {
                runCatching {
                    repository.splitOrReorderPdf(sourcePdfId, zeroBasedOrder, title)
                }.onSuccess { result ->
                    if (result != null) {
                        recentPdfResult.value = result
                    }
                    onDone(result)
                }.onFailure {
                    statusMessage.value = "Cannot edit password-locked PDF"
                    onDone(null)
                }
            }
        }
    }

    fun compressPdf(sourcePdfId: Long, qualityPreset: PdfQualityPreset) {
        viewModelScope.launch {
            runBusy("Compressing PDF…") {
                runCatching {
                    repository.compressPdf(sourcePdfId, qualityPreset)
                }.onSuccess { pair ->
                    if (pair != null) {
                        val (newPdf, oldSize) = pair
                        activeTab.value = VaultTab.PDFS
                        recentPdfResult.value = newPdf
                        statusMessage.value = "${PdfEngine.formatFileSize(oldSize)} → ${PdfEngine.formatFileSize(newPdf.sizeBytes)}"
                    } else {
                        statusMessage.value = "Compression failed"
                    }
                }.onFailure {
                    statusMessage.value = "Cannot compress locked PDF"
                }
            }
        }
    }

    fun protectPdf(sourcePdfId: Long, password: String) {
        if (password.isBlank()) return
        viewModelScope.launch {
            runBusy("Locking PDF…") {
                runCatching {
                    repository.protectPdf(sourcePdfId, password.trim())
                }.onSuccess { result ->
                    if (result != null) {
                        activeTab.value = VaultTab.PDFS
                        recentPdfResult.value = result
                    } else {
                        statusMessage.value = "Failed to lock PDF"
                    }
                }.onFailure {
                    statusMessage.value = "Cannot lock this PDF"
                }
            }
        }
    }

    fun convertPdfToScan(sourcePdfId: Long, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            runBusy("Converting PDF pages…") {
                runCatching {
                    repository.convertPdfToScanDocument(sourcePdfId)
                }.onSuccess { docId ->
                    if (docId > 0) {
                        activeTab.value = VaultTab.SCANS
                        onCreated(docId)
                    } else {
                        statusMessage.value = "Cannot extract locked PDF"
                    }
                }.onFailure {
                    statusMessage.value = "Cannot convert locked PDF"
                }
            }
        }
    }

    fun savePdfImagesToGallery(pdf: PdfFileModel) {
        viewModelScope.launch(Dispatchers.IO) {
            busyState.value = Triple(true, "Exporting pages as photos…", null)
            try {
                val tempDir = File(getApplication<Application>().cacheDir, "pdf_img_export_${System.currentTimeMillis()}")
                val files = PdfEngine.renderPdfPagesToImageFiles(pdf.filePath, tempDir, 2600)
                var savedCount = 0
                files.forEachIndexed { idx, file ->
                    if (ShareUtils.saveImageToGallery(getApplication(), file, "${pdf.title}_p${idx + 1}")) {
                        savedCount++
                    }
                }
                tempDir.deleteRecursively()
                statusMessage.value = if (savedCount > 0) "Saved $savedCount photo(s) to Pictures/NaimScanner" else "Image export requires Android 10+"
            } finally {
                busyState.value = Triple(false, "", null)
            }
        }
    }

    fun renamePdf(pdfId: Long, title: String) {
        viewModelScope.launch {
            repository.renamePdf(pdfId, title)
        }
    }

    fun duplicatePdf(pdfId: Long) {
        viewModelScope.launch {
            repository.duplicatePdf(pdfId)
        }
    }

    fun deletePdf(pdfId: Long) {
        viewModelScope.launch {
            repository.deletePdf(pdfId)
        }
    }

    suspend fun detectCornersForFile(rawPath: String): QuadCorners = withContext(Dispatchers.Default) {
        val bmp = ImageProcessor.decodeFileNormalized(rawPath, 900) ?: return@withContext QuadCorners()
        val detected = ImageProcessor.detectDocumentCorners(bmp)
        bmp.recycle()
        detected
    }

    private suspend inline fun runBusy(
        message: String,
        progress: Float? = null,
        crossinline block: suspend () -> Unit
    ) {
        busyState.value = Triple(true, message, progress)
        try {
            block()
        } finally {
            busyState.value = Triple(false, "", null)
        }
    }
}
