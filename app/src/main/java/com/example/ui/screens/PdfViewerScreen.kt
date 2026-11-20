package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.PdfEngine
import com.example.model.PdfFileModel
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TealAccent
import com.example.util.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PdfViewerScreen(
    pdf: PdfFileModel,
    onBack: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onSaveOrderedOrSplitPages: (List<Int>, String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var pageCount by remember(pdf.id) { mutableStateOf(0) }
    var renderError by remember(pdf.id) { mutableStateOf(false) }
    var editPagesMode by remember { mutableStateOf(false) }
    val pageOrder = remember(pdf.id) { mutableStateListOf<Int>() }
    val selectedPages = remember(pdf.id) { mutableStateListOf<Int>() }
    var showSaveDialog by remember { mutableStateOf(false) }
    var zoomScale by remember(pdf.id) { mutableFloatStateOf(1f) }
    var panOffset by remember(pdf.id) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pdf.filePath) {
        val count = withContext(Dispatchers.IO) { PdfEngine.getPdfPageCount(pdf.filePath) }
        if (count <= 0) {
            renderError = true
        } else {
            pageCount = count
            pageOrder.clear()
            pageOrder.addAll(0 until count)
            renderError = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("pdf_viewer_back")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pdf.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${PdfEngine.formatFileSize(pdf.sizeBytes)} · ${pdf.pageCount}p · ${pdf.pageSize.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent
                )
            }

            if (!renderError) {
                IconButton(
                    onClick = {
                        if (zoomScale > 1.2f) {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        } else {
                            zoomScale = 2.2f
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (zoomScale > 1.2f) Icons.Filled.ZoomOut else Icons.Filled.ZoomIn,
                        contentDescription = "Zoom",
                        tint = if (zoomScale > 1.2f) TealAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!renderError && pageCount >= 1) {
                IconButton(
                    onClick = {
                        editPagesMode = !editPagesMode
                        if (!editPagesMode) {
                            selectedPages.clear()
                            pageOrder.clear()
                            pageOrder.addAll(0 until pageCount)
                        }
                    },
                    modifier = Modifier.testTag("pdf_split_toggle")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.CallSplit,
                        contentDescription = "Split or reorder pages",
                        tint = if (editPagesMode) TealAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onSaveToDownloads) {
                Icon(Icons.Filled.Download, contentDescription = "Save to Downloads", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(
                onClick = { ShareUtils.sharePdf(context, pdf.filePath, pdf.title) },
                modifier = Modifier.testTag("pdf_viewer_share")
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Share PDF", tint = TealAccent)
            }
        }

        if (renderError || pdf.isPasswordProtected) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (pdf.isPasswordProtected) "Password-Protected PDF" else "PDF Ready",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${PdfEngine.formatFileSize(pdf.sizeBytes)} · ${pdf.pageCount} page(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { ShareUtils.openPdfExternally(context, pdf.filePath) }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open")
                            }
                            Button(
                                onClick = { ShareUtils.sharePdf(context, pdf.filePath, pdf.title) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealAccent,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share")
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(0.dp))
                    .pointerInput(pdf.id, editPagesMode) {
                        if (!editPagesMode) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoomScale > 1.2f) {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        zoomScale = 2.5f
                                    }
                                }
                            )
                        }
                    }
                    .pointerInput(pdf.id, editPagesMode) {
                        if (!editPagesMode) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pointerCount = event.changes.count { it.pressed }
                                    if (pointerCount >= 2 || zoomScale > 1.05f) {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                        zoomScale = newScale
                                        panOffset = if (newScale <= 1.05f) {
                                            Offset.Zero
                                        } else {
                                            Offset(
                                                (panOffset.x + pan.x).coerceIn(-900f, 900f),
                                                (panOffset.y + pan.y).coerceIn(-600f, 600f)
                                            )
                                        }
                                        if (pointerCount >= 2 || zoomScale > 1.05f) {
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                    }
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(pageOrder, key = { idx, pageIdx -> "${pageIdx}_$idx" }) { listIdx, pageIdx ->
                        val isSelected = selectedPages.contains(pageIdx)
                        PdfRenderedPageCard(
                            pdfPath = pdf.filePath,
                            pageIndex = pageIdx,
                            editMode = editPagesMode,
                            canMoveUp = listIdx > 0,
                            canMoveDown = listIdx < pageOrder.lastIndex,
                            canDelete = pageOrder.size > 1,
                            isSelected = isSelected,
                            onMoveUp = {
                                if (listIdx > 0) {
                                    val item = pageOrder.removeAt(listIdx)
                                    pageOrder.add(listIdx - 1, item)
                                }
                            },
                            onMoveDown = {
                                if (listIdx < pageOrder.lastIndex) {
                                    val item = pageOrder.removeAt(listIdx)
                                    pageOrder.add(listIdx + 1, item)
                                }
                            },
                            onRemovePage = {
                                if (pageOrder.size > 1) {
                                    pageOrder.removeAt(listIdx)
                                    selectedPages.remove(pageIdx)
                                }
                            },
                            onToggleSelect = {
                                if (selectedPages.contains(pageIdx)) selectedPages.remove(pageIdx)
                                else selectedPages.add(pageIdx)
                            }
                        )
                    }
                }
            }

            if (editPagesMode) {
                val targetList = if (selectedPages.isNotEmpty()) pageOrder.filter { selectedPages.contains(it) } else pageOrder.toList()
                Surface(
                    color = ObsidianSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedPages.isNotEmpty()) "${selectedPages.size} checked" else "${pageOrder.size} pages in order",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(
                            onClick = { showSaveDialog = true },
                            enabled = targetList.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TealAccent,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(if (selectedPages.isNotEmpty()) "Extract (${targetList.size}p)" else "Save PDF (${targetList.size}p)")
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        var newTitle by remember {
            mutableStateOf(if (selectedPages.isNotEmpty()) "${pdf.title} Extract" else "${pdf.title} Reordered")
        }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save New PDF") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    label = { Text("File Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = if (selectedPages.isNotEmpty()) {
                        pageOrder.filter { selectedPages.contains(it) }
                    } else {
                        pageOrder.toList()
                    }
                    onSaveOrderedOrSplitPages(target, newTitle)
                    showSaveDialog = false
                    editPagesMode = false
                    selectedPages.clear()
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PdfRenderedPageCard(
    pdfPath: String,
    pageIndex: Int,
    editMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    isSelected: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemovePage: () -> Unit,
    onToggleSelect: () -> Unit
) {
    var bitmap by remember(pdfPath, pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pdfPath, pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            PdfEngine.renderPdfPage(pdfPath, pageIndex, 2000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) TealAccent else ObsidianBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .then(if (editMode) Modifier.clickable(onClick = onToggleSelect) else Modifier)
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.71f),
                contentAlignment = Alignment.Center
            ) {
                Text("Page ${pageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Surface(
            color = ObsidianBg.copy(alpha = 0.78f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            Text(
                text = "P${pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }

        if (editMode) {
            Surface(
                color = ObsidianBg.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onRemovePage, enabled = canDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove page", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onToggleSelect, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = "Select page ${pageIndex + 1}",
                            tint = if (isSelected) TealAccent else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
