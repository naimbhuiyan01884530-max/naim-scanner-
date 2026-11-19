package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import com.example.model.FilterMode
import com.example.model.NormalizedPoint
import com.example.model.PdfQualityPreset
import com.example.model.QuadCorners
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessor {

    fun decodeUriNormalized(context: Context, uri: Uri, maxLongEdge: Int = 2200): Bitmap? {
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

    fun decodeFileNormalized(path: String, maxLongEdge: Int = 2200): Bitmap? {
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

    fun saveBitmapJpeg(bitmap: Bitmap, outFile: File, quality: Int = 92) {
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
    }

    /**
     * Fast Sobel gradient + luminance contrast quad detector.
     * Finds the outer document rectangle/trapezoid automatically on a compact proxy bitmap.
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

        // Compute edge magnitude map using Sobel operator
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

        if (maxEdge < 30f) return QuadCorners()

        // Find strong border pixels avoiding extreme 2% outer margin noise
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

        if (strongCount < 20) return QuadCorners()

        val areaWidth = max(trBestX - tlBestX, brBestX - blBestX).toFloat() / w
        val areaHeight = max(blBestY - tlBestY, brBestY - trBestY).toFloat() / h

        // Sanity check: detected document should occupy a reasonable area
        if (areaWidth < 0.25f || areaHeight < 0.25f) {
            return QuadCorners()
        }

        return QuadCorners(
            topLeft = NormalizedPoint((tlBestX.toFloat() / w).coerceIn(0.01f, 0.45f), (tlBestY.toFloat() / h).coerceIn(0.01f, 0.45f)),
            topRight = NormalizedPoint((trBestX.toFloat() / w).coerceIn(0.55f, 0.99f), (trBestY.toFloat() / h).coerceIn(0.01f, 0.45f)),
            bottomRight = NormalizedPoint((brBestX.toFloat() / w).coerceIn(0.55f, 0.99f), (brBestY.toFloat() / h).coerceIn(0.55f, 0.99f)),
            bottomLeft = NormalizedPoint((blBestX.toFloat() / w).coerceIn(0.01f, 0.45f), (blBestY.toFloat() / h).coerceIn(0.55f, 0.99f))
        )
    }

    /**
     * Performs 4-corner perspective dewarp + orientation transform.
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

        val dstW = ((topW + botW) / 2.0).roundToInt().coerceIn(64, 2600)
        val dstH = ((leftH + rightH) / 2.0).roundToInt().coerceIn(64, 3400)

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
     * Applies high-clarity document enhancement with local illumination shadow removal & edge sharpening.
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

        if (mode == FilterMode.ORIGINAL) {
            val out = IntArray(pixels.size)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (((((c shr 16) and 0xFF) - 128f) * contrastBoost + 128f) + brightnessOffset).roundToInt().coerceIn(0, 255)
                val g = (((((c shr 8) and 0xFF) - 128f) * contrastBoost + 128f) + brightnessOffset).roundToInt().coerceIn(0, 255)
                val b = ((((c and 0xFF) - 128f) * contrastBoost + 128f) + brightnessOffset).roundToInt().coerceIn(0, 255)
                out[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        }

        // Downsampled illumination background map (removes shadows and uneven desk lighting)
        val bgW = (w / 16).coerceIn(16, 120)
        val bgH = (h / 16).coerceIn(16, 120)
        val bgBmp = Bitmap.createScaledBitmap(source, bgW, bgH, true)
        val bgRaw = IntArray(bgW * bgH)
        bgBmp.getPixels(bgRaw, 0, bgW, 0, 0, bgW, bgH)
        bgBmp.recycle()

        // Local maximum-ish illumination smoothing on downsampled map (3x3 dilation + average)
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
                // Blend local bright paper estimate and smooth average so dark ink isn't washed out
                smoothBg[y * bgW + x] = (maxNear * 0.65f + (sumNear / cnt) * 0.35f).coerceAtLeast(36f)
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
                        // Crisp document S-curve pushing background >= 188 to pure 255 and ink <= 125 deep black
                        val centered = (norm - 155f) * (2.6f * contrastBoost) + 155f
                        val clamped = centered.coerceIn(0f, 255f)
                        val clean = if (clamped > 218f) 255f else if (clamped < 48f) clamped * 0.45f else clamped
                        outLum[idx] = clean
                    }
                    FilterMode.CLEAN_GRAY -> {
                        val norm = (lum * 248f / bgEstimate) + brightnessOffset
                        val centered = (norm - 140f) * (1.45f * contrastBoost) + 145f
                        val clean = if (centered > 235f) 255f else centered.coerceIn(0f, 255f)
                        outLum[idx] = clean
                    }
                    FilterMode.COLOR_BOOST -> {
                        val gain = (244f / bgEstimate).coerceIn(1.0f, 2.15f)
                        var nr = r * gain + brightnessOffset
                        var ng = g * gain + brightnessOffset
                        var nb = b * gain + brightnessOffset

                        // Vibrance / contrast around midpoint
                        val avg = (nr + ng + nb) / 3f
                        val sat = 1.24f
                        nr = avg + (nr - avg) * sat
                        ng = avg + (ng - avg) * sat
                        nb = avg + (nb - avg) * sat

                        nr = (nr - 128f) * (1.15f * contrastBoost) + 128f
                        ng = (ng - 128f) * (1.15f * contrastBoost) + 128f
                        nb = (nb - 128f) * (1.15f * contrastBoost) + 128f

                        val ir = nr.roundToInt().coerceIn(0, 255)
                        val ig = ng.roundToInt().coerceIn(0, 255)
                        val ib = nb.roundToInt().coerceIn(0, 255)
                        outPixels[idx] = (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
                    }
                    FilterMode.ORIGINAL -> Unit
                }
            }
        }

        if (mode == FilterMode.SHARP_BW || mode == FilterMode.CLEAN_GRAY) {
            // Fast 3x3 unsharp mask to keep small text crisp when zoomed in
            val amount = if (mode == FilterMode.SHARP_BW) 0.55f else 0.35f
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
                        if (mode == FilterMode.SHARP_BW && v > 230f) 255 else v.roundToInt().coerceIn(0, 255)
                    } else {
                        center.roundToInt().coerceIn(0, 255)
                    }
                    outPixels[idx] = (0xFF shl 24) or (sharp shl 16) or (sharp shl 8) or sharp
                }
            }
        }

        return Bitmap.createBitmap(outPixels, w, h, Bitmap.Config.ARGB_8888)
    }

    fun encodeJpegForPdf(
        source: Bitmap,
        filterMode: FilterMode,
        qualityPreset: PdfQualityPreset,
        watermarkText: String = ""
    ): ByteArray {
        val longEdge = max(source.width, source.height)
        val scaled = if (longEdge > qualityPreset.maxLongEdgePx) {
            val factor = qualityPreset.maxLongEdgePx.toFloat() / longEdge
            val tw = (source.width * factor).roundToInt().coerceAtLeast(1)
            val th = (source.height * factor).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, tw, th, true)
        } else {
            source
        }

        val finalBitmap = if (watermarkText.isNotBlank()) {
            drawWatermarkOnBitmap(scaled, watermarkText.trim())
        } else {
            scaled
        }

        val jpegQuality = if (filterMode == FilterMode.SHARP_BW) {
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

    private fun drawWatermarkOnBitmap(source: Bitmap, text: String): Bitmap {
        val copy = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(copy)
        val textSize = (min(copy.width, copy.height) * 0.055f).coerceIn(22f, 96f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(46, 20, 184, 166)
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.save()
        canvas.translate(copy.width / 2f, copy.height / 2f)
        canvas.rotate(-32f)
        canvas.drawText(text, 0f, textSize * 0.35f, paint)
        canvas.restore()
        return copy
    }
}
