package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.engine.PdfEngine
import com.example.ui.ScannerViewModel
import com.example.ui.components.MergePdfsSheet
import com.example.ui.screens.CameraCaptureScreen
import com.example.ui.screens.CropPerspectiveScreen
import com.example.ui.screens.DocumentEditorScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.VaultHomeScreen
import com.example.ui.theme.NaimScannerTheme
import com.example.ui.theme.ObsidianBg
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
}

@Composable
fun NaimScannerApp(viewModel: ScannerViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var showMergeSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                        onImportExternalPdf = viewModel::importExternalPdf,
                        onOpenMergePdfs = { showMergeSheet = true },
                        onOpenDocument = { doc -> screen = AppScreen.DocumentEditor(doc.id) },
                        onRenameDocument = viewModel::renameDocument,
                        onDuplicateDocument = viewModel::duplicateDocument,
                        onDeleteDocument = viewModel::deleteDocument,
                        onOpenPdf = { pdf -> screen = AppScreen.PdfViewer(pdf.id) },
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
                                    mode = page.filterMode
                                )
                            },
                            onRetakePage = { page ->
                                screen = AppScreen.Camera(targetDocumentId = doc.id, retakePageId = page.id)
                            },
                            onDeletePage = { page -> viewModel.deletePage(page.id) },
                            onApplyFilter = { page, mode -> viewModel.applyFilter(page.id, mode) },
                            onApplyFilterAll = { mode -> viewModel.applyFilterToAll(doc.id, mode) },
                            onMovePage = { from, to -> viewModel.movePage(doc.id, from, to) },
                            onAddPageCamera = { screen = AppScreen.Camera(targetDocumentId = doc.id) },
                            onAddPagesGallery = { uris -> viewModel.addPagesFromGallery(doc.id, uris) },
                            onExportPdf = { title, pageSize, quality, watermark, password ->
                                viewModel.exportPdf(doc.id, title, pageSize, quality, watermark, password)
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
                            onExtractPages = { indices, title ->
                                viewModel.splitExtractPdf(pdf.id, indices, title) { created ->
                                    if (created != null) {
                                        screen = AppScreen.PdfViewer(created.id)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Busy Processing Overlay
            if (uiState.isBusy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = TealAccent,
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = uiState.busyMessage.ifEmpty { "Working…" },
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
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

    // Exported PDF Result Dialog showing EXACT PDF file size + Open & Share actions
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(exported.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Size: ${PdfEngine.formatFileSize(exported.sizeBytes)} · ${exported.pageCount} page(s) · ${exported.pageSize.label}",
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
