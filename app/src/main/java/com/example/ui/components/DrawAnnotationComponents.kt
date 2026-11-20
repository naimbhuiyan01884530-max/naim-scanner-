package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.ImageProcessor
import com.example.model.DrawStroke
import com.example.model.NormalizedPoint
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceMid
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealOnContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min

private val PresetDrawColors = listOf(
    Color(0xFF0F172A), // Ink Black
    Color(0xFFEF4444), // Red
    Color(0xFF2563EB), // Blue
    Color(0xFF14B8A6), // Teal
    Color(0xFF22C55E), // Green
    Color(0xFFF59E0B), // Amber
    Color(0xFFFACC15), // Highlighter Yellow
    Color(0xFFA855F7), // Purple
    Color(0xFFEC4899), // Pink
    Color(0xFFFFFFFF)  // White
)

private val BrushWidths = listOf(
    "Fine" to 0.004f,
    "Med" to 0.0075f,
    "Bold" to 0.013f,
    "Wide" to 0.022f
)

@Composable
fun AnyColorPickerControls(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    strokeWidthFraction: Float,
    onStrokeWidthChange: (Float) -> Unit,
    isHighlighter: Boolean,
    onHighlighterChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomSpectrum by remember { mutableStateOf(false) }
    var hue by remember { mutableFloatStateOf(175f) }
    var tone by remember { mutableFloatStateOf(0.85f) } // 0..0.5 saturation ramp, 0.5..1 brightness

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quick Color Swatches + Rainbow Toggle
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                        .border(2.dp, TealAccent, CircleShape)
                        .clickable { showCustomSpectrum = !showCustomSpectrum },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = "Custom any color",
                        tint = if (isLightColor(selectedColor)) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            items(PresetDrawColors) { preset ->
                val isSelected = selectedColor.toArgb() == preset.toArgb()
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(preset)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) TealAccent else ObsidianBorder,
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(preset) }
                )
            }
        }

        // Expandable Full-Spectrum Any-Color Picker
        if (showCustomSpectrum) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurfaceMid)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Custom Any Color", style = MaterialTheme.typography.labelMedium)
                    TextButton(
                        onClick = { showCustomSpectrum = false },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Hide", color = TealAccent, style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Hue Rainbow Strip (0..360)
                SpectrumBar(
                    fraction = hue / 360f,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Red,
                            Color.Yellow,
                            Color.Green,
                            Color.Cyan,
                            Color.Blue,
                            Color.Magenta,
                            Color.Red
                        )
                    ),
                    onFractionChange = { f ->
                        hue = (f * 360f).coerceIn(0f, 360f)
                        onColorSelected(hsvToneToColor(hue, tone))
                    }
                )

                // Shade / Brightness Strip (Black -> Vivid Hue -> Pastel White)
                val pureHueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                SpectrumBar(
                    fraction = tone,
                    brush = Brush.horizontalGradient(
                        listOf(Color.Black, pureHueColor, Color.White)
                    ),
                    onFractionChange = { f ->
                        tone = f.coerceIn(0f, 1f)
                        onColorSelected(hsvToneToColor(hue, tone))
                    }
                )
            }
        }

        // Pen / Highlighter + Width Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !isHighlighter,
                onClick = { onHighlighterChange(false) },
                leadingIcon = { Icon(Icons.Filled.Brush, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("Pen") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TealContainer,
                    selectedLabelColor = TealOnContainer
                )
            )
            FilterChip(
                selected = isHighlighter,
                onClick = { onHighlighterChange(true) },
                leadingIcon = { Icon(Icons.Filled.Highlight, contentDescription = null, modifier = Modifier.size(15.dp)) },
                label = { Text("Marker") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TealContainer,
                    selectedLabelColor = TealOnContainer
                )
            )
            Spacer(Modifier.weight(1f))
            BrushWidths.forEach { (label, frac) ->
                val selected = kotlin.math.abs(strokeWidthFraction - frac) < 0.001f
                Surface(
                    color = if (selected) TealContainer else ObsidianSurfaceMid,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (selected) TealAccent else ObsidianBorder, RoundedCornerShape(8.dp))
                        .clickable { onStrokeWidthChange(frac) }
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) TealOnContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpectrumBar(
    fraction: Float,
    brush: Brush,
    onFractionChange: (Float) -> Unit
) {
    var widthPx by remember { mutableIntStateOf(1) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(brush)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(13.dp))
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onFractionChange((offset.x / widthPx).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onFractionChange((change.position.x / widthPx).coerceIn(0f, 1f))
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = fraction.coerceIn(0f, 1f) * size.width
            val cy = size.height / 2f
            drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, cy))
            drawCircle(Color.Black, radius = 9.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
        }
    }
}

private fun hsvToneToColor(hue: Float, tone: Float): Color {
    // 0..0.5 = dark-to-vivid (value 0 -> 1, sat 1), 0.5..1 = vivid-to-pastel (sat 1 -> 0, value 1)
    val sat: Float
    val value: Float
    if (tone <= 0.5f) {
        sat = 1f
        value = (tone * 2f).coerceIn(0.05f, 1f)
    } else {
        sat = (1f - (tone - 0.5f) * 2f).coerceIn(0f, 1f)
        value = 1f
    }
    return Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))
}

private fun isLightColor(color: Color): Boolean {
    val argb = color.toArgb()
    val r = AndroidColor.red(argb)
    val g = AndroidColor.green(argb)
    val b = AndroidColor.blue(argb)
    return (0.299f * r + 0.587f * g + 0.114f * b) > 155f
}

/**
 * Interactive normalized drawing overlay matched directly to the rendered page bitmap bounds.
 */
@Composable
fun InteractiveStrokeOverlay(
    strokes: MutableList<DrawStroke>,
    activeColor: Color,
    strokeWidthFraction: Float,
    isHighlighter: Boolean,
    redrawTick: Int,
    onRedrawTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    val activeStrokePoints = remember { mutableListOf<NormalizedPoint>() }

    Canvas(
        modifier = modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(activeColor, strokeWidthFraction, isHighlighter) {
                detectTapGestures { offset ->
                    val w = boxSize.width.coerceAtLeast(1).toFloat()
                    val h = boxSize.height.coerceAtLeast(1).toFloat()
                    val pt = NormalizedPoint((offset.x / w).coerceIn(0f, 1f), (offset.y / h).coerceIn(0f, 1f))
                    strokes.add(
                        DrawStroke(
                            points = listOf(pt),
                            colorArgb = activeColor.toArgb(),
                            strokeWidthFraction = strokeWidthFraction,
                            isHighlighter = isHighlighter
                        )
                    )
                    onRedrawTick()
                }
            }
            .pointerInput(activeColor, strokeWidthFraction, isHighlighter) {
                detectDragGestures(
                    onDragStart = { start ->
                        val w = boxSize.width.coerceAtLeast(1).toFloat()
                        val h = boxSize.height.coerceAtLeast(1).toFloat()
                        activeStrokePoints.clear()
                        activeStrokePoints.add(
                            NormalizedPoint((start.x / w).coerceIn(0f, 1f), (start.y / h).coerceIn(0f, 1f))
                        )
                        strokes.add(
                            DrawStroke(
                                points = activeStrokePoints.toList(),
                                colorArgb = activeColor.toArgb(),
                                strokeWidthFraction = strokeWidthFraction,
                                isHighlighter = isHighlighter
                            )
                        )
                        onRedrawTick()
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val w = boxSize.width.coerceAtLeast(1).toFloat()
                        val h = boxSize.height.coerceAtLeast(1).toFloat()
                        activeStrokePoints.add(
                            NormalizedPoint((change.position.x / w).coerceIn(0f, 1f), (change.position.y / h).coerceIn(0f, 1f))
                        )
                        if (strokes.isNotEmpty()) {
                            strokes[strokes.lastIndex] = strokes.last().copy(points = activeStrokePoints.toList())
                        }
                        onRedrawTick()
                    }
                )
            }
    ) {
        if (redrawTick >= 0) {
            val minDim = min(size.width, size.height)
            for (s in strokes) {
                if (s.points.isEmpty()) continue
                val baseColor = Color(s.colorArgb)
                val drawColor = if (s.isHighlighter) baseColor.copy(alpha = 0.38f) else baseColor
                val widthPx = (minDim * s.strokeWidthFraction * (if (s.isHighlighter) 2.6f else 1f)).coerceIn(3f, 84f)
                if (s.points.size == 1) {
                    drawCircle(
                        color = drawColor,
                        radius = widthPx * 0.5f,
                        center = Offset(s.points[0].x * size.width, s.points[0].y * size.height)
                    )
                } else {
                    val path = Path().apply {
                        moveTo(s.points[0].x * size.width, s.points[0].y * size.height)
                        for (i in 1 until s.points.size) {
                            lineTo(s.points[i].x * size.width, s.points[i].y * size.height)
                        }
                    }
                    drawPath(
                        path = path,
                        color = drawColor,
                        style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
        }
    }
}

/**
 * Full-screen dialog for drawing anything in any color onto a scanned page image.
 */
@Composable
fun PageDrawDialog(
    imagePath: String,
    pageLabel: String,
    onDismiss: () -> Unit,
    onSave: (List<DrawStroke>) -> Unit
) {
    var bitmap by remember(imagePath) { mutableStateOf<Bitmap?>(null) }
    val strokes = remember(imagePath) { mutableStateListOf<DrawStroke>() }
    var redrawTick by remember { mutableIntStateOf(0) }
    var activeColor by remember { mutableStateOf(Color(0xFFEF4444)) }
    var strokeWidthFraction by remember { mutableFloatStateOf(0.0075f) }
    var isHighlighter by remember { mutableStateOf(false) }

    LaunchedEffect(imagePath) {
        bitmap = withContext(Dispatchers.IO) {
            ImageProcessor.decodeFileCached(imagePath, 1800)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close draw")
                }
                Text(
                    text = "Draw on $pageLabel",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            strokes.removeAt(strokes.lastIndex)
                            redrawTick++
                        }
                    },
                    enabled = strokes.isNotEmpty()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo stroke")
                }
                IconButton(
                    onClick = {
                        strokes.clear()
                        redrawTick++
                    },
                    enabled = strokes.isNotEmpty()
                ) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear drawing")
                }
                Button(
                    onClick = { onSave(strokes.toList()) },
                    enabled = strokes.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TealAccent,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("page_draw_save")
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save")
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    val imgAspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
                    val boxAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
                    val fitModifier = if (imgAspect > boxAspect) {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(imgAspect)
                    } else {
                        Modifier
                            .fillMaxSize()
                            .aspectRatio(imgAspect, matchHeightConstraintsFirst = true)
                    }
                    Box(
                        modifier = fitModifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = pageLabel,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                        InteractiveStrokeOverlay(
                            strokes = strokes,
                            activeColor = activeColor,
                            strokeWidthFraction = strokeWidthFraction,
                            isHighlighter = isHighlighter,
                            redrawTick = redrawTick,
                            onRedrawTick = { redrawTick++ },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                AnyColorPickerControls(
                    selectedColor = activeColor,
                    onColorSelected = { activeColor = it },
                    strokeWidthFraction = strokeWidthFraction,
                    onStrokeWidthChange = { strokeWidthFraction = it },
                    isHighlighter = isHighlighter,
                    onHighlighterChange = { isHighlighter = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

/**
 * 1-tap serial position picker so users can jump any page directly to any page number (1..N).
 */
@Composable
fun DirectSerialPickerDialog(
    currentOneBasedIndex: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onSelectTargetOneBasedIndex: (Int) -> Unit
) {
    val numbers = remember(totalCount) { (1..totalCount).toList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Move Page #$currentOneBasedIndex To") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 52.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(numbers) { pos ->
                    val isCurrent = pos == currentOneBasedIndex
                    Surface(
                        color = if (isCurrent) TealContainer else ObsidianSurfaceMid,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = 1.dp,
                                color = if (isCurrent) TealAccent else ObsidianBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectTargetOneBasedIndex(pos) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$pos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) TealOnContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
