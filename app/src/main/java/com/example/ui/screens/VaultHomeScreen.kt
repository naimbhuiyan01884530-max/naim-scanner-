package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.engine.PdfEngine
import com.example.model.PdfFileModel
import com.example.model.ScanDocumentModel
import com.example.ui.ScannerUiState
import com.example.ui.VaultTab
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.ObsidianSurfaceMid
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealOnContainer
import com.example.util.ShareUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VaultHomeScreen(
    uiState: ScannerUiState,
    onTabSelected: (VaultTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFolderSelected: (Long?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onStartCameraScan: () -> Unit,
    onPickGalleryImages: (List<Uri>) -> Unit,
    onImportExternalPdf: (Uri) -> Unit,
    onOpenMergePdfs: () -> Unit,
    onOpenDocument: (ScanDocumentModel) -> Unit,
    onRenameDocument: (Long, String) -> Unit,
    onDuplicateDocument: (Long) -> Unit,
    onDeleteDocument: (Long) -> Unit,
    onOpenPdf: (PdfFileModel) -> Unit,
    onRenamePdf: (Long, String) -> Unit,
    onDuplicatePdf: (Long) -> Unit,
    onDeletePdf: (Long) -> Unit
) {
    val context = LocalContext.current
    var showSearch by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renameDocTarget by remember { mutableStateOf<ScanDocumentModel?>(null) }
    var renamePdfTarget by remember { mutableStateOf<PdfFileModel?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris ->
        if (uris.isNotEmpty()) onPickGalleryImages(uris)
    }

    val pdfImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportExternalPdf(uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header: Clean "Naim" Branding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("home_brand_header")
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(TealAccent)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.header_brand),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onOpenMergePdfs,
                        modifier = Modifier.testTag("home_merge_pdfs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Merge,
                            contentDescription = stringResource(R.string.action_merge_pdfs),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { pdfImportLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.testTag("home_import_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileOpen,
                            contentDescription = stringResource(R.string.action_import_pdf),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            showSearch = !showSearch
                            if (!showSearch) onSearchQueryChange("")
                        },
                        modifier = Modifier.testTag("home_search_toggle")
                    ) {
                        Icon(
                            imageVector = if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = if (showSearch) TealAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Expandable Search Field
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 10.dp)
                        .testTag("home_search_input")
                )
            }

            // Segmented Tab Switcher: Scans / PDFs
            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    VaultTabButton(
                        label = "${stringResource(R.string.tab_scans)} (${uiState.documents.size})",
                        selected = uiState.activeTab == VaultTab.SCANS,
                        onClick = { onTabSelected(VaultTab.SCANS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_scans")
                    )
                    VaultTabButton(
                        label = "${stringResource(R.string.tab_pdfs)} (${uiState.pdfs.size})",
                        selected = uiState.activeTab == VaultTab.PDFS,
                        onClick = { onTabSelected(VaultTab.PDFS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tab_pdfs")
                    )
                }
            }

            // Folder Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedFolderId == null,
                        onClick = { onFolderSelected(null) },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ObsidianSurface,
                            selectedContainerColor = TealContainer,
                            selectedLabelColor = TealOnContainer
                        )
                    )
                }
                items(uiState.folders, key = { it.id }) { folder ->
                    FilterChip(
                        selected = uiState.selectedFolderId == folder.id,
                        onClick = { onFolderSelected(folder.id) },
                        label = { Text(folder.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ObsidianSurface,
                            selectedContainerColor = TealContainer,
                            selectedLabelColor = TealOnContainer
                        )
                    )
                }
                item {
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                            .clickable { showNewFolderDialog = true }
                            .testTag("add_folder_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "New folder", tint = TealAccent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Folder", style = MaterialTheme.typography.labelMedium, color = TealAccent)
                        }
                    }
                }
            }

            // Content List / Grid
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.activeTab) {
                    VaultTab.SCANS -> {
                        if (uiState.documents.isEmpty()) {
                            EmptyVaultView(
                                title = stringResource(R.string.empty_scans_title),
                                subtitle = stringResource(R.string.empty_scans_subtitle),
                                onScanClick = onStartCameraScan
                            )
                        } else {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val columns = if (maxWidth >= 600.dp) 3 else 2
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(columns),
                                    contentPadding = PaddingValues(
                                        start = 20.dp,
                                        end = 20.dp,
                                        top = 4.dp,
                                        bottom = 112.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.documents, key = { it.id }) { doc ->
                                        ScanDocumentCard(
                                            doc = doc,
                                            onOpen = { onOpenDocument(doc) },
                                            onRename = { renameDocTarget = doc },
                                            onDuplicate = { onDuplicateDocument(doc.id) },
                                            onDelete = { onDeleteDocument(doc.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    VaultTab.PDFS -> {
                        if (uiState.pdfs.isEmpty()) {
                            EmptyVaultView(
                                title = stringResource(R.string.empty_pdfs_title),
                                subtitle = stringResource(R.string.empty_pdfs_subtitle),
                                onScanClick = onStartCameraScan
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(
                                    start = 20.dp,
                                    end = 20.dp,
                                    top = 4.dp,
                                    bottom = 112.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(uiState.pdfs, key = { it.id }) { pdf ->
                                    PdfVaultCard(
                                        pdf = pdf,
                                        onOpen = { onOpenPdf(pdf) },
                                        onShare = { ShareUtils.sharePdf(context, pdf.filePath, pdf.title) },
                                        onRename = { renamePdfTarget = pdf },
                                        onDuplicate = { onDuplicatePdf(pdf.id) },
                                        onDelete = { onDeletePdf(pdf.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // One-Handed Bottom Action Bar (anchored right for effortless thumb reach)
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = ObsidianSurfaceHigh,
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .height(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                    .clickable {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .testTag("home_gallery_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.action_gallery),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_gallery),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Button(
                onClick = onStartCameraScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(18.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                contentPadding = PaddingValues(horizontal = 22.dp),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("home_scan_button")
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_scan),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showNewFolderDialog) {
        var folderInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = folderInput,
                    onValueChange = { folderInput = it },
                    singleLine = true,
                    label = { Text("Folder name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCreateFolder(folderInput)
                    showNewFolderDialog = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    renameDocTarget?.let { doc ->
        var input by remember(doc.id) { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { renameDocTarget = null },
            title = { Text("Rename Scan") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameDocument(doc.id, input)
                    renameDocTarget = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameDocTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    renamePdfTarget?.let { pdf ->
        var input by remember(pdf.id) { mutableStateOf(pdf.title) }
        AlertDialog(
            onDismissRequest = { renamePdfTarget = null },
            title = { Text("Rename PDF") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenamePdf(pdf.id, input)
                    renamePdfTarget = null
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { renamePdfTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun VaultTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) ObsidianSurfaceHigh else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) TealAccent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyVaultView(
    title: String,
    subtitle: String,
    onScanClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_vault),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(230.dp)
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(20.dp))
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onScanClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_scan))
            }
        }
    }
}

@Composable
private fun ScanDocumentCard(
    doc: ScanDocumentModel,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val firstPage = doc.pages.firstOrNull()
    val thumbFile = firstPage?.let { File(it.enhancedImagePath) }

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .background(ObsidianSurfaceMid)
            ) {
                if (thumbFile != null && thumbFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(thumbFile)
                            .memoryCacheKey("${thumbFile.absolutePath}_${thumbFile.lastModified()}")
                            .build(),
                        contentDescription = doc.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.Center)
                    )
                }

                // Page count badge
                Surface(
                    color = ObsidianBg.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${doc.pages.size}p",
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Document options",
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_duplicate)) },
                            leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatShortDate(doc.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    doc.lastPdfSizeBytes?.let { bytes ->
                        Text(
                            text = PdfEngine.formatFileSize(bytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = TealAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfVaultCard(
    pdf: PdfFileModel,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TealContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (pdf.isPasswordProtected) Icons.Filled.Lock else Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = if (pdf.isPasswordProtected) MaterialTheme.colorScheme.tertiary else TealAccent
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pdf.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${PdfEngine.formatFileSize(pdf.sizeBytes)} · ${pdf.pageCount}p · ${pdf.pageSize.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent
                )
            }

            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.action_share),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "PDF options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_duplicate)) },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

private fun formatShortDate(millis: Long): String {
    val fmt = SimpleDateFormat("MMM d", Locale.US)
    return fmt.format(Date(millis))
}
