package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.example.R
import com.example.model.FilterMode
import com.example.model.FolderModel
import com.example.model.PageModel
import com.example.model.PdfPageSize
import com.example.model.PdfQualityPreset
import com.example.model.ScanDocumentModel
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.ObsidianSurfaceMid
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealOnContainer
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentEditorScreen(
    document: ScanDocumentModel,
    folders: List<FolderModel>,
    onBack: () -> Unit,
    onRenameDocument: (String) -> Unit,
    onUpdateFolderTag: (Long?, String) -> Unit,
    onOpenCrop: (PageModel) -> Unit,
    onQuickRotate: (PageModel) -> Unit,
    onRetakePage: (PageModel) -> Unit,
    onDeletePage: (PageModel) -> Unit,
    onApplyFilter: (PageModel, FilterMode) -> Unit,
    onApplyFilterAll: (FilterMode) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onAddPageCamera: () -> Unit,
    onAddPagesGallery: (List<android.net.Uri>) -> Unit,
    onExportPdf: (
        title: String,
        pageSize: PdfPageSize,
        quality: PdfQualityPreset,
        watermark: String,
        password: String
    ) -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedIndex by remember(document.pages.size) {
        mutableIntStateOf(0.coerceAtMost((document.pages.size - 1).coerceAtLeast(0)))
    }
    val safeIndex = selectedIndex.coerceIn(0, (document.pages.size - 1).coerceAtLeast(0))
    val activePage = document.pages.getOrNull(safeIndex)

    var showRenameDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
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
                onClick = onBack,
                modifier = Modifier.testTag("editor_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { showRenameDialog = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
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
                onClick = { showExportSheet = true },
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
            // Zoomable Active Page Preview
            val context = LocalContext.current
            var zoomScale by remember(activePage.id) { mutableFloatStateOf(1f) }
            var panOffset by remember(activePage.id) { mutableStateOf(Offset.Zero) }
            val imageFile = File(activePage.enhancedImagePath)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ObsidianSurface)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                    .pointerInput(activePage.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
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

                // Page Indicator Pill
                Surface(
                    color = ObsidianBg.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "${safeIndex + 1} / ${document.pages.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Enhancement Mode Chips + Apply All
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(FilterMode.entries) { _, mode ->
                    val selected = activePage.filterMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onApplyFilter(activePage, mode) },
                        label = { Text(mode.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ObsidianSurface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = TealContainer,
                            selectedLabelColor = TealOnContainer
                        )
                    )
                }
                item {
                    TextButton(onClick = { onApplyFilterAll(activePage.filterMode) }) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Apply All")
                    }
                }
            }

            // Quick Page Actions (Crop, Rotate, Retake, Reorder, Delete)
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditorActionIcon(Icons.Filled.Crop, "Crop") { onOpenCrop(activePage) }
                    EditorActionIcon(Icons.AutoMirrored.Filled.RotateRight, "Rotate") { onQuickRotate(activePage) }
                    EditorActionIcon(Icons.Filled.CameraAlt, "Retake") { onRetakePage(activePage) }
                    EditorActionIcon(Icons.Filled.DeleteOutline, "Remove", isDestructive = true) {
                        onDeletePage(activePage)
                        if (safeIndex > 0) selectedIndex = safeIndex - 1
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Multi-Page Thumbnail Reorder Strip + Add Page
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
                    val thumbFile = File(p.enhancedImagePath)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(width = 58.dp, height = 74.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (isCurrent) TealAccent else ObsidianBorder,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedIndex = idx }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(thumbFile)
                                    .memoryCacheKey("${p.enhancedImagePath}_${thumbFile.lastModified()}")
                                    .build(),
                                contentDescription = "Page ${idx + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = if (isCurrent) TealAccent else ObsidianBg.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(bottomEnd = 6.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (isCurrent && document.pages.size > 1) {
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

                // Add Page (Camera)
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

                // Add Page (Gallery)
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
        ExportPdfBottomSheet(
            defaultTitle = document.title,
            pageCount = document.pages.size,
            onDismiss = { showExportSheet = false },
            onExport = { title, pageSize, quality, watermark, password ->
                showExportSheet = false
                onExportPdf(title, pageSize, quality, watermark, password)
            }
        )
    }
}

@Composable
private fun EditorActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportPdfBottomSheet(
    defaultTitle: String,
    pageCount: Int,
    onDismiss: () -> Unit,
    onExport: (
        title: String,
        pageSize: PdfPageSize,
        quality: PdfQualityPreset,
        watermark: String,
        password: String
    ) -> Unit
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var quality by remember { mutableStateOf(PdfQualityPreset.BALANCED) }
    var pageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var watermarkEnabled by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var passwordEnabled by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Export PDF", style = MaterialTheme.typography.titleLarge)
                Text("$pageCount page(s)", style = MaterialTheme.typography.labelSmall, color = TealAccent)
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("File Name") },
                modifier = Modifier.fillMaxWidth()
            )

            // Quality / File Size Presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Size & Quality", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) TealOnContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when (preset) {
                                        PdfQualityPreset.SMALL -> "Tiny MB"
                                        PdfQualityPreset.BALANCED -> "Sharp"
                                        PdfQualityPreset.HIGH -> "Max HD"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Page Size
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Page Size", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfPageSize.entries.forEach { pSize ->
                        val selected = pSize == pageSize
                        FilterChip(
                            selected = selected,
                            onClick = { pageSize = pSize },
                            label = { Text(pSize.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Optional Watermark
            Surface(
                color = ObsidianSurfaceMid,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
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
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
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

            Button(
                onClick = {
                    val finalWm = if (watermarkEnabled) watermarkText.trim() else ""
                    val finalPw = if (passwordEnabled) password.trim() else ""
                    onExport(title, pageSize, quality, finalWm, finalPw)
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
                    .testTag("confirm_export_pdf_button")
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_export_pdf), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
