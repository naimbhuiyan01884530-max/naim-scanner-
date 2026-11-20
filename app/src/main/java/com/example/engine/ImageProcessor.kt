package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.media.ExifInterface
import android.net.Uri
import com.example.model.FilterMode
import com.example.model.NormalizedPoint
import com.example.model.PdfQualityPreset
import com.example.model.QuadCorners
import com.example.model.SignaturePlacement
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class EncodedPdfStream(
    val streamBytes: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val colorSpace: String,
    val bitsPerComponent: Int,
    val filterName: String
)

object ImageProcessor {

    const val MAX_CAPTURE_EDGE = 3600
    const val THUMB_EDGE = 380

    fun decodeUriNormalized(context: Context, uri: Uri, maxLongEdge: Int = MAX_CAPTURE_EDGE): Bitmap? {
        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxLongEdge)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return null

        return applyExifOrientation(raw, orientation)
    }

    fun decodeFileNormalized(path: String, maxLongEdge: Int = MAX_CAPTURE_EDGE): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxLongEdge)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeFile(path, opts) ?: return null
        return applyExifOrientation(raw, orientation)
    }

    /**
     * Returns a cached preview Bitmap for instant cropper/viewer UI responsiveness.
     * Callers must NOT recycle bitmaps returned by this method.
     */
    fun decodeFileCached(path: String, maxLongEdge: Int = 1600): Bitmap? {
        ScannerCache.getBitmap(path, maxLongEdge)?.let { return it }
        val decoded = decodeFileNormalized(path, maxLongEdge) ?: return null
        ScannerCache.putBitmap(path, maxLongEdge, decoded)
        return decoded
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        var swapDim = false
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
                matrix.postRotate(90f)
                matrix.postTranslate(bitmap.height / 2f, bitmap.width / 2f)
                swapDim = true
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.postRotate(180f, bitmap.width / 2f, bitmap.height / 2f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.postTranslate(-bitmap.width / 2f, -bitmap.height / 2f)
                matrix.postRotate(270f)
                matrix.postTranslate(bitmap.height / 2f, bitmap.width / 2f)
                swapDim = true
            }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.postScale(1f, -1f, bitmap.width / 2f, bitmap.height / 2f)
            }
            else -> return bitmap
        }
        val outW = if (swapDim) bitmap.height else bitmap.width
        val outH = if (swapDim) bitmap.width else bitmap.height
        val rotated = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(rotated).drawBitmap(bitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxLongEdge: Int): Int {
        var inSampleSize = 1
        var longEdge = max(width, height)
        while (longEdge / 2 >= maxLongEdge) {
            longEdge /= 2
            inSampleSize *= 2
        }
        return inSampleSize
    }

    fun saveBitmapJpeg(bitmap: Bitmap, outFile: File, quality: Int = 94) {
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
    }

    fun saveThumbnail(source: Bitmap, outFile: File, maxEdge: Int = THUMB_EDGE) {
        val longEdge = max(source.width, source.height).coerceAtLeast(1)
        val scale = (maxEdge.toFloat() / longEdge).coerceAtMost(1f)
        val tw = (source.width * scale).roundToInt().coerceAtLeast(1)
        val th = (source.height * scale).roundToInt().coerceAtLeast(1)
        val thumb = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply { setScale(tw.toFloat() / source.width, th.toFloat() / source.height) }
        Canvas(thumb).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        saveBitmapJpeg(thumb, outFile, 84)
        thumb.recycle()
    }

    /**
     * Real-time (<5ms) live camera Y-plane document boundary detector for viewfinder auto-crop overlay.
     */
    fun detectDocumentCornersFromLuma(
        yBuffer: ByteBuffer,
        rowStride: Int,
        srcWidth: Int,
        srcHeight: Int,
        rotationDegrees: Int
    ): QuadCorners? {
        if (srcWidth < 64 || srcHeight < 64) return null
        val stepX = max(1, srcWidth / 128)
        val stepY = max(1, srcHeight / 96)
        val w = srcWidth / stepX
        val h = srcHeight / stepY
        if (w < 24 || h < 24) return null

        val lum = FloatArray(w * h)
        val cap = yBuffer.capacity()
        for (sy in 0 until h) {
            val rowOff = sy * stepY * rowStride
            for (sx in 0 until w) {
                val idx = rowOff + sx * stepX
                lum[sy * w + sx] = if (idx in 0 until cap) (yBuffer.get(idx).toInt() and 0xFF).toFloat() else 0f
            }
        }

        val rawQuad = detectQuadOnLuminanceGrid(lum, w, h) ?: return null
        return rotateQuadNormalized(rawQuad, rotationDegrees)
    }

    private fun rotateQuadNormalized(quad: QuadCorners, rotationDegrees: Int): QuadCorners {
        fun rotPt(pt: NormalizedPoint): NormalizedPoint = when (((rotationDegrees % 360) + 360) % 360) {
            90 -> NormalizedPoint(1f - pt.y, pt.x)
            180 -> NormalizedPoint(1f - pt.x, 1f - pt.y)
            270 -> NormalizedPoint(pt.y, 1f - pt.x)
            else -> pt
        }
        val pts = listOf(
            rotPt(quad.topLeft),
            rotPt(quad.topRight),
            rotPt(quad.bottomRight),
            rotPt(quad.bottomLeft)
        )
        val tl = pts.minBy { it.x + it.y }
        val br = pts.maxBy { it.x + it.y }
        val tr = pts.maxBy { it.x - it.y }
        val bl = pts.minBy { it.x - it.y }
        return QuadCorners(tl, tr, br, bl)
    }

    /**
     * Fast Sobel gradient + luminance contrast quad detector.
     */
    fun detectDocumentCorners(source: Bitmap): QuadCorners {
        val targetMax = 180
        val scale = targetMax.toFloat() / max(source.width, source.height).coerceAtLeast(1)
        val w = (source.width * scale).roundToInt().coerceAtLeast(32)
        val h = (source.height * scale).roundToInt().coerceAtLeast(32)
        val small = Bitmap.createScaledBitmap(source, w, h, true)

        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small != source) small.recycle()

        val lum = FloatArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        return detectQuadOnLuminanceGrid(lum, w, h) ?: QuadCorners()
    }

    private fun detectQuadOnLuminanceGrid(lum: FloatArray, w: Int, h: Int): QuadCorners? {
        val edges = FloatArray(w * h)
        var maxEdge = 0f
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val gx = -lum[(y - 1) * w + (x - 1)] + lum[(y - 1) * w + (x + 1)] -
                    2f * lum[row + (x - 1)] + 2f * lum[row + (x + 1)] -
                    lum[(y + 1) * w + (x - 1)] + lum[(y + 1) * w + (x + 1)]

                val gy = -lum[(y - 1) * w + (x - 1)] - 2f * lum[(y - 1) * w + x] - lum[(y - 1) * w + (x + 1)] +
                    lum[(y + 1) * w + (x - 1)] + 2f * lum[(y + 1) * w + x] + lum[(y + 1) * w + (x + 1)]

                val mag = abs(gx) + abs(gy)
                edges[row + x] = mag
                if (mag > maxEdge) maxEdge = mag
            }
        }

        if (maxEdge < 32f) return null

        val threshold = maxEdge * 0.26f
        val marginX = (w * 0.02f).roundToInt().coerceAtLeast(2)
        val marginY = (h * 0.02f).roundToInt().coerceAtLeast(2)

        var tlBestX = marginX
        var tlBestY = marginY
        var minSum = Float.MAX_VALUE

        var brBestX = w - marginX
        var brBestY = h - marginY
        var maxSum = -Float.MAX_VALUE

        var trBestX = w - marginX
        var trBestY = marginY
        var maxDiff = -Float.MAX_VALUE

        var blBestX = marginX
        var blBestY = h - marginY
        var minDiff = Float.MAX_VALUE

        var strongCount = 0
        for (y in marginY until h - marginY) {
            for (x in marginX until w - marginX) {
                if (edges[y * w + x] >= threshold) {
                    strongCount++
                    val sum = (x + y).toFloat()
                    val diff = (x - y).toFloat()
                    if (sum < minSum) {
                        minSum = sum
                        tlBestX = x
                        tlBestY = y
                    }
                    if (sum > maxSum) {
                        maxSum = sum
                        brBestX = x
                        brBestY = y
                    }
                    if (diff > maxDiff) {
                        maxDiff = diff
                        trBestX = x
                        trBestY = y
                    }
                    if (diff < minDiff) {
                        minDiff = diff
                        blBestX = x
                        blBestY = y
                    }
                }
            }
        }

        if (strongCount < 20) return null

        val areaWidth = max(trBestX - tlBestX, brBestX - blBestX).toFloat() / w
        val areaHeight = max(blBestY - tlBestY, brBestY - trBestY).toFloat() / h

        if (areaWidth < 0.28f || areaHeight < 0.28f) {
            return null
        }

        return QuadCorners(
            topLeft = NormalizedPoint((tlBestX.toFloat() / w).coerceIn(0.01f, 0.45f), (tlBestY.toFloat() / h).coerceIn(0.01f, 0.45f)),
            topRight = NormalizedPoint((trBestX.toFloat() / w).coerceIn(0.55f, 0.99f), (trBestY.toFloat() / h).coerceIn(0.01f, 0.45f)),
            bottomRight = NormalizedPoint((brBestX.toFloat() / w).coerceIn(0.55f, 0.99f), (brBestY.toFloat() / h).coerceIn(0.55f, 0.99f)),
            bottomLeft = NormalizedPoint((blBestX.toFloat() / w).coerceIn(0.01f, 0.45f), (blBestY.toFloat() / h).coerceIn(0.55f, 0.99f))
        )
    }

    /**
     * Performs high-resolution 4-corner perspective dewarp + orientation transform.
     */
    fun warpAndOrient(
        source: Bitmap,
        corners: QuadCorners,
        rotationDegrees: Int,
        flipHorizontal: Boolean
    ): Bitmap {
        val sw = source.width.toFloat()
        val sh = source.height.toFloat()

        val srcPts = corners.toFloatArray(sw, sh)
        val topW = hypot((srcPts[2] - srcPts[0]).toDouble(), (srcPts[3] - srcPts[1]).toDouble())
        val botW = hypot((srcPts[4] - srcPts[6]).toDouble(), (srcPts[5] - srcPts[7]).toDouble())
        val leftH = hypot((srcPts[6] - srcPts[0]).toDouble(), (srcPts[7] - srcPts[1]).toDouble())
        val rightH = hypot((srcPts[4] - srcPts[2]).toDouble(), (srcPts[5] - srcPts[3]).toDouble())

        val dstW = ((topW + botW) / 2.0).roundToInt().coerceIn(64, 3800)
        val dstH = ((leftH + rightH) / 2.0).roundToInt().coerceIn(64, 4200)

        val dstPts = floatArrayOf(
            0f, 0f,
            dstW.toFloat(), 0f,
            dstW.toFloat(), dstH.toFloat(),
            0f, dstH.toFloat()
        )

        val warpMatrix = Matrix()
        warpMatrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val warped = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(warped)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(source, warpMatrix, paint)

        val normRot = ((rotationDegrees % 360) + 360) % 360
        if (normRot == 0 && !flipHorizontal) return warped

        val swapDim = normRot == 90 || normRot == 270
        val outW = if (swapDim) warped.height else warped.width
        val outH = if (swapDim) warped.width else warped.height
        val orientMatrix = Matrix()
        orientMatrix.postTranslate(-warped.width / 2f, -warped.height / 2f)
        if (flipHorizontal) {
            orientMatrix.postScale(-1f, 1f)
        }
        if (normRot != 0) {
            orientMatrix.postRotate(normRot.toFloat())
        }
        orientMatrix.postTranslate(outW / 2f, outH / 2f)

        val finalBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(finalBitmap).drawBitmap(warped, orientMatrix, paint)
        warped.recycle()
        return finalBitmap
    }

    /**
     * High-clarity document enhancement with fine local illumination normalization,
     * adaptive thresholding, and edge-preserving text sharpening.
     */
    fun applyEnhancement(
        source: Bitmap,
        mode: FilterMode,
        contrastBoost: Float = 1.0f,
        brightnessOffset: Float = 0f
    ): Bitmap {
        if (mode == FilterMode.ORIGINAL && abs(contrastBoost - 1.0f) < 0.02f && abs(brightnessOffset) < 1f) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        if (mode == FilterMode.ORIGINAL || mode == FilterMode.GRAYSCALE) {
            val out = IntArray(pixels.size)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (mode == FilterMode.GRAYSCALE) {
                    val gray = 0.299f * r + 0.587f * g + 0.114f * b
                    val adj = (((gray - 128f) * (1.15f * contrastBoost) + 132f) + brightnessOffset)
                        .roundToInt().coerceIn(0, 255)
                    out[i] = (0xFF shl 24) or (adj shl 16) or (adj shl 8) or adj
                } else {
                    val ir = (((r - 128f) * contrastBoost + 128f) + brightnessOffset).roundToInt().coerceIn(0, 255)
                    val ig = (((g - 128f) * contrastBoost + 128f) + brightnessOffset).roundToInt().coerceIn(0, 255)
                    val ib = (((b - 128f) * contrastBoost + 128f) + brightnessOffset).roundToInt().coerceIn(0, 255)
                    out[i] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
                }
            }
            return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        }

        // Fine local illumination background map for shadow removal without haloes
        val bgW = (w / 22).coerceIn(16, 160)
        val bgH = (h / 22).coerceIn(16, 160)
        val bgBmp = Bitmap.createScaledBitmap(source, bgW, bgH, true)
        val bgRaw = IntArray(bgW * bgH)
        bgBmp.getPixels(bgRaw, 0, bgW, 0, 0, bgW, bgH)
        bgBmp.recycle()

        val bgLum = FloatArray(bgW * bgH)
        for (i in bgRaw.indices) {
            val c = bgRaw[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            bgLum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        val smoothBg = FloatArray(bgW * bgH)
        for (y in 0 until bgH) {
            for (x in 0 until bgW) {
                var maxNear = 0f
                var sumNear = 0f
                var cnt = 0
                for (dy in -2..2) {
                    val ny = (y + dy).coerceIn(0, bgH - 1)
                    for (dx in -2..2) {
                        val nx = (x + dx).coerceIn(0, bgW - 1)
                        val v = bgLum[ny * bgW + nx]
                        if (v > maxNear) maxNear = v
                        sumNear += v
                        cnt++
                    }
                }
                smoothBg[y * bgW + x] = (maxNear * 0.70f + (sumNear / cnt) * 0.30f).coerceAtLeast(36f)
            }
        }

        val outLum = FloatArray(w * h)
        val outPixels = IntArray(w * h)

        for (y in 0 until h) {
            val by = min((y.toFloat() / h * bgH).toInt(), bgH - 1)
            val rowOffset = y * w
            for (x in 0 until w) {
                val bx = min((x.toFloat() / w * bgW).toInt(), bgW - 1)
                val bgEstimate = smoothBg[by * bgW + bx]
                val idx = rowOffset + x
                val c = pixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val lum = 0.299f * r + 0.587f * g + 0.114f * b

                when (mode) {
                    FilterMode.SHARP_BW -> {
                        val norm = (lum * 255f / bgEstimate) + brightnessOffset
                        val centered = (norm - 166f) * (3.15f * contrastBoost) + 166f
                        val clamped = centered.coerceIn(0f, 255f)
                        val clean = when {
                            clamped > 208f -> 255f
                            clamped < 64f -> 0f
                            else -> clamped * 0.65f
                        }
                        outLum[idx] = clean
                    }
                    FilterMode.CLEAN_GRAY -> {
                        val norm = (lum * 252f / bgEstimate) + brightnessOffset
                        val centered = (norm - 142f) * (1.62f * contrastBoost) + 148f
                        val clean = if (centered > 230f) 255f else centered.coerceIn(0f, 255f)
                        outLum[idx] = clean
                    }
                    FilterMode.COLOR_BOOST -> {
                        val gain = (248f / bgEstimate).coerceIn(1.0f, 2.25f)
                        var nr = r * gain + brightnessOffset
                        var ng = g * gain + brightnessOffset
                        var nb = b * gain + brightnessOffset

                        val avg = (nr + ng + nb) / 3f
                        val sat = 1.28f
                        nr = avg + (nr - avg) * sat
                        ng = avg + (ng - avg) * sat
                        nb = avg + (nb - avg) * sat

                        nr = (nr - 128f) * (1.20f * contrastBoost) + 128f
                        ng = (ng - 128f) * (1.20f * contrastBoost) + 128f
                        nb = (nb - 128f) * (1.20f * contrastBoost) + 128f

                        val ir = nr.roundToInt().coerceIn(0, 255)
                        val ig = ng.roundToInt().coerceIn(0, 255)
                        val ib = nb.roundToInt().coerceIn(0, 255)
                        outPixels[idx] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
                    }
                    FilterMode.GRAYSCALE, FilterMode.ORIGINAL -> Unit
                }
            }
        }

        if (mode == FilterMode.SHARP_BW || mode == FilterMode.CLEAN_GRAY) {
            val amount = if (mode == FilterMode.SHARP_BW) 0.78f else 0.50f
            for (y in 0 until h) {
                val row = y * w
                for (x in 0 until w) {
                    val idx = row + x
                    val center = outLum[idx]
                    val sharp = if (x in 1 until w - 1 && y in 1 until h - 1) {
                        val neighbors = (
                            outLum[idx - 1] + outLum[idx + 1] +
                                outLum[idx - w] + outLum[idx + w]
                            ) * 0.25f
                        val v = center + (center - neighbors) * amount
                        if (mode == FilterMode.SHARP_BW && v > 220f) 255 else v.roundToInt().coerceIn(0, 255)
                    } else {
                        center.roundToInt().coerceIn(0, 255)
                    }
                    outPixels[idx] = (0xFF shl 24) or (sharp shl 16) or (sharp shl 8) or sharp
                }
            }
        }

        return Bitmap.createBitmap(outPixels, w, h, Bitmap.Config.ARGB_8888)
    }

    fun stampSignatureOnBitmap(
        source: Bitmap,
        strokes: List<List<NormalizedPoint>>,
        placement: SignaturePlacement,
        inkColorInt: Int
    ): Bitmap {
        val copy = source.copy(Bitmap.Config.ARGB_8888, true)
        if (strokes.isEmpty()) return copy
        val canvas = Canvas(copy)

        val boxW = copy.width * 0.34f
        val boxH = copy.height * 0.14f
        val marginX = copy.width * 0.06f
        val marginY = copy.height * 0.06f
        val left = when (placement) {
            SignaturePlacement.BOTTOM_LEFT -> marginX
            SignaturePlacement.BOTTOM_CENTER -> (copy.width - boxW) / 2f
            SignaturePlacement.BOTTOM_RIGHT -> copy.width - boxW - marginX
        }
        val top = copy.height - boxH - marginY

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkColorInt
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = (min(copy.width, copy.height) * 0.0055f).coerceIn(3.5f, 14f)
        }

        for (stroke in strokes) {
            if (stroke.size < 2) continue
            val path = Path()
            path.moveTo(left + stroke[0].x * boxW, top + stroke[0].y * boxH)
            for (i in 1 until stroke.size) {
                path.lineTo(left + stroke[i].x * boxW, top + stroke[i].y * boxH)
            }
            canvas.drawPath(path, paint)
        }
        return copy
    }

    fun composeTwoUpPage(topBmp: Bitmap, bottomBmp: Bitmap?, targetLongEdge: Int): Bitmap {
        val outW = (targetLongEdge * 0.707f).roundToInt().coerceAtLeast(800)
        val outH = targetLongEdge.coerceAtLeast(1130)
        val page = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page)
        canvas.drawColor(Color.WHITE)

        val pad = outW * 0.06f
        val slotW = outW - pad * 2f
        val slotH = (outH - pad * 3f) / 2f

        fun drawFit(bmp: Bitmap, slotTop: Float) {
            val scale = min(slotW / bmp.width, slotH / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dx = (outW - dw) / 2f
            val dy = slotTop + (slotH - dh) / 2f
            val m = Matrix().apply {
                postScale(scale, scale)
                postTranslate(dx, dy)
            }
            canvas.drawBitmap(bmp, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

        drawFit(topBmp, pad)
        if (bottomBmp != null) {
            drawFit(bottomBmp, pad * 2f + slotH)
        }
        return page
    }

    /**
     * Hybrid smart PDF stream encoder:
     * - For high-resolution SHARP_BW scans with pure monochrome ink, emits lossless 1-bit FlateDecode
     *   (/DeviceGray /BitsPerComponent 1 /Filter /FlateDecode) for zero pixelation at extreme zoom
     *   and tiny file size.
     * - For photos, shaded grayscale, or colored ink/watermarks, emits crisp adaptive DCTDecode JPEG.
     */
    fun encodePageForPdf(
        source: Bitmap,
        filterMode: FilterMode,
        qualityPreset: PdfQualityPreset,
        watermarkText: String = "",
        pageNumberLabel: String = ""
    ): EncodedPdfStream {
        val longEdge = max(source.width, source.height)
        val scaled = if (longEdge > qualityPreset.maxLongEdgePx) {
            val factor = qualityPreset.maxLongEdgePx.toFloat() / longEdge
            val tw = (source.width * factor).roundToInt().coerceAtLeast(1)
            val th = (source.height * factor).roundToInt().coerceAtLeast(1)
            val resized = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            val m = Matrix().apply { setScale(tw.toFloat() / source.width, th.toFloat() / source.height) }
            Canvas(resized).drawBitmap(source, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            resized
        } else {
            source
        }

        val needsOverlay = watermarkText.isNotBlank() || pageNumberLabel.isNotBlank()
        val finalBitmap = if (needsOverlay) {
            val copy = scaled.copy(Bitmap.Config.ARGB_8888, true)
            if (watermarkText.isNotBlank()) drawWatermarkOnCanvas(copy, watermarkText.trim())
            if (pageNumberLabel.isNotBlank()) drawPageNumberOnCanvas(copy, pageNumberLabel)
            copy
        } else {
            scaled
        }

        val w = finalBitmap.width
        val h = finalBitmap.height

        // Use lossless 1-bit FlateDecode when SHARP_BW, high enough DPI (>= 2480), no colored watermark,
        // and no colored signature stamped on page.
        val useMonochromeFlate = filterMode == FilterMode.SHARP_BW &&
            watermarkText.isBlank() &&
            qualityPreset.maxLongEdgePx >= 2480 &&
            isMonochromeBitmap(finalBitmap)

        if (useMonochromeFlate) {
            val flateBytes = encode1BitFlate(finalBitmap)
            if (finalBitmap !== scaled && finalBitmap !== source) finalBitmap.recycle()
            if (scaled !== source) scaled.recycle()
            return EncodedPdfStream(
                streamBytes = flateBytes,
                widthPx = w,
                heightPx = h,
                colorSpace = "/DeviceGray",
                bitsPerComponent = 1,
                filterName = "/FlateDecode"
            )
        }

        val jpegQuality = if (filterMode == FilterMode.SHARP_BW || filterMode == FilterMode.GRAYSCALE) {
            qualityPreset.bwJpegQuality
        } else {
            qualityPreset.jpegQuality
        }

        val baos = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)

        if (finalBitmap !== scaled && finalBitmap !== source) finalBitmap.recycle()
        if (scaled !== source) scaled.recycle()

        return EncodedPdfStream(
            streamBytes = baos.toByteArray(),
            widthPx = w,
            heightPx = h,
            colorSpace = "/DeviceRGB",
            bitsPerComponent = 8,
            filterName = "/DCTDecode"
        )
    }

    fun encodeJpegForPdf(
        source: Bitmap,
        filterMode: FilterMode,
        qualityPreset: PdfQualityPreset,
        watermarkText: String = "",
        pageNumberLabel: String = ""
    ): ByteArray {
        val longEdge = max(source.width, source.height)
        val scaled = if (longEdge > qualityPreset.maxLongEdgePx) {
            val factor = qualityPreset.maxLongEdgePx.toFloat() / longEdge
            val tw = (source.width * factor).roundToInt().coerceAtLeast(1)
            val th = (source.height * factor).roundToInt().coerceAtLeast(1)
            val resized = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
            val m = Matrix().apply { setScale(tw.toFloat() / source.width, th.toFloat() / source.height) }
            Canvas(resized).drawBitmap(source, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            resized
        } else {
            source
        }

        val needsOverlay = watermarkText.isNotBlank() || pageNumberLabel.isNotBlank()
        val finalBitmap = if (needsOverlay) {
            val copy = scaled.copy(Bitmap.Config.ARGB_8888, true)
            if (watermarkText.isNotBlank()) drawWatermarkOnCanvas(copy, watermarkText.trim())
            if (pageNumberLabel.isNotBlank()) drawPageNumberOnCanvas(copy, pageNumberLabel)
            copy
        } else {
            scaled
        }

        val jpegQuality = if (filterMode == FilterMode.SHARP_BW || filterMode == FilterMode.GRAYSCALE) {
            qualityPreset.bwJpegQuality
        } else {
            qualityPreset.jpegQuality
        }

        val baos = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)

        if (finalBitmap !== scaled && finalBitmap !== source) finalBitmap.recycle()
        if (scaled !== source) scaled.recycle()

        return baos.toByteArray()
    }

    private fun isMonochromeBitmap(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val step = max(1, pixels.size / 12000)
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (abs(r - g) > 14 || abs(g - b) > 14 || abs(r - b) > 14) {
                return false
            }
            i += step
        }
        return true
    }

    private fun encode1BitFlate(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val rowBytes = (w + 7) / 8
        val packed = ByteArray(rowBytes * h)
        val rowPixels = IntArray(w)

        for (y in 0 until h) {
            bitmap.getPixels(rowPixels, 0, w, 0, y, w, 1)
            val rowOutOffset = y * rowBytes
            var byteAcc = 0
            var bitCount = 0
            var byteIdx = 0
            for (x in 0 until w) {
                val c = rowPixels[x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val lum = (r * 3 + g * 6 + b) / 10
                val bit = if (lum >= 172) 1 else 0
                byteAcc = (byteAcc shl 1) or bit
                bitCount++
                if (bitCount == 8) {
                    packed[rowOutOffset + byteIdx] = byteAcc.toByte()
                    byteIdx++
                    byteAcc = 0
                    bitCount = 0
                }
            }
            if (bitCount > 0) {
                byteAcc = byteAcc shl (8 - bitCount)
                packed[rowOutOffset + byteIdx] = byteAcc.toByte()
            }
        }

        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(packed)
        deflater.finish()
        val out = ByteArrayOutputStream(packed.size / 6 + 64)
        val buf = ByteArray(8192)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            if (count > 0) out.write(buf, 0, count)
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun drawWatermarkOnCanvas(target: Bitmap, text: String) {
        val canvas = Canvas(target)
        val textSize = (min(target.width, target.height) * 0.055f).coerceIn(22f, 120f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(46, 20, 184, 166)
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.save()
        canvas.translate(target.width / 2f, target.height / 2f)
        canvas.rotate(-32f)
        canvas.drawText(text, 0f, textSize * 0.35f, paint)
        canvas.restore()
    }

    private fun drawPageNumberOnCanvas(target: Bitmap, label: String) {
        val canvas = Canvas(target)
        val textSize = (min(target.width, target.height) * 0.022f).coerceIn(18f, 44f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 70, 80, 95)
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(label, target.width / 2f, target.height - textSize * 1.1f, paint)
    }
}
