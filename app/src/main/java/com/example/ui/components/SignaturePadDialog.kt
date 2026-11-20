package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.model.NormalizedPoint
import com.example.model.SignaturePlacement
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TealAccent

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onApplySignature: (List<List<NormalizedPoint>>, SignaturePlacement, Int) -> Unit
) {
    val strokes = remember { mutableStateListOf<MutableList<NormalizedPoint>>() }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
    var placement by remember { mutableStateOf(SignaturePlacement.BOTTOM_RIGHT) }
    val inkColors = listOf(
        Color(0xFF0F172A),
        Color(0xFF1D4ED8),
        Color(0xFF0F766E)
    )
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    var redrawTick by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Add Signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.5.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val w = canvasSize.width.coerceAtLeast(1).toFloat()
                                    val h = canvasSize.height.coerceAtLeast(1).toFloat()
                                    val list = mutableListOf(
                                        NormalizedPoint((start.x / w).coerceIn(0f, 1f), (start.y / h).coerceIn(0f, 1f))
                                    )
                                    strokes.add(list)
                                    redrawTick++
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val w = canvasSize.width.coerceAtLeast(1).toFloat()
                                    val h = canvasSize.height.coerceAtLeast(1).toFloat()
                                    strokes.lastOrNull()?.add(
                                        NormalizedPoint(
                                            (change.position.x / w).coerceIn(0f, 1f),
                                            (change.position.y / h).coerceIn(0f, 1f)
                                        )
                                    )
                                    redrawTick++
                                }
                            )
                        }
                ) {
                    val activeColor = inkColors[selectedColorIndex]
                    val tick = redrawTick
                    Canvas(modifier = Modifier.matchParentSize()) {
                        // Reference tick so Canvas redraws on every pointer point
                        if (tick >= 0) {
                            for (stroke in strokes) {
                                if (stroke.size < 2) continue
                                val path = Path().apply {
                                    moveTo(stroke[0].x * size.width, stroke[0].y * size.height)
                                    for (i in 1 until stroke.size) {
                                        lineTo(stroke[i].x * size.width, stroke[i].y * size.height)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = activeColor,
                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                    if (strokes.isEmpty()) {
                        Text(
                            text = "Sign here",
                            color = Color(0xFF94A3B8),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                // Ink Color & Clear
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        inkColors.forEachIndexed { index, color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (index == selectedColorIndex) 2.5.dp else 1.dp,
                                        color = if (index == selectedColorIndex) TealAccent else ObsidianBorder,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorIndex = index }
                            )
                        }
                    }
                    TextButton(onClick = {
                        strokes.clear()
                        redrawTick++
                    }) {
                        Text("Clear")
                    }
                }

                // Placement chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SignaturePlacement.entries.forEach { pos ->
                        FilterChip(
                            selected = placement == pos,
                            onClick = { placement = pos },
                            label = {
                                Text(
                                    text = when (pos) {
                                        SignaturePlacement.BOTTOM_LEFT -> "Left"
                                        SignaturePlacement.BOTTOM_CENTER -> "Center"
                                        SignaturePlacement.BOTTOM_RIGHT -> "Right"
                                    }
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = strokes.any { it.size >= 2 },
                onClick = {
                    onApplySignature(
                        strokes.map { it.toList() },
                        placement,
                        inkColors[selectedColorIndex].toArgb()
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Stamp")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
