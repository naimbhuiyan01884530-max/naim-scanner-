package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.PdfEngine
import com.example.model.PdfFileModel
import com.example.model.PdfQualityPreset
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceMid
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealContainer

private enum class PdfToolAction(val title: String, val subtitle: String) {
    COMPRESS("Compress PDF", "Reduce size cleanly"),
    LOCK("Lock PDF", "Add password protection"),
    SPLIT("Split / Reorder", "Extract or remove pages"),
    TO_SCAN("PDF → Scan", "Edit, crop & enhance"),
    TO_IMAGES("PDF → Photos", "Save pages to Pictures")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsSheet(
    pdfs: List<PdfFileModel>,
    onDismiss: () -> Unit,
    onImportPdf: () -> Unit,
    onOpenMerge: () -> Unit,
    onOpenPdfForSplit: (PdfFileModel) -> Unit,
    onCompressPdf: (PdfFileModel, PdfQualityPreset) -> Unit,
    onProtectPdf: (PdfFileModel, String) -> Unit,
    onConvertPdfToScan: (PdfFileModel) -> Unit,
    onSavePdfAsImages: (PdfFileModel) -> Unit
) {
    var pendingTool by remember { mutableStateOf<PdfToolAction?>(null) }
    var compressTarget by remember { mutableStateOf<PdfFileModel?>(null) }
    var lockTarget by remember { mutableStateOf<PdfFileModel?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("PDF Toolkit", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTile(
                    icon = Icons.Filled.Merge,
                    title = "Merge PDFs",
                    subtitle = "Combine files",
                    tint = TealAccent,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onDismiss()
                        onOpenMerge()
                    }
                )
                ToolTile(
                    icon = Icons.AutoMirrored.Filled.CallSplit,
                    title = "Split & Reorder",
                    subtitle = "Extract pages",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = { pendingTool = PdfToolAction.SPLIT }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTile(
                    icon = Icons.Filled.Compress,
                    title = "Compress PDF",
                    subtitle = "Shrink size",
                    tint = TealAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { pendingTool = PdfToolAction.COMPRESS }
                )
                ToolTile(
                    icon = Icons.Filled.Lock,
                    title = "Lock PDF",
                    subtitle = "Set password",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                    onClick = { pendingTool = PdfToolAction.LOCK }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTile(
                    icon = Icons.Filled.DocumentScanner,
                    title = "PDF → Scan",
                    subtitle = "Crop & filter",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = { pendingTool = PdfToolAction.TO_SCAN }
                )
                ToolTile(
                    icon = Icons.Filled.Image,
                    title = "PDF → Photos",
                    subtitle = "Export JPGs",
                    tint = TealAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { pendingTool = PdfToolAction.TO_IMAGES }
                )
            }

            Surface(
                color = ObsidianSurfaceMid,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    .clickable {
                        onDismiss()
                        onImportPdf()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null, tint = TealAccent)
                    Spacer(Modifier.width(12.dp))
                    Text("Import PDF from Device", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    pendingTool?.let { tool ->
        val unlockedPdfs = pdfs.filter { !it.isPasswordProtected }
        AlertDialog(
            onDismissRequest = { pendingTool = null },
            containerColor = ObsidianSurface,
            title = { Text("Select PDF · ${tool.title}") },
            text = {
                if (unlockedPdfs.isEmpty()) {
                    Text(
                        text = "No unlocked PDFs yet. Export a scan or import a PDF first.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(unlockedPdfs, key = { it.id }) { pdf ->
                            Surface(
                                color = ObsidianSurfaceMid,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        pendingTool = null
                                        when (tool) {
                                            PdfToolAction.COMPRESS -> compressTarget = pdf
                                            PdfToolAction.LOCK -> lockTarget = pdf
                                            PdfToolAction.SPLIT -> {
                                                onDismiss()
                                                onOpenPdfForSplit(pdf)
                                            }
                                            PdfToolAction.TO_SCAN -> {
                                                onDismiss()
                                                onConvertPdfToScan(pdf)
                                            }
                                            PdfToolAction.TO_IMAGES -> {
                                                onDismiss()
                                                onSavePdfAsImages(pdf)
                                            }
                                        }
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = pdf.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${PdfEngine.formatFileSize(pdf.sizeBytes)} · ${pdf.pageCount}p",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TealAccent
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingTool = null }) { Text("Close") }
            }
        )
    }

    compressTarget?.let { target ->
        var chosenQuality by remember { mutableStateOf(PdfQualityPreset.SMALL) }
        AlertDialog(
            onDismissRequest = { compressTarget = null },
            containerColor = ObsidianSurface,
            title = { Text("Compress PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${target.title} (${PdfEngine.formatFileSize(target.sizeBytes)})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(PdfQualityPreset.SMALL, PdfQualityPreset.BALANCED).forEach { preset ->
                            FilterChip(
                                selected = chosenQuality == preset,
                                onClick = { chosenQuality = preset },
                                label = { Text("${preset.label} (${preset.subtitle})") }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = target
                    compressTarget = null
                    onDismiss()
                    onCompressPdf(p, chosenQuality)
                }) { Text("Compress") }
            },
            dismissButton = {
                TextButton(onClick = { compressTarget = null }) { Text("Cancel") }
            }
        )
    }

    lockTarget?.let { target ->
        var pwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { lockTarget = null },
            containerColor = ObsidianSurface,
            title = { Text("Lock PDF") },
            text = {
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Password") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = pwd.isNotBlank(),
                    onClick = {
                        val p = target
                        val finalPw = pwd
                        lockTarget = null
                        onDismiss()
                        onProtectPdf(p, finalPw)
                    }
                ) { Text("Lock") }
            },
            dismissButton = {
                TextButton(onClick = { lockTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ToolTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = ObsidianSurfaceMid,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TealContainer.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
