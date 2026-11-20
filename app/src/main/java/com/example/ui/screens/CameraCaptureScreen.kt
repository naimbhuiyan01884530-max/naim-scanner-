package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.R
import com.example.engine.ImageProcessor
import com.example.model.PhotoSharpness
import com.example.model.QuadCorners
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TealAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

private enum class FlashSetting(val label: String) {
    OFF("Off"),
    AUTO("Auto"),
    ON("On"),
    TORCH("Torch")
}

@Composable
fun CameraCaptureScreen(
    singleShotMode: Boolean = false,
    onFinishBatch: (files: List<File>, autoDetect: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler {
        onCancel()
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasPermission) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = TealAccent,
                    modifier = Modifier.size(56.dp)
                )
                Text(
                    text = stringResource(R.string.camera_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                ) {
                    Text(stringResource(R.string.camera_permission_action))
                }
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianSurface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
        return
    }

    var flashSetting by remember { mutableStateOf(FlashSetting.OFF) }
    var autoDetect by remember { mutableStateOf(true) }
    var pendingSaves by remember { mutableIntStateOf(0) }
    val capturedFiles = remember { mutableStateListOf<File>() }
    val sharpnessMap = remember { mutableStateMapOf<String, PhotoSharpness>() }
    var showBatchReview by remember { mutableStateOf(false) }
    var reviewIndex by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var captureFlashPulse by remember { mutableIntStateOf(0) }
    var showShutterFlash by remember { mutableStateOf(false) }
    var liveQuad by remember { mutableStateOf<QuadCorners?>(null) }

    val captureExecutor = remember { Executors.newFixedThreadPool(2) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            captureExecutor.shutdown()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(captureFlashPulse) {
        if (captureFlashPulse > 0) {
            showShutterFlash = true
            delay(95)
            showShutterFlash = false
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var lastAnalyzedMs = 0L
        cameraProviderFuture.addListener({
            runCatching {
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                val now = System.currentTimeMillis()
                                if (autoDetect && now - lastAnalyzedMs >= 160L) {
                                    lastAnalyzedMs = now
                                    val yPlane = imageProxy.planes[0]
                                    val detected = ImageProcessor.detectDocumentCornersFromLuma(
                                        yBuffer = yPlane.buffer,
                                        rowStride = yPlane.rowStride,
                                        srcWidth = imageProxy.width,
                                        srcHeight = imageProxy.height,
                                        rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                    )
                                    mainExecutor.execute {
                                        liveQuad = detected
                                    }
                                } else if (!autoDetect) {
                                    mainExecutor.execute { liveQuad = null }
                                }
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
                cameraRef = camera
                imageCaptureRef = imageCapture
            }
        }, mainExecutor)

        onDispose {
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }
        }
    }

    LaunchedEffect(flashSetting, cameraRef, imageCaptureRef) {
        val ic = imageCaptureRef
        val cam = cameraRef
        when (flashSetting) {
            FlashSetting.OFF -> {
                cam?.cameraControl?.enableTorch(false)
                ic?.flashMode = ImageCapture.FLASH_MODE_OFF
            }
            FlashSetting.AUTO -> {
                cam?.cameraControl?.enableTorch(false)
                ic?.flashMode = ImageCapture.FLASH_MODE_AUTO
            }
            FlashSetting.ON -> {
                cam?.cameraControl?.enableTorch(false)
                ic?.flashMode = ImageCapture.FLASH_MODE_ON
            }
            FlashSetting.TORCH -> {
                ic?.flashMode = ImageCapture.FLASH_MODE_OFF
                cam?.cameraControl?.enableTorch(true)
            }
        }
    }

    val flashAlpha by animateFloatAsState(
        targetValue = if (showShutterFlash) 0.28f else 0f,
        animationSpec = tween(durationMillis = 90),
        label = "shutter_flash"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Live Auto-Crop Edge Detection Quad Overlay + Guide Corners
        val activeQuad = if (autoDetect) liveQuad else null
        val tlx by animateFloatAsState(activeQuad?.topLeft?.x ?: 0.1f, tween(140), label = "tlx")
        val tly by animateFloatAsState(activeQuad?.topLeft?.y ?: 0.18f, tween(140), label = "tly")
        val trx by animateFloatAsState(activeQuad?.topRight?.x ?: 0.9f, tween(140), label = "trx")
        val tryY by animateFloatAsState(activeQuad?.topRight?.y ?: 0.18f, tween(140), label = "try")
        val brx by animateFloatAsState(activeQuad?.bottomRight?.x ?: 0.9f, tween(140), label = "brx")
        val bry by animateFloatAsState(activeQuad?.bottomRight?.y ?: 0.82f, tween(140), label = "bry")
        val blx by animateFloatAsState(activeQuad?.bottomLeft?.x ?: 0.1f, tween(140), label = "blx")
        val bly by animateFloatAsState(activeQuad?.bottomLeft?.y ?: 0.82f, tween(140), label = "bly")

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (activeQuad != null) {
                val pTl = Offset(tlx * size.width, tly * size.height)
                val pTr = Offset(trx * size.width, tryY * size.height)
                val pBr = Offset(brx * size.width, bry * size.height)
                val pBl = Offset(blx * size.width, bly * size.height)
                val quadPath = Path().apply {
                    moveTo(pTl.x, pTl.y)
                    lineTo(pTr.x, pTr.y)
                    lineTo(pBr.x, pBr.y)
                    lineTo(pBl.x, pBl.y)
                    close()
                }
                drawPath(quadPath, color = TealAccent.copy(alpha = 0.14f))
                drawPath(quadPath, color = TealAccent, style = Stroke(width = 2.5.dp.toPx()))
                listOf(pTl, pTr, pBr, pBl).forEach { corner ->
                    drawCircle(color = TealAccent, radius = 6.dp.toPx(), center = corner)
                }
            } else {
                val padX = size.width * 0.1f
                val padY = size.height * 0.18f
                val left = padX
                val right = size.width - padX
                val top = padY
                val bottom = size.height - padY
                val len = 28.dp.toPx()
                val stroke = 2.5.dp.toPx()
                val guideColor = TealAccent.copy(alpha = 0.72f)

                drawLine(guideColor, Offset(left, top), Offset(left + len, top), stroke)
                drawLine(guideColor, Offset(left, top), Offset(left, top + len), stroke)
                drawLine(guideColor, Offset(right, top), Offset(right - len, top), stroke)
                drawLine(guideColor, Offset(right, top), Offset(right, top + len), stroke)
                drawLine(guideColor, Offset(left, bottom), Offset(left + len, bottom), stroke)
                drawLine(guideColor, Offset(left, bottom), Offset(left, bottom - len), stroke)
                drawLine(guideColor, Offset(right, bottom), Offset(right - len, bottom), stroke)
                drawLine(guideColor, Offset(right, bottom), Offset(right, bottom - len), stroke)
            }
        }

        if (flashAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha))
            )
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    capturedFiles.forEach { it.delete() }
                    onCancel()
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .testTag("camera_close_button")
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close camera", tint = Color.White)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = autoDetect,
                    onClick = { autoDetect = !autoDetect },
                    label = { Text(if (autoDetect) "Auto Edge" else "Full Frame") },
                    leadingIcon = {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Black.copy(alpha = 0.55f),
                        labelColor = Color.White,
                        selectedContainerColor = TealAccent.copy(alpha = 0.25f),
                        selectedLabelColor = TealAccent,
                        selectedLeadingIconColor = TealAccent
                    )
                )

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            flashSetting = when (flashSetting) {
                                FlashSetting.OFF -> FlashSetting.AUTO
                                FlashSetting.AUTO -> FlashSetting.ON
                                FlashSetting.ON -> FlashSetting.TORCH
                                FlashSetting.TORCH -> FlashSetting.OFF
                            }
                        }
                        .testTag("camera_flash_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val icon = when (flashSetting) {
                            FlashSetting.OFF -> Icons.Filled.FlashOff
                            FlashSetting.AUTO -> Icons.Filled.FlashAuto
                            FlashSetting.ON -> Icons.Filled.FlashOn
                            FlashSetting.TORCH -> Icons.Filled.Highlight
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Flash ${flashSetting.label}",
                            tint = if (flashSetting == FlashSetting.OFF) Color.White else TealAccent
                        )
                    }
                }
            }
        }

        // Bottom One-Handed Rapid Batch Capture Dock
        Surface(
            color = ObsidianBg.copy(alpha = 0.88f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Live batch thumbnail + count + tap to preview & remove blurry shots
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clickable(enabled = capturedFiles.isNotEmpty() && !singleShotMode) {
                            reviewIndex = capturedFiles.lastIndex.coerceAtLeast(0)
                            showBatchReview = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val lastFile = capturedFiles.lastOrNull()
                    if (lastFile != null) {
                        val lastSharpness = sharpnessMap[lastFile.absolutePath]
                        AsyncImage(
                            model = lastFile,
                            contentDescription = "Preview captured photos",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = 1.5.dp,
                                    color = if (lastSharpness == PhotoSharpness.BLURRY) MaterialTheme.colorScheme.error else TealAccent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(TealAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = capturedFiles.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    } else if (pendingSaves > 0) {
                        CircularProgressIndicator(
                            color = TealAccent,
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Spacer(Modifier.size(52.dp))
                    }
                }

                // Rapid non-blocking shutter button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(3.5.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(TealAccent)
                        .clickable {
                            val ic = imageCaptureRef ?: return@clickable
                            if (singleShotMode && pendingSaves > 0) return@clickable
                            pendingSaves++
                            val pulseIdx = captureFlashPulse++
                            val outFile = File(
                                context.cacheDir,
                                "cam_${System.currentTimeMillis()}_$pulseIdx.jpg"
                            )
                            val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                            val mainExec = ContextCompat.getMainExecutor(context)
                            ic.takePicture(
                                opts,
                                captureExecutor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        mainExec.execute {
                                            pendingSaves = (pendingSaves - 1).coerceAtLeast(0)
                                            capturedFiles.add(outFile)
                                            if (singleShotMode) {
                                                onFinishBatch(capturedFiles.toList(), autoDetect)
                                            } else {
                                                scope.launch(Dispatchers.IO) {
                                                    val s = ImageProcessor.estimatePhotoSharpness(outFile.absolutePath)
                                                    withContext(Dispatchers.Main) {
                                                        sharpnessMap[outFile.absolutePath] = s
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        mainExec.execute {
                                            pendingSaves = (pendingSaves - 1).coerceAtLeast(0)
                                        }
                                    }
                                }
                            )
                        }
                        .testTag("camera_shutter_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Capture page",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Review / Done button
                if (!singleShotMode) {
                    Button(
                        onClick = {
                            reviewIndex = 0
                            showBatchReview = true
                        },
                        enabled = capturedFiles.isNotEmpty() && pendingSaves == 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealAccent,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = ObsidianSurface,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("camera_done_button")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (capturedFiles.isEmpty()) "Review"
                            else "Review (${capturedFiles.size})"
                        )
                    }
                } else {
                    Spacer(Modifier.width(80.dp))
                }
            }
        }

        // Batch Photo Preview & Blurry Shot Removal Overlay
        if (showBatchReview && capturedFiles.isNotEmpty()) {
            BackHandler {
                showBatchReview = false
            }
            val safeIdx = reviewIndex.coerceIn(0, capturedFiles.lastIndex)
            val currentFile = capturedFiles[safeIdx]
            val currentSharpness = sharpnessMap[currentFile.absolutePath]
            var zoomScale by remember(currentFile.absolutePath) { mutableFloatStateOf(1f) }
            var panOffset by remember(currentFile.absolutePath) { mutableStateOf(Offset.Zero) }

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
                    IconButton(onClick = { showBatchReview = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to camera")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Preview Batch (${safeIdx + 1}/${capturedFiles.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        val blurryCount = capturedFiles.count { sharpnessMap[it.absolutePath] == PhotoSharpness.BLURRY }
                        Text(
                            text = if (blurryCount > 0) "$blurryCount photo(s) look blurry · pinch to inspect"
                            else "Pinch to zoom · drop unwanted shots",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (blurryCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { onFinishBatch(capturedFiles.toList(), autoDetect) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealAccent,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("batch_review_save")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Save (${capturedFiles.size})")
                    }
                }

                // Zoomable Full-Screen Shot Preview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                        .pointerInput(currentFile.absolutePath) {
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
                        .pointerInput(currentFile.absolutePath) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                zoomScale = newScale
                                panOffset = if (newScale <= 1.05f) Offset.Zero else panOffset + pan
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = currentFile,
                        contentDescription = "Captured photo ${safeIdx + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            }
                    )

                    if (currentSharpness != null) {
                        val badgeColor = when (currentSharpness) {
                            PhotoSharpness.SHARP -> TealAccent
                            PhotoSharpness.SOFT -> MaterialTheme.colorScheme.secondary
                            PhotoSharpness.BLURRY -> MaterialTheme.colorScheme.error
                        }
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.86f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (currentSharpness == PhotoSharpness.BLURRY) {
                                    Icon(
                                        Icons.Filled.WarningAmber,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = currentSharpness.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor
                                )
                            }
                        }
                    }

                    if (safeIdx > 0) {
                        IconButton(
                            onClick = { reviewIndex = safeIdx - 1 },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(6.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ObsidianBg.copy(alpha = 0.72f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous photo", tint = Color.White)
                        }
                    }

                    if (safeIdx < capturedFiles.lastIndex) {
                        IconButton(
                            onClick = { reviewIndex = safeIdx + 1 },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(6.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ObsidianBg.copy(alpha = 0.72f))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next photo", tint = Color.White)
                        }
                    }
                }

                // Discard Blurry / Retake / Add More Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val removed = capturedFiles.removeAt(safeIdx)
                            sharpnessMap.remove(removed.absolutePath)
                            removed.delete()
                            if (capturedFiles.isEmpty()) {
                                showBatchReview = false
                            } else {
                                reviewIndex = safeIdx.coerceAtMost(capturedFiles.lastIndex)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Discard")
                    }

                    OutlinedButton(
                        onClick = {
                            val removed = capturedFiles.removeAt(safeIdx)
                            sharpnessMap.remove(removed.absolutePath)
                            removed.delete()
                            reviewIndex = safeIdx.coerceAtMost((capturedFiles.size - 1).coerceAtLeast(0))
                            showBatchReview = false
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retake")
                    }

                    OutlinedButton(
                        onClick = { showBatchReview = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AddAPhoto, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                // Scrollable Batch Thumbnail Strip
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(capturedFiles, key = { _, f -> f.absolutePath }) { idx, file ->
                        val selected = idx == safeIdx
                        val blurry = sharpnessMap[file.absolutePath] == PhotoSharpness.BLURRY
                        Box(
                            modifier = Modifier
                                .size(width = 56.dp, height = 72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = when {
                                        selected -> TealAccent
                                        blurry -> MaterialTheme.colorScheme.error
                                        else -> ObsidianBorder
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { reviewIndex = idx }
                        ) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Photo ${idx + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = if (selected) TealAccent else ObsidianBg.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(bottomEnd = 6.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            if (blurry) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
