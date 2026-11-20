package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import com.example.engine.PdfEngine
import com.example.engine.ScannerCache
import com.example.model.PdfFileModel
import com.example.ui.ScannerViewModel
import com.example.ui.components.MergePdfsSheet
import com.example.ui.components.PdfToolsSheet
import com.example.ui.screens.CameraCaptureScreen
import com.example.ui.screens.CropPerspectiveScreen
import com.example.ui.screens.DocumentEditorScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.VaultHomeScreen
import com.example.ui.theme.NaimScannerTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TealAccent
import com.example.util.ShareUtils

private sealed interface AppScreen {
    data object Home : AppScreen
    data class Camera(
        val targetDocumentId: Long? = null,
        val retakePageId: Long? = null
    ) : AppScreen
    data class DocumentEditor(val documentId: Long) : AppScreen
    data class CropPage(val documentId: Long, val pageId: Long) : AppScreen
    data class PdfViewer(val pdfId: Long) : AppScreen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Coil.setImageLoader(ScannerCache.imageLoader(applicationContext))
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(ObsidianBg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(ObsidianBg.toArgb())
        )
        setContent {
            NaimScannerTheme {
                NaimScannerApp()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            ScannerCache.clear()
        }
    }
}

@Composable
fun NaimScannerApp(viewModel: ScannerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var showMergeSheet by remember { mutableStateOf(false) }
    var showToolsSheet by remember { mutableStateOf(false) }
    var pendingSavePdf by remember { mutableStateOf<PdfFileModel?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val pdfImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importExternalPdf(uri)
    }

    val createSafPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val target = pendingSavePdf
        pendingSavePdf = null
        if (destUri != null && target != null) {
            val ok = ShareUtils.copyPdfToUri(context, target.filePath, destUri)
            viewModel.showMessage(if (ok) "PDF saved" else "Failed to save PDF")
        }
    }

    fun triggerSaveToDownloads(pdf: PdfFileModel) {
        val savedDirect = ShareUtils.savePdfToDownloads(context, pdf.filePath, pdf.title)
        if (savedDirect) {
            viewModel.showMessage("Saved to Downloads/NaimScanner")
        } else {
            pendingSavePdf = pdf
            createSafPdfLauncher.launch("${pdf.title}.pdf")
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val current = screen) {
                AppScreen.Home -> {
                    VaultHomeScreen(
                        uiState = uiState,
                        onTabSelected = viewModel::setTab,
                        onSearchQueryChange = viewModel::setSearchQuery,
                        onFolderSelected = viewModel::selectFolder,
                        onCreateFolder = viewModel::createFolder,
                        onStartCameraScan = { screen = AppScreen.Camera() },
                        onPickGalleryImages = { uris ->
                            viewModel.createDocumentFromGallery(uris) { docId ->
                                screen = AppScreen.DocumentEditor(docId)
                            }
                        },
                        onOpenPdfTools = { showToolsSheet = true },
                        onOpenDocument = { doc -> screen = AppScreen.DocumentEditor(doc.id) },
                        onRenameDocument = viewModel::renameDocument,
                        onDuplicateDocument = viewModel::duplicateDocument,
                        onDeleteDocument = viewModel::deleteDocument,
                        onOpenPdf = { pdf -> screen = AppScreen.PdfViewer(pdf.id) },
                        onSavePdfToDownloads = ::triggerSaveToDownloads,
                        onRenamePdf = viewModel::renamePdf,
                        onDuplicatePdf = viewModel::duplicatePdf,
                        onDeletePdf = viewModel::deletePdf
                    )
                }

                is AppScreen.Camera -> {
                    val isSingleShot = current.targetDocumentId != null || current.retakePageId != null
                    CameraCaptureScreen(
                        singleShotMode = isSingleShot,
                        onFinishBatch = { files, autoDetect ->
                            when {
                                current.retakePageId != null && current.targetDocumentId != null && files.isNotEmpty() -> {
                                    viewModel.retakePageFromCameraFile(current.retakePageId, files.first()) {
                                        screen = AppScreen.DocumentEditor(current.targetDocumentId)
                                    }
                                }
                                current.targetDocumentId != null && files.isNotEmpty() -> {
                                    viewModel.addPageFromCameraFile(current.targetDocumentId, files.first(), autoDetect) {
                                        screen = AppScreen.DocumentEditor(current.targetDocumentId)
                                    }
                                }
                                else -> {
                                    viewModel.createDocumentFromCapturedFiles(files, autoDetect) { docId ->
                                        screen = AppScreen.DocumentEditor(docId)
                                    }
                                }
                            }
                        },
                        onCancel = {
                            screen = if (current.targetDocumentId != null) {
                                AppScreen.DocumentEditor(current.targetDocumentId)
                            } else {
                                AppScreen.Home
                            }
                        }
                    )
                }

                is AppScreen.DocumentEditor -> {
                    val doc = uiState.documents.find { it.id == current.documentId }
                    if (doc == null) {
                        LaunchedEffect(Unit) { screen = AppScreen.Home }
                    } else {
                        DocumentEditorScreen(
                            document = doc,
                            folders = uiState.folders,
                            onBack = { screen = AppScreen.Home },
                            onRenameDocument = { title -> viewModel.renameDocument(doc.id, title) },
                            onUpdateFolderTag = { folderId, tag -> viewModel.updateDocumentFolderAndTag(doc.id, folderId, tag) },
                            onOpenCrop = { page -> screen = AppScreen.CropPage(doc.id, page.id) },
                            onQuickRotate = { page ->
                                viewModel.quickRotatePage(
                                    pageId = page.id,
                                    currentCorners = page.corners,
                                    newRotation = (page.rotationDegrees + 90) % 360,
                                    flipH = page.flipHorizontal,
                                    mode = page.filterMode,
                                    contrast = page.contrastBoost,
                                    brightness = page.brightnessOffset
                                )
                            },
                            onDuplicatePage = { page -> viewModel.duplicatePage(page.id) },
                            onRetakePage = { page ->
                                screen = AppScreen.Camera(targetDocumentId = doc.id, retakePageId = page.id)
                            },
                            onDeletePage = { page -> viewModel.deletePage(page.id) },
                            onApplyFilter = { page, mode, contrast, brightness ->
                                viewModel.applyFilterAndAdjust(page.id, mode, contrast, brightness)
                            },
                            onApplyFilterAll = { mode -> viewModel.applyFilterToAll(doc.id, mode) },
                            onBatchApplyFilter = { pageIds, mode -> viewModel.batchApplyFilter(doc.id, pageIds, mode) },
                            onBatchRotate = { pageIds -> viewModel.batchRotatePages(doc.id, pageIds) },
                            onBatchAutoCrop = { pageIds -> viewModel.batchAutoCropPages(doc.id, pageIds) },
                            onBatchDelete = { pageIds -> viewModel.batchDeletePages(doc.id, pageIds) },
                            onExtractOcr = { page, onResult -> viewModel.extractOcrForPage(page.id, onResult) },
                            onStampSignature = { page, strokes, placement, colorInt ->
                                viewModel.stampSignatureOnPage(page.id, strokes, placement, colorInt)
                            },
                            onDrawOnPage = { page, strokes ->
                                viewModel.drawStrokesOnPage(page.id, strokes)
                            },
                            onSavePagePhoto = { page, pageNum ->
                                viewModel.savePageToGallery(page.enhancedImagePath, "${doc.title}_p$pageNum")
                            },
                            onMovePage = { from, to -> viewModel.movePage(doc.id, from, to) },
                            onReorderAllPages = { orderedIds -> viewModel.reorderAllPages(doc.id, orderedIds) },
                            onAddPageCamera = { screen = AppScreen.Camera(targetDocumentId = doc.id) },
                            onAddPagesGallery = { uris -> viewModel.addPagesFromGallery(doc.id, uris) },
                            onExportPdf = { title, pageSize, quality, layout, margin, watermark, pageNums, password, selectedPageIds ->
                                viewModel.exportPdf(
                                    documentId = doc.id,
                                    title = title,
                                    pageSize = pageSize,
                                    qualityPreset = quality,
                                    pageLayout = layout,
                                    margin = margin,
                                    watermarkText = watermark,
                                    includePageNumbers = pageNums,
                                    password = password,
                                    selectedPageIds = selectedPageIds
                                )
                            }
                        )
                    }
                }

                is AppScreen.CropPage -> {
                    val doc = uiState.documents.find { it.id == current.documentId }
                    val page = doc?.pages?.find { it.id == current.pageId }
                    if (doc == null || page == null) {
                        LaunchedEffect(Unit) { screen = AppScreen.Home }
                    } else {
                        CropPerspectiveScreen(
                            page = page,
                            onDetectCorners = viewModel::detectCornersForFile,
                            onApply = { corners, rot, flip ->
                                viewModel.updatePageTransformAndFilter(
                                    pageId = page.id,
                                    corners = corners,
                                    rotationDegrees = rot,
                                    flipHorizontal = flip,
                                    filterMode = page.filterMode,
                                    contrastBoost = page.contrastBoost,
                                    brightnessOffset = page.brightnessOffset,
                                    onDone = { screen = AppScreen.DocumentEditor(doc.id) }
                                )
                            },
                            onBack = { screen = AppScreen.DocumentEditor(doc.id) }
                        )
                    }
                }

                is AppScreen.PdfViewer -> {
                    val pdf = uiState.pdfs.find { it.id == current.pdfId }
                    if (pdf == null) {
                        LaunchedEffect(Unit) { screen = AppScreen.Home }
                    } else {
                        PdfViewerScreen(
                            pdf = pdf,
                            onBack = { screen = AppScreen.Home },
                            onSaveToDownloads = { triggerSaveToDownloads(pdf) },
                            onSaveOrderedOrSplitPages = { indices, title ->
                                viewModel.splitOrReorderPdf(pdf.id, indices, title) { created ->
                                    if (created != null) {
                                        screen = AppScreen.PdfViewer(created.id)
                                    }
                                }
                            },
                            onAnnotatePdf = { annotationsMap, saveAsCopy ->
                                viewModel.annotatePdf(pdf.id, annotationsMap, saveAsCopy) { updated ->
                                    if (updated != null && updated.id != pdf.id) {
                                        screen = AppScreen.PdfViewer(updated.id)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Non-intrusive, non-blocking background progress indicator pill
            AnimatedVisibility(
                visible = uiState.isBusy,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = 20.dp, end = 20.dp)
            ) {
                Surface(
                    color = ObsidianSurface.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier.border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = TealAccent,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = uiState.busyMessage.ifEmpty { "Working…" },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        val progress = uiState.busyProgress
                        if (progress != null) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                color = TealAccent,
                                trackColor = ObsidianBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showToolsSheet) {
        PdfToolsSheet(
            pdfs = uiState.pdfs,
            onDismiss = { showToolsSheet = false },
            onImportPdf = { pdfImportLauncher.launch(arrayOf("application/pdf")) },
            onOpenMerge = { showMergeSheet = true },
            onOpenPdfForSplit = { pdf -> screen = AppScreen.PdfViewer(pdf.id) },
            onCompressPdf = { pdf, quality -> viewModel.compressPdf(pdf.id, quality) },
            onProtectPdf = { pdf, pwd -> viewModel.protectPdf(pdf.id, pwd) },
            onConvertPdfToScan = { pdf ->
                viewModel.convertPdfToScan(pdf.id) { docId ->
                    screen = AppScreen.DocumentEditor(docId)
                }
            },
            onSavePdfAsImages = { pdf -> viewModel.savePdfImagesToGallery(pdf) }
        )
    }

    if (showMergeSheet) {
        MergePdfsSheet(
            pdfs = uiState.pdfs,
            onDismiss = { showMergeSheet = false },
            onMerge = { ids, title, pageSize, quality, password ->
                showMergeSheet = false
                viewModel.mergePdfs(ids, title, pageSize, quality, password)
            }
        )
    }

    uiState.recentPdfResult?.let { exported ->
        AlertDialog(
            onDismissRequest = viewModel::clearRecentPdfResult,
            containerColor = ObsidianSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TealAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("PDF Saved")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(exported.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Size: ${PdfEngine.formatFileSize(exported.sizeBytes)} · ${exported.pageCount} page(s) · ${exported.qualityPreset.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent
                    )
                    if (exported.isPasswordProtected) {
                        Text(
                            text = "Password protected",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearRecentPdfResult()
                                screen = AppScreen.PdfViewer(exported.id)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open")
                        }
                        Button(
                            onClick = {
                                viewModel.clearRecentPdfResult()
                                ShareUtils.sharePdf(context, exported.filePath, exported.title)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TealAccent,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.clearRecentPdfResult()
                            triggerSaveToDownloads(exported)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save to Downloads")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearRecentPdfResult) {
                    Text("Done")
                }
            }
        )
    }
}
