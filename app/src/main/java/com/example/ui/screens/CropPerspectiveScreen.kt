package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.ImageProcessor
import com.example.model.NormalizedPoint
import com.example.model.PageModel
import com.example.model.QuadCorners
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TealAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

private enum class DragCorner { TL, TR, BR, BL }

@Composable
fun CropPerspectiveScreen(
    page: PageModel,
    onDetectCorners: suspend (String) -> QuadCorners,
    onApply: (QuadCorners, Int, Boolean) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val scope = rememberCoroutineScope()
    var rawBitmap by remember(page.id) { mutableStateOf<Bitmap?>(null) }
    var imageBitmap by remember(page.id) { mutableStateOf<ImageBitmap?>(null) }
    var corners by remember(page.id) { mutableStateOf(page.corners) }
    var rotationDegrees by remember(page.id) { mutableIntStateOf(page.rotationDegrees) }
    var flipHorizontal by remember(page.id) { mutableStateOf(page.flipHorizontal) }
    var activeCorner by remember { mutableStateOf<DragCorner?>(null) }

    LaunchedEffect(page.rawImagePath) {
        val loaded = withContext(Dispatchers.IO) {
            ImageProcessor.decodeFileCached(page.rawImagePath, 1600)
        }
        rawBitmap = loaded
        imageBitmap = loaded?.asImageBitmap()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("crop_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Perspective Crop",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = { onApply(corners, rotationDegrees, flipHorizontal) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealAccent,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("crop_apply_button")
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_apply))
            }
        }

        // Interactive Crop Canvas + Zoom Loupe
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val bmp = imageBitmap
            if (bmp != null) {
                val containerW = constraints.maxWidth.toFloat()
                val containerH = constraints.maxHeight.toFloat()
                val scale = min(containerW / bmp.width.toFloat(), containerH / bmp.height.toFloat())
                val drawW = bmp.width * scale
                val drawH = bmp.height * scale
                val leftOffset = (containerW - drawW) / 2f
                val topOffset = (containerH - drawH) / 2f
                val density = LocalDensity.current
                val touchRadiusPx = with(density) { 44.dp.toPx() }

                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(bmp, leftOffset, topOffset, drawW, drawH) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val pts = mapOf(
                                            DragCorner.TL to Offset(leftOffset + corners.topLeft.x * drawW, topOffset + corners.topLeft.y * drawH),
                                            DragCorner.TR to Offset(leftOffset + corners.topRight.x * drawW, topOffset + corners.topRight.y * drawH),
                                            DragCorner.BR to Offset(leftOffset + corners.bottomRight.x * drawW, topOffset + corners.bottomRight.y * drawH),
                                            DragCorner.BL to Offset(leftOffset + corners.bottomLeft.x * drawW, topOffset + corners.bottomLeft.y * drawH)
                                        )
                                        val nearest = pts.minByOrNull { (_, p) ->
                                            hypot((p.x - start.x).toDouble(), (p.y - start.y).toDouble())
                                        }
                                        if (nearest != null) {
                                            val dist = hypot((nearest.value.x - start.x).toDouble(), (nearest.value.y - start.y).toDouble())
                                            activeCorner = if (dist <= touchRadiusPx * 1.8) nearest.key else null
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val target = activeCorner ?: return@detectDragGestures
                                        val dx = dragAmount.x / drawW
                                        val dy = dragAmount.y / drawH
                                        corners = when (target) {
                                            DragCorner.TL -> corners.copy(
                                                topLeft = NormalizedPoint(
                                                    (corners.topLeft.x + dx).coerceIn(0f, corners.topRight.x - 0.08f),
                                                    (corners.topLeft.y + dy).coerceIn(0f, corners.bottomLeft.y - 0.08f)
                                                )
                                            )
                                            DragCorner.TR -> corners.copy(
                                                topRight = NormalizedPoint(
                                                    (corners.topRight.x + dx).coerceIn(corners.topLeft.x + 0.08f, 1f),
                                                    (corners.topRight.y + dy).coerceIn(0f, corners.bottomRight.y - 0.08f)
                                                )
                                            )
                                            DragCorner.BR -> corners.copy(
                                                bottomRight = NormalizedPoint(
                                                    (corners.bottomRight.x + dx).coerceIn(corners.bottomLeft.x + 0.08f, 1f),
                                                    (corners.bottomRight.y + dy).coerceIn(corners.topRight.y + 0.08f, 1f)
                                                )
                                            )
                                            DragCorner.BL -> corners.copy(
                                                bottomLeft = NormalizedPoint(
                                                    (corners.bottomLeft.x + dx).coerceIn(0f, corners.bottomRight.x - 0.08f),
                                                    (corners.bottomLeft.y + dy).coerceIn(corners.topLeft.y + 0.08f, 1f)
                                                )
                                            )
                                        }
                                    },
                                    onDragEnd = { activeCorner = null },
                                    onDragCancel = { activeCorner = null }
                                )
                            }
                    ) {
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(leftOffset.roundToInt(), topOffset.roundToInt()),
                            dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt())
                        )

                        val tl = Offset(leftOffset + corners.topLeft.x * drawW, topOffset + corners.topLeft.y * drawH)
                        val tr = Offset(leftOffset + corners.topRight.x * drawW, topOffset + corners.topRight.y * drawH)
                        val br = Offset(leftOffset + corners.bottomRight.x * drawW, topOffset + corners.bottomRight.y * drawH)
                        val bl = Offset(leftOffset + corners.bottomLeft.x * drawW, topOffset + corners.bottomLeft.y * drawH)

                        // Dark mask outside quad
                        val maskPath = Path().apply {
                            addRect(androidx.compose.ui.geometry.Rect(leftOffset, topOffset, leftOffset + drawW, topOffset + drawH))
                        }
                        val quadPath = Path().apply {
                            moveTo(tl.x, tl.y)
                            lineTo(tr.x, tr.y)
                            lineTo(br.x, br.y)
                            lineTo(bl.x, bl.y)
                            close()
                        }
                        val outsidePath = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, maskPath, quadPath)
                        drawPath(outsidePath, color = Color.Black.copy(alpha = 0.56f))

                        // Quad border lines
                        drawPath(quadPath, color = TealAccent, style = Stroke(width = 2.5.dp.toPx()))

                        // Corner handles
                        val handles = listOf(
                            DragCorner.TL to tl,
                            DragCorner.TR to tr,
                            DragCorner.BR to br,
                            DragCorner.BL to bl
                        )
                        handles.forEach { (corner, pt) ->
                            val isSelected = corner == activeCorner
                            drawCircle(
                                color = if (isSelected) TealAccent else Color.White,
                                radius = if (isSelected) 13.dp.toPx() else 10.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = TealAccent,
                                radius = if (isSelected) 13.dp.toPx() else 10.dp.toPx(),
                                center = pt,
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                    }

                    // Precision Zoom Loupe when dragging a corner
                    val dragging = activeCorner
                    if (dragging != null) {
                        val normPt = when (dragging) {
                            DragCorner.TL -> corners.topLeft
                            DragCorner.TR -> corners.topRight
                            DragCorner.BR -> corners.bottomRight
                            DragCorner.BL -> corners.bottomLeft
                        }
                        val alignTopRight = normPt.x < 0.5f
                        ZoomLoupe(
                            bitmap = bmp,
                            normalizedPoint = normPt,
                            modifier = Modifier
                                .align(if (alignTopRight) Alignment.TopEnd else Alignment.TopStart)
                                .padding(8.dp)
                        )
                    }
                }
            } else {
                Text("Loading image…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Bottom Orientation & Auto-Detect Controls
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CropToolButton(
                        icon = Icons.Filled.AutoFixHigh,
                        label = stringResource(R.string.action_auto),
                        onClick = {
                            scope.launch {
                                corners = onDetectCorners(page.rawImagePath)
                            }
                        }
                    )
                    CropToolButton(
                        icon = Icons.AutoMirrored.Filled.RotateLeft,
                        label = "Left 90°",
                        onClick = { rotationDegrees = (rotationDegrees + 270) % 360 }
                    )
                    CropToolButton(
                        icon = Icons.AutoMirrored.Filled.RotateRight,
                        label = "Right 90°",
                        onClick = { rotationDegrees = (rotationDegrees + 90) % 360 }
                    )
                    CropToolButton(
                        icon = Icons.Filled.Flip,
                        label = "Flip",
                        onClick = { flipHorizontal = !flipHorizontal }
                    )
                    CropToolButton(
                        icon = Icons.Filled.RestartAlt,
                        label = stringResource(R.string.action_reset),
                        onClick = {
                            corners = QuadCorners.FULL
                            rotationDegrees = 0
                            flipHorizontal = false
                        }
                    )
                }

                if (rotationDegrees != 0 || flipHorizontal) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            if (rotationDegrees != 0) append("Rotation: ${rotationDegrees}°  ")
                            if (flipHorizontal) append("Flipped")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TealAccent,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomLoupe(
    bitmap: ImageBitmap,
    normalizedPoint: NormalizedPoint,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(124.dp)
            .clip(CircleShape)
            .background(ObsidianBg)
            .border(2.5.dp, TealAccent, CircleShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cropRadius = (min(bitmap.width, bitmap.height) * 0.11f).roundToInt().coerceAtLeast(32)
            val cx = (normalizedPoint.x * bitmap.width).roundToInt()
            val cy = (normalizedPoint.y * bitmap.height).roundToInt()
            val srcLeft = (cx - cropRadius).coerceIn(0, (bitmap.width - cropRadius * 2).coerceAtLeast(0))
            val srcTop = (cy - cropRadius).coerceIn(0, (bitmap.height - cropRadius * 2).coerceAtLeast(0))
            val srcW = (cropRadius * 2).coerceAtMost(bitmap.width - srcLeft)
            val srcH = (cropRadius * 2).coerceAtMost(bitmap.height - srcTop)

            drawImage(
                image = bitmap,
                srcOffset = IntOffset(srcLeft, srcTop),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            )

            // Crosshair
            val midX = size.width / 2f
            val midY = size.height / 2f
            drawLine(
                color = TealAccent,
                start = Offset(midX - 18.dp.toPx(), midY),
                end = Offset(midX + 18.dp.toPx(), midY),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = TealAccent,
                start = Offset(midX, midY - 18.dp.toPx()),
                end = Offset(midX, midY + 18.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
private fun CropToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
