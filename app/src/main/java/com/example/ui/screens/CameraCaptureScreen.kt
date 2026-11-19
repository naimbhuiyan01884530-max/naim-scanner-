package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TealAccent
import java.io.File

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
    var isCapturing by remember { mutableStateOf(false) }
    val capturedFiles = remember { mutableStateListOf<File>() }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }
    var captureFlashPulse by remember { mutableIntStateOf(0) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener({
            runCatching {
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                cameraRef = camera
                imageCaptureRef = imageCapture
            }
        }, executor)

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Subtle document corner alignment guide overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padX = size.width * 0.1f
            val padY = size.height * 0.18f
            val left = padX
            val right = size.width - padX
            val top = padY
            val bottom = size.height - padY
            val len = 28.dp.toPx()
            val stroke = 2.5.dp.toPx()
            val guideColor = TealAccent.copy(alpha = 0.72f)

            // Top-Left
            drawLine(guideColor, Offset(left, top), Offset(left + len, top), stroke)
            drawLine(guideColor, Offset(left, top), Offset(left, top + len), stroke)
            // Top-Right
            drawLine(guideColor, Offset(right, top), Offset(right - len, top), stroke)
            drawLine(guideColor, Offset(right, top), Offset(right, top + len), stroke)
            // Bottom-Left
            drawLine(guideColor, Offset(left, bottom), Offset(left + len, bottom), stroke)
            drawLine(guideColor, Offset(left, bottom), Offset(left, bottom - len), stroke)
            // Bottom-Right
            drawLine(guideColor, Offset(right, bottom), Offset(right - len, bottom), stroke)
            drawLine(guideColor, Offset(right, bottom), Offset(right, bottom - len), stroke)
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

        // Bottom One-Handed Capture Dock
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
                // Live batch thumbnail + count
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val lastFile = capturedFiles.lastOrNull()
                    if (lastFile != null) {
                        AsyncImage(
                            model = lastFile,
                            contentDescription = "Last captured page",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.5.dp, TealAccent, RoundedCornerShape(10.dp))
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
                    } else {
                        Spacer(Modifier.size(52.dp))
                    }
                }

                // Shutter button
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .border(3.5.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(if (isCapturing) TealAccent.copy(alpha = 0.5f) else TealAccent)
                        .clickable(enabled = !isCapturing) {
                            val ic = imageCaptureRef ?: return@clickable
                            isCapturing = true
                            val outFile = File(
                                context.cacheDir,
                                "cam_${System.currentTimeMillis()}_${captureFlashPulse++}.jpg"
                            )
                            val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
                            ic.takePicture(
                                opts,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        isCapturing = false
                                        capturedFiles.add(outFile)
                                        if (singleShotMode) {
                                            onFinishBatch(capturedFiles.toList(), autoDetect)
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        isCapturing = false
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

                // Done button
                if (!singleShotMode) {
                    Button(
                        onClick = { onFinishBatch(capturedFiles.toList(), autoDetect) },
                        enabled = capturedFiles.isNotEmpty(),
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
                            text = if (capturedFiles.isEmpty()) stringResource(R.string.action_done)
                            else "${stringResource(R.string.action_done)} (${capturedFiles.size})"
                        )
                    }
                } else {
                    Spacer(Modifier.width(80.dp))
                }
            }
        }
    }
}
