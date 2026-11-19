package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ScannerRepository
import com.example.engine.ImageProcessor
import com.example.model.FilterMode
import com.example.model.FolderModel
import com.example.model.PdfFileModel
import com.example.model.PdfPageSize
import com.example.model.PdfQualityPreset
import com.example.model.QuadCorners
import com.example.model.ScanDocumentModel
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
    val recentPdfResult: PdfFileModel? = null,
    val statusMessage: String? = null
)

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScannerRepository(application)

    private val activeTab = MutableStateFlow(VaultTab.SCANS)
    private val searchQuery = MutableStateFlow("")
    private val selectedFolderId = MutableStateFlow<Long?>(null)
    private val busyState = MutableStateFlow<Pair<Boolean, String>>(false to "")
    private val recentPdfResult = MutableStateFlow<PdfFileModel?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)

    private data class FilterCriteria(
        val tab: VaultTab,
        val query: String,
        val folderId: Long?,
        val busy: Pair<Boolean, String>,
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
                (doc.folderName?.lowercase()?.contains(q) == true)
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
            runBusy("Processing ${files.size} page(s)…") {
                val docId = repository.createDocumentFromImageFiles(
                    imageFiles = files,
                    folderId = selectedFolderId.value,
                    autoDetectCorners = autoDetectCorners
                )
                files.forEach { it.delete() }
                if (docId > 0) onCreated(docId)
            }
        }
    }

    fun createDocumentFromGallery(uris: List<Uri>, onCreated: (Long) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            runBusy("Importing ${uris.size} image(s)…") {
                val docId = repository.createDocumentFromUris(
                    uris = uris,
                    folderId = selectedFolderId.value
                )
                if (docId > 0) onCreated(docId)
                else statusMessage.value = "Could not load selected image"
            }
        }
    }

    fun addPagesFromGallery(documentId: Long, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            runBusy("Adding pages…") {
                repository.addPagesFromUris(documentId, uris)
            }
        }
    }

    fun addPageFromCameraFile(documentId: Long, file: File, autoDetectCorners: Boolean, onDone: () -> Unit) {
        viewModelScope.launch {
            runBusy("Adding page…") {
                repository.addPageFromFile(documentId, file, autoDetectCorners)
                file.delete()
                onDone()
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

    fun applyFilter(pageId: Long, mode: FilterMode) {
        viewModelScope.launch {
            runBusy("Applying ${mode.label}…") {
                repository.applyFilterToPage(pageId, mode)
            }
        }
    }

    fun applyFilterToAll(documentId: Long, mode: FilterMode) {
        viewModelScope.launch {
            runBusy("Updating all pages…") {
                repository.applyFilterToAllPages(documentId, mode)
            }
        }
    }

    fun quickRotatePage(pageId: Long, currentCorners: QuadCorners, newRotation: Int, flipH: Boolean, mode: FilterMode) {
        viewModelScope.launch {
            runBusy("Rotating…") {
                repository.updatePageTransformAndFilter(
                    pageId = pageId,
                    corners = currentCorners,
                    rotationDegrees = newRotation,
                    flipHorizontal = flipH,
                    filterMode = mode
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
        watermarkText: String,
        password: String
    ) {
        viewModelScope.launch {
            runBusy("Building PDF…") {
                val result = repository.exportDocumentToPdf(
                    documentId = documentId,
                    pdfTitle = title,
                    pageSize = pageSize,
                    qualityPreset = qualityPreset,
                    watermarkText = watermarkText,
                    password = password
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

    fun splitExtractPdf(
        sourcePdfId: Long,
        zeroBasedIndices: List<Int>,
        title: String,
        onDone: (PdfFileModel?) -> Unit
    ) {
        viewModelScope.launch {
            runBusy("Extracting ${zeroBasedIndices.size} page(s)…") {
                runCatching {
                    repository.splitExtractPdf(sourcePdfId, zeroBasedIndices, title)
                }.onSuccess { result ->
                    if (result != null) {
                        recentPdfResult.value = result
                    }
                    onDone(result)
                }.onFailure {
                    statusMessage.value = "Cannot extract from locked PDF"
                    onDone(null)
                }
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

    private suspend inline fun runBusy(message: String, crossinline block: suspend () -> Unit) {
        busyState.value = true to message
        try {
            block()
        } finally {
            busyState.value = false to ""
        }
    }
}
