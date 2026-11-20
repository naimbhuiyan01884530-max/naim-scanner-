package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.example.R
import com.example.engine.PdfEngine
import com.example.model.DrawStroke
import com.example.model.FilterMode
import com.example.model.FolderModel
import com.example.model.NormalizedPoint
import com.example.model.PageModel
import com.example.model.PdfMargin
import com.example.model.PdfPageLayout
import com.example.model.PdfPageSize
import com.example.model.PdfQualityPreset
import com.example.model.ScanDocumentModel
import com.example.model.SignaturePlacement
import com.example.ui.components.DirectSerialPickerDialog
import com.example.ui.components.PageDrawDialog
import com.example.ui.components.SignaturePadDialog
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceMid
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealOnContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentEditorScreen(
    document: ScanDocumentModel,
    folders: List<FolderModel>,
    onBack: () -> Unit,
    onRenameDocument: (String) -> Unit,
    onUpdateFolderTag: (Long?, String) -> Unit,
    onOpenCrop: (PageModel) -> Unit,
    onQuickRotate: (PageModel) -> Unit,
    onDuplicatePage: (PageModel) -> Unit,
    onRetakePage: (PageModel) -> Unit,
    onDeletePage: (PageModel) -> Unit,
    onApplyFilter: (PageModel, FilterMode, Float, Float) -> Unit,
    onApplyFilterAll: (FilterMode) -> Unit,
    onBatchApplyFilter: (Set<Long>, FilterMode) -> Unit,
    onBatchRotate: (Set<Long>) -> Unit,
    onBatchAutoCrop: (Set<Long>) -> Unit,
    onBatchDelete: (Set<Long>) -> Unit,
    onExtractOcr: (PageModel, (String) -> Unit) -> Unit,
    onStampSignature: (PageModel, List<List<NormalizedPoint>>, SignaturePlacement, Int) -> Unit,
    onDrawOnPage: (PageModel, List<DrawStroke>) -> Unit,
    onSavePagePhoto: (PageModel, Int) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onReorderAllPages: (List<Long>) -> Unit,
    onAddPageCamera: () -> Unit,
    onAddPagesGallery: (List<android.net.Uri>) -> Unit,
    onExportPdf: (
        title: String,
        pageSize: PdfPageSize,
        quality: PdfQualityPreset,
        layout: PdfPageLayout,
        margin: PdfMargin,
        watermark: String,
        includePageNumbers: Boolean,
        password: String,
        selectedPageIds: Set<Long>?
    ) -> Unit
) {
    BackHandler {
        onBack()
    }

    var selectedIndex by remember(document.pages.size) {
        mutableIntStateOf(0.coerceAtMost((document.pages.size - 1).coerceAtLeast(0)))
    }
    val safeIndex = selectedIndex.coerceIn(0, (document.pages.size - 1).coerceAtLeast(0))
    val activePage = document.pages.getOrNull(safeIndex)

    var batchMode by remember { mutableStateOf(false) }
    val selectedPageIds = remember(document.id) { mutableStateListOf<Long>() }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var exportSubsetOnly by remember { mutableStateOf(false) }
    var showAdjustPanel by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showDrawDialog by remember { mutableStateOf(false) }
    var showReorderDialog by remember { mutableStateOf(false) }
    var ocrDialogText by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 40)
    ) { uris ->
        if (uris.isNotEmpty()) onAddPagesGallery(uris)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (batchMode) {
                        batchMode = false
                        selectedPageIds.clear()
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier.testTag("editor_back_button")
            ) {
                Icon(
                    imageVector = if (batchMode) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !batchMode) { showRenameDialog = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (batchMode) {
                    Text(
                        text = "${selectedPageIds.size} of ${document.pages.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        color = TealAccent
                    )
                    Text(
                        text = "Tap pages to select",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = document.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Rename",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = buildString {
                            append("${document.pages.size} page")
                            if (document.pages.size != 1) append("s")
                            if (!document.folderName.isNullOrBlank()) append(" · ${document.folderName}")
                            if (document.tag.isNotBlank()) append(" · #${document.tag}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (batchMode) {
                TextButton(onClick = {
                    if (selectedPageIds.size == document.pages.size) {
                        selectedPageIds.clear()
                    } else {
                        selectedPageIds.clear()
                        selectedPageIds.addAll(document.pages.map { it.id })
                    }
                }) {
                    Text(if (selectedPageIds.size == document.pages.size) "None" else "All")
                }
            } else {
                if (document.pages.size > 1) {
                    IconButton(
                        onClick = {
                            batchMode = true
                            selectedPageIds.clear()
                            activePage?.let { selectedPageIds.add(it.id) }
                        },
                        modifier = Modifier.testTag("editor_batch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Checklist,
                            contentDescription = "Batch select",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { showFolderDialog = true },
                    modifier = Modifier.testTag("editor_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = "Folder & tag",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = {
                        exportSubsetOnly = false
                        showExportSheet = true
                    },
                    enabled = document.pages.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TealAccent,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("editor_export_pdf_button")
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("PDF")
                }
            }
        }

        if (activePage == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No pages in document", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onAddPageCamera) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_scan))
                        }
                        OutlinedButton(onClick = {
                            galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_gallery))
                        }
                    }
                }
            }
        } else {
            val context = LocalContext.current
            var zoomScale by remember(activePage.id) { mutableFloatStateOf(1f) }
            var panOffset by remember(activePage.id) { mutableStateOf(Offset.Zero) }
            val imageFile = File(activePage.enhancedImagePath)

            // Pinch + Double-tap Zoomable Active Page Preview
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                    .pointerInput(activePage.id) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (zoomScale > 1.2f) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    zoomScale = 2.6f
                                }
                            }
                        )
                    }
                    .pointerInput(activePage.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                            zoomScale = newScale
                            panOffset = if (newScale <= 1.05f) Offset.Zero else panOffset + pan
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageFile)
                        .memoryCacheKey("${activePage.enhancedImagePath}_${imageFile.lastModified()}")
                        .diskCacheKey("${activePage.enhancedImagePath}_${imageFile.lastModified()}")
                        .build(),
                    contentDescription = "Page ${safeIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                )

                Surface(
                    color = ObsidianBg.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = document.pages.size > 1) { showReorderDialog = true }
                ) {
                    Text(
                        text = if (zoomScale > 1.1f) "${safeIndex + 1}/${document.pages.size} · ${String.format(Locale.US, "%.1fx", zoomScale)}"
                        else "${safeIndex + 1} / ${document.pages.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Brightness & Contrast Adjustment Drawer
            AnimatedVisibility(visible = showAdjustPanel && !batchMode) {
                var contrast by remember(activePage.id, activePage.contrastBoost) {
                    mutableFloatStateOf(activePage.contrastBoost)
                }
                var brightness by remember(activePage.id, activePage.brightnessOffset) {
                    mutableFloatStateOf(activePage.brightnessOffset)
                }
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Brightness: ${brightness.roundToInt()}", style = MaterialTheme.typography.labelSmall)
                            Text("Contrast: ${String.format(Locale.US, "%.2fx", contrast)}", style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = {
                                onApplyFilter(activePage, activePage.filterMode, contrast, brightness)
                                showAdjustPanel = false
                            }) {
                                Text("Apply", color = TealAccent)
                            }
                        }
                        Slider(
                            value = brightness,
                            onValueChange = { brightness = it },
                            valueRange = -50f..50f,
                            colors = SliderDefaults.colors(thumbColor = TealAccent, activeTrackColor = TealAccent)
                        )
                        Slider(
                            value = contrast,
                            onValueChange = { contrast = it },
                            valueRange = 0.7f..1.6f,
                            colors = SliderDefaults.colors(thumbColor = TealAccent, activeTrackColor = TealAccent)
                        )
                    }
                }
            }

            // Enhancement Mode Chips (applies to selected pages in batchMode or current page otherwise)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(FilterMode.entries) { _, mode ->
                    val selected = activePage.filterMode == mode && !batchMode
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (batchMode && selectedPageIds.isNotEmpty()) {
                                onBatchApplyFilter(selectedPageIds.toSet(), mode)
                            } else {
                                onApplyFilter(activePage, mode, activePage.contrastBoost, activePage.brightnessOffset)
                            }
                        },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ObsidianSurface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = TealContainer,
                            selectedLabelColor = TealOnContainer
                        )
                    )
                }
                if (!batchMode) {
                    item {
                        TextButton(onClick = { onApplyFilterAll(activePage.filterMode) }) {
                            Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Apply All")
                        }
                    }
                }
            }

            // Context-Aware Actions Bar (Batch Edit vs Single Page Tools)
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, if (batchMode) TealAccent.copy(alpha = 0.6f) else ObsidianBorder, RoundedCornerShape(16.dp))
            ) {
                if (batchMode) {
                    val hasSelection = selectedPageIds.isNotEmpty()
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            EditorActionIcon(Icons.Filled.AutoFixHigh, "Auto Crop", enabled = hasSelection) {
                                onBatchAutoCrop(selectedPageIds.toSet())
                            }
                        }
                        item {
                            EditorActionIcon(Icons.AutoMirrored.Filled.RotateRight, "Rotate", enabled = hasSelection) {
                                onBatchRotate(selectedPageIds.toSet())
                            }
                        }
                        item {
                            EditorActionIcon(Icons.Filled.PictureAsPdf, "Export PDF", isActive = hasSelection, enabled = hasSelection) {
                                exportSubsetOnly = true
                                showExportSheet = true
                            }
                        }
                        item {
                            EditorActionIcon(Icons.Filled.DeleteOutline, "Delete", isDestructive = true, enabled = hasSelection) {
                                val toRemove = selectedPageIds.toSet()
                                selectedPageIds.clear()
                                batchMode = false
                                onBatchDelete(toRemove)
                                selectedIndex = 0
                            }
                        }
                    }
                } else {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item { EditorActionIcon(Icons.Filled.Crop, "Crop") { onOpenCrop(activePage) } }
                        item { EditorActionIcon(Icons.AutoMirrored.Filled.RotateRight, "Rotate") { onQuickRotate(activePage) } }
                        item { EditorActionIcon(Icons.Filled.Tune, "Adjust", isActive = showAdjustPanel) { showAdjustPanel = !showAdjustPanel } }
                        item { EditorActionIcon(Icons.Filled.Brush, "Draw") { showDrawDialog = true } }
                        item { EditorActionIcon(Icons.Filled.Draw, "Sign") { showSignatureDialog = true } }
                        item {
                            EditorActionIcon(Icons.Filled.TextSnippet, "OCR") {
                                if (activePage.ocrText.isNotBlank()) {
                                    ocrDialogText = activePage.ocrText
                                } else {
                                    onExtractOcr(activePage) { txt -> ocrDialogText = txt }
                                }
                            }
                        }
                        if (document.pages.size > 1) {
                            item { EditorActionIcon(Icons.Filled.SwapVert, "Serial") { showReorderDialog = true } }
                        }
                        item { EditorActionIcon(Icons.Filled.ContentCopy, "Copy") { onDuplicatePage(activePage) } }
                        item { EditorActionIcon(Icons.Filled.SaveAlt, "Photo") { onSavePagePhoto(activePage, safeIndex + 1) } }
                        item { EditorActionIcon(Icons.Filled.CameraAlt, "Retake") { onRetakePage(activePage) } }
                        item {
                            EditorActionIcon(Icons.Filled.DeleteOutline, "Remove", isDestructive = true) {
                                onDeletePage(activePage)
                                if (safeIndex > 0) selectedIndex = safeIndex - 1
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Multi-Page Thumbnail Strip (lightweight _thumb.jpg + long-press to enter Batch Edit!)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(document.pages, key = { _, p -> p.id }) { idx, p ->
                    val isCurrent = idx == safeIndex
                    val isBatchSelected = selectedPageIds.contains(p.id)
                    val thumbFile = File(p.bestThumbPath())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(width = 58.dp, height = 74.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isBatchSelected || isCurrent) 2.dp else 1.dp,
                                    color = when {
                                        batchMode && isBatchSelected -> TealAccent
                                        !batchMode && isCurrent -> TealAccent
                                        else -> ObsidianBorder
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (batchMode) {
                                            if (isBatchSelected) selectedPageIds.remove(p.id)
                                            else selectedPageIds.add(p.id)
                                        } else {
                                            selectedIndex = idx
                                        }
                                    },
                                    onLongClick = {
                                        if (!batchMode) {
                                            batchMode = true
                                            selectedPageIds.clear()
                                            selectedPageIds.add(p.id)
                                        }
                                    }
                                )
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(thumbFile)
                                    .size(Size(180, 240))
                                    .memoryCacheKey("${thumbFile.absolutePath}_${thumbFile.lastModified()}")
                                    .build(),
                                contentDescription = "Page ${idx + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = if ((batchMode && isBatchSelected) || (!batchMode && isCurrent)) TealAccent else ObsidianBg.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(bottomEnd = 6.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if ((batchMode && isBatchSelected) || (!batchMode && isCurrent)) MaterialTheme.colorScheme.onPrimary else Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            if (batchMode && isBatchSelected) {
                                Icon(
                                    imageVector = Icons.Filled.CheckBox,
                                    contentDescription = "Selected",
                                    tint = TealAccent,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(18.dp)
                                        .background(ObsidianBg.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                )
                            }
                        }
                        if (!batchMode && isCurrent && document.pages.size > 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = {
                                        if (idx > 0) {
                                            onMovePage(idx, idx - 1)
                                            selectedIndex = idx - 1
                                        }
                                    },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Move left",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (idx < document.pages.lastIndex) {
                                            onMovePage(idx, idx + 1)
                                            selectedIndex = idx + 1
                                        }
                                    },
                                    enabled = idx < document.pages.lastIndex,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Move right",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .size(width = 58.dp, height = 74.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianSurfaceMid)
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                            .clickable { onAddPageCamera() }
                            .testTag("editor_add_camera_page"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = "Add scan", tint = TealAccent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(2.dp))
                            Text("Scan", style = MaterialTheme.typography.labelSmall, color = TealAccent)
                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .size(width = 58.dp, height = 74.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianSurfaceMid)
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            .testTag("editor_add_gallery_page"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Add from gallery", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(2.dp))
                            Text("Photo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }

    if (showSignatureDialog && activePage != null) {
        SignaturePadDialog(
            onDismiss = { showSignatureDialog = false },
            onApplySignature = { strokes, placement, colorInt ->
                showSignatureDialog = false
                onStampSignature(activePage, strokes, placement, colorInt)
            }
        )
    }

    if (showDrawDialog && activePage != null) {
        PageDrawDialog(
            imagePath = activePage.enhancedImagePath,
            pageLabel = "Page ${safeIndex + 1}",
            onDismiss = { showDrawDialog = false },
            onSave = { strokes ->
                showDrawDialog = false
                onDrawOnPage(activePage, strokes)
            }
        )
    }

    ocrDialogText?.let { extracted ->
        AlertDialog(
            onDismissRequest = { ocrDialogText = null },
            containerColor = ObsidianSurface,
            title = { Text("Recognized Text (Offline)") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = extracted.ifBlank { "No text detected on this page." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (extracted.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = extracted.isNotBlank(),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(extracted))
                        ocrDialogText = null
                    }
                ) {
                    Text("Copy Text", color = TealAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { ocrDialogText = null }) { Text("Close") }
            }
        )
    }

    if (showReorderDialog) {
        val workingOrder = remember(document.pages) {
            mutableStateListOf<PageModel>().apply { addAll(document.pages) }
        }
        var pickSerialForIdx by remember { mutableStateOf<Int?>(null) }

        AlertDialog(
            onDismissRequest = { showReorderDialog = false },
            containerColor = ObsidianSurface,
            title = {
                Column {
                    Text("Page Serial Order")
                    Text(
                        text = "Tap any # badge to jump directly to a page number",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(workingOrder, key = { _, p -> p.id }) { idx, p ->
                        Surface(
                            color = ObsidianSurfaceMid,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = TealContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, TealAccent.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                                        .clickable { pickSerialForIdx = idx }
                                ) {
                                    Text(
                                        text = "#${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TealOnContainer,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                AsyncImage(
                                    model = File(p.bestThumbPath()),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 36.dp, height = 46.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Page ${idx + 1}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = "Tap #${idx + 1} to move",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (idx > 0) {
                                            val item = workingOrder.removeAt(idx)
                                            workingOrder.add(idx - 1, item)
                                        }
                                    },
                                    enabled = idx > 0
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                                }
                                IconButton(
                                    onClick = {
                                        if (idx < workingOrder.lastIndex) {
                                            val item = workingOrder.removeAt(idx)
                                            workingOrder.add(idx + 1, item)
                                        }
                                    },
                                    enabled = idx < workingOrder.lastIndex
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onReorderAllPages(workingOrder.map { it.id })
                    showReorderDialog = false
                }) { Text("Save Order", color = TealAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showReorderDialog = false }) { Text("Cancel") }
            }
        )

        pickSerialForIdx?.let { fromIdx ->
            DirectSerialPickerDialog(
                currentOneBasedIndex = fromIdx + 1,
                totalCount = workingOrder.size,
                onDismiss = { pickSerialForIdx = null },
                onSelectTargetOneBasedIndex = { targetOneBased ->
                    val toIdx = (targetOneBased - 1).coerceIn(0, workingOrder.lastIndex)
                    if (fromIdx in workingOrder.indices && fromIdx != toIdx) {
                        val moved = workingOrder.removeAt(fromIdx)
                        workingOrder.add(toIdx, moved)
                    }
                    pickSerialForIdx = null
                }
            )
        }
    }

    if (showRenameDialog) {
        var nameInput by remember { mutableStateOf(document.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameDocument(nameInput)
                    showRenameDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showFolderDialog) {
        var chosenFolderId by remember { mutableStateOf(document.folderId) }
        var tagInput by remember { mutableStateOf(document.tag) }
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Folder & Tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = chosenFolderId == null,
                                onClick = { chosenFolderId = null },
                                label = { Text("None") }
                            )
                        }
                        itemsIndexed(folders) { _, folder ->
                            FilterChip(
                                selected = chosenFolderId == folder.id,
                                onClick = { chosenFolderId = folder.id },
                                label = { Text(folder.name) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = tagInput,
                        onValueChange = { tagInput = it },
                        singleLine = true,
                        label = { Text("Tag (optional)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateFolderTag(chosenFolderId, tagInput)
                    showFolderDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showExportSheet) {
        val subset = if (exportSubsetOnly && selectedPageIds.isNotEmpty()) selectedPageIds.toSet() else null
        val targetPages = if (subset != null) document.pages.filter { subset.contains(it.id) } else document.pages
        ExportPdfBottomSheet(
            defaultTitle = if (subset != null) "${document.title} (${targetPages.size}p)" else document.title,
            pagesToExport = targetPages,
            onDismiss = { showExportSheet = false },
            onExport = { title, pageSize, quality, layout, margin, watermark, pageNums, password ->
                showExportSheet = false
                batchMode = false
                onExportPdf(title, pageSize, quality, layout, margin, watermark, pageNums, password, subset)
            }
        )
    }
}

@Composable
private fun EditorActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    isDestructive -> MaterialTheme.colorScheme.error
                    isActive -> TealAccent
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    isDestructive -> MaterialTheme.colorScheme.error
                    isActive -> TealAccent
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportPdfBottomSheet(
    defaultTitle: String,
    pagesToExport: List<PageModel>,
    onDismiss: () -> Unit,
    onExport: (
        title: String,
        pageSize: PdfPageSize,
        quality: PdfQualityPreset,
        layout: PdfPageLayout,
        margin: PdfMargin,
        watermark: String,
        includePageNumbers: Boolean,
        password: String
    ) -> Unit
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var quality by remember { mutableStateOf(PdfQualityPreset.HIGH) }
    var pageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var pageLayout by remember { mutableStateOf(PdfPageLayout.SINGLE) }
    var margin by remember { mutableStateOf(PdfMargin.NARROW) }
    var includePageNumbers by remember { mutableStateOf(false) }
    var watermarkEnabled by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var passwordEnabled by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var estimatedBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(pagesToExport, quality, pageLayout, watermarkEnabled, watermarkText) {
        estimatedBytes = withContext(Dispatchers.IO) {
            PdfEngine.estimateExportSizeBytes(
                pages = pagesToExport.map { it.enhancedImagePath to it.filterMode },
                qualityPreset = quality,
                pageLayout = pageLayout,
                watermarkText = if (watermarkEnabled) watermarkText.trim() else ""
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Export PDF", style = MaterialTheme.typography.titleLarge)
                Text("${pagesToExport.size} page(s)", style = MaterialTheme.typography.labelSmall, color = TealAccent)
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("File Name") },
                modifier = Modifier.fillMaxWidth()
            )

            // Quality Presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Clarity & Compression", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PdfQualityPreset.entries.forEach { preset ->
                        val selected = preset == quality
                        Surface(
                            color = if (selected) TealContainer else ObsidianSurfaceMid,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    if (selected) TealAccent else ObsidianBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { quality = preset }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) TealOnContainer else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = preset.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Page Size, Layout & Margin
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Page Size, Layout & Margin", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfPageSize.entries.forEach { pSize ->
                        FilterChip(
                            selected = pSize == pageSize,
                            onClick = { pageSize = pSize },
                            label = { Text(pSize.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfPageLayout.entries.forEach { layout ->
                        FilterChip(
                            selected = layout == pageLayout,
                            onClick = { pageLayout = layout },
                            label = { Text(layout.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (pageSize != PdfPageSize.FIT_IMAGE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PdfMargin.entries.forEach { m ->
                            FilterChip(
                                selected = m == margin,
                                onClick = { margin = m },
                                label = { Text("${m.label} Margin") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Page Numbers toggle
            Surface(
                color = ObsidianSurfaceMid,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Page Numbers (1 / N)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = includePageNumbers, onCheckedChange = { includePageNumbers = it })
                }
            }

            // Optional Watermark
            Surface(
                color = ObsidianSurfaceMid,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = TealAccent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Watermark", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = watermarkEnabled, onCheckedChange = { watermarkEnabled = it })
                    }
                    if (watermarkEnabled) {
                        OutlinedTextField(
                            value = watermarkText,
                            onValueChange = { watermarkText = it },
                            singleLine = true,
                            label = { Text("Watermark text") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )
                    }
                }
            }

            // Optional Password Lock
            Surface(
                color = ObsidianSurfaceMid,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Password Lock", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = passwordEnabled, onCheckedChange = { passwordEnabled = it })
                    }
                    if (passwordEnabled) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text("PDF Password") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )
                    }
                }
            }

            // Live Estimated Export Size Banner
            Surface(
                color = TealContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TealAccent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Estimated PDF Size",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${quality.label} · ${pageSize.label} · ${pageLayout.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (estimatedBytes > 0L) "~${PdfEngine.formatFileSize(estimatedBytes)}" else "Calculating…",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealAccent
                    )
                }
            }

            Button(
                onClick = {
                    val finalWm = if (watermarkEnabled) watermarkText.trim() else ""
                    val finalPw = if (passwordEnabled) password.trim() else ""
                    onExport(title, pageSize, quality, pageLayout, margin, finalWm, includePageNumbers, finalPw)
                },
                enabled = !passwordEnabled || password.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("export_sheet_confirm")
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create PDF", fontWeight = FontWeight.Bold)
            }
        }
    }
}
