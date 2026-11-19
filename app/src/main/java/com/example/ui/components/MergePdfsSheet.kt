package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.engine.PdfEngine
import com.example.model.PdfFileModel
import com.example.model.PdfPageSize
import com.example.model.PdfQualityPreset
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceMid
import com.example.ui.theme.TealAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergePdfsSheet(
    pdfs: List<PdfFileModel>,
    onDismiss: () -> Unit,
    onMerge: (List<Long>, String, PdfPageSize, PdfQualityPreset, String) -> Unit
) {
    val mergeable = remember(pdfs) { pdfs.filter { !it.isPasswordProtected } }
    val orderedSelection = remember { mutableStateListOf<PdfFileModel>() }
    var title by remember { mutableStateOf("Merged PDF") }
    var quality by remember { mutableStateOf(PdfQualityPreset.BALANCED) }
    var pageSize by remember { mutableStateOf(PdfPageSize.A4) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Merge PDFs", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${orderedSelection.size} selected",
                    style = MaterialTheme.typography.labelSmall,
                    color = TealAccent
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("Merged File Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PdfQualityPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = quality == preset,
                        onClick = { quality = preset },
                        label = { Text(preset.label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (mergeable.size < 2) {
                Text(
                    text = "Save or import at least 2 unlocked PDFs to merge.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val displayList = orderedSelection + mergeable.filter { item -> orderedSelection.none { it.id == item.id } }
                    itemsIndexed(displayList, key = { _, it -> it.id }) { _, pdf ->
                        val selIndex = orderedSelection.indexOfFirst { it.id == pdf.id }
                        val isSelected = selIndex >= 0
                        Surface(
                            color = ObsidianSurfaceMid,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    1.dp,
                                    if (isSelected) TealAccent else ObsidianBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    if (isSelected) orderedSelection.removeAt(selIndex)
                                    else orderedSelection.add(pdf)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (isSelected) TealAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isSelected) "${selIndex + 1}. ${pdf.title}" else pdf.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${pdf.pageCount}p · ${PdfEngine.formatFileSize(pdf.sizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    IconButton(
                                        onClick = {
                                            if (selIndex > 0) {
                                                val item = orderedSelection.removeAt(selIndex)
                                                orderedSelection.add(selIndex - 1, item)
                                            }
                                        },
                                        enabled = selIndex > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            if (selIndex < orderedSelection.lastIndex) {
                                                val item = orderedSelection.removeAt(selIndex)
                                                orderedSelection.add(selIndex + 1, item)
                                            }
                                        },
                                        enabled = selIndex < orderedSelection.lastIndex,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onMerge(orderedSelection.map { it.id }, title, pageSize, quality, "")
                },
                enabled = orderedSelection.size >= 2,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Filled.Merge, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Merge (${orderedSelection.size} PDFs)")
            }
        }
    }
}
