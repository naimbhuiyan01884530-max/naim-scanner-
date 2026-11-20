package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.model.FilterMode
import com.example.model.PdfMargin
import com.example.model.PdfPageLayout
import com.example.model.PdfPageSize
import com.example.model.PdfQualityPreset
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

data class PdfImageInput(
    val jpegBytes: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val colorSpace: String = "/DeviceRGB",
    val bitsPerComponent: Int = 8,
    val filterName: String = "/DCTDecode"
)

object PdfEngine {

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }

    fun buildPdfFromImages(
        imageFiles: List<Pair<String, FilterMode>>,
        outFile: File,
        pageSize: PdfPageSize,
        qualityPreset: PdfQualityPreset,
        pageLayout: PdfPageLayout = PdfPageLayout.SINGLE,
        marginPt: Float = PdfMargin.NARROW.marginPt,
        watermarkText: String = "",
        includePageNumbers: Boolean = false,
        password: String = ""
    ): Pair<Long, Int> {
        val inputs = mutableListOf<PdfImageInput>()

        if (pageLayout == PdfPageLayout.TWO_UP_ID) {
            val chunks = imageFiles.chunked(2)
            chunks.forEachIndexed { idx, pair ->
                val bmp1 = ImageProcessor.decodeFileNormalized(pair[0].first, qualityPreset.maxLongEdgePx)
                val bmp2 = pair.getOrNull(1)?.let {
                    ImageProcessor.decodeFileNormalized(it.first, qualityPreset.maxLongEdgePx)
                }
                if (bmp1 != null) {
                    val combined = ImageProcessor.composeTwoUpPage(bmp1, bmp2, qualityPreset.maxLongEdgePx)
                    bmp1.recycle()
                    bmp2?.recycle()
                    val pageLabel = if (includePageNumbers) "${idx + 1} / ${chunks.size}" else ""
                    val encoded = ImageProcessor.encodePageForPdf(
                        source = combined,
                        filterMode = FilterMode.ORIGINAL,
                        qualityPreset = qualityPreset,
                        watermarkText = watermarkText,
                        pageNumberLabel = pageLabel
                    )
                    combined.recycle()
                    inputs.add(
                        PdfImageInput(
                            jpegBytes = encoded.streamBytes,
                            widthPx = encoded.widthPx,
                            heightPx = encoded.heightPx,
                            colorSpace = encoded.colorSpace,
                            bitsPerComponent = encoded.bitsPerComponent,
                            filterName = encoded.filterName
                        )
                    )
                }
            }
        } else {
            val total = imageFiles.size
            imageFiles.forEachIndexed { idx, (path, mode) ->
                val bmp = ImageProcessor.decodeFileNormalized(path, qualityPreset.maxLongEdgePx) ?: return@forEachIndexed
                val pageLabel = if (includePageNumbers) "${idx + 1} / $total" else ""
                val encoded = ImageProcessor.encodePageForPdf(
                    source = bmp,
                    filterMode = mode,
                    qualityPreset = qualityPreset,
                    watermarkText = watermarkText,
                    pageNumberLabel = pageLabel
                )
                bmp.recycle()
                if (encoded.widthPx > 0 && encoded.heightPx > 0 && encoded.streamBytes.isNotEmpty()) {
                    inputs.add(
                        PdfImageInput(
                            jpegBytes = encoded.streamBytes,
                            widthPx = encoded.widthPx,
                            heightPx = encoded.heightPx,
                            colorSpace = encoded.colorSpace,
                            bitsPerComponent = encoded.bitsPerComponent,
                            filterName = encoded.filterName
                        )
                    )
                }
            }
        }

        require(inputs.isNotEmpty()) { "No valid pages to export" }
        val bytes = writePdfBytes(inputs, outFile, pageSize, marginPt, password)
        return bytes to inputs.size
    }

    fun writePdfBytes(
        pages: List<PdfImageInput>,
        outFile: File,
        pageSize: PdfPageSize,
        marginPt: Float = PdfMargin.NARROW.marginPt,
        password: String = ""
    ): Long {
        outFile.parentFile?.mkdirs()
        val rawPdfBytes = generatePdfBytes(pages, pageSize, marginPt, password)
        FileOutputStream(outFile).use { fos ->
            fos.write(rawPdfBytes)
        }
        return outFile.length()
    }

    fun generatePdfBytes(
        pages: List<PdfImageInput>,
        pageSize: PdfPageSize,
        marginPt: Float = PdfMargin.NARROW.marginPt,
        password: String = ""
    ): ByteArray {
        val useEncryption = password.isNotEmpty()
        val fileId = md5("NaimScanner-${System.nanoTime()}-${pages.size}".toByteArray())
        val encData = if (useEncryption) computePdfEncryption(password, fileId) else null

        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()

        fun writeAscii(text: String) {
            out.write(text.toByteArray(Charsets.US_ASCII))
        }

        writeAscii("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")

        val n = pages.size
        val pageKids = (0 until n).joinToString(" ") { "${3 + it * 3} 0 R" }

        offsets.add(out.size())
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets.add(out.size())
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [$pageKids] /Count $n >>\nendobj\n")

        for (i in 0 until n) {
            val img = pages[i]
            val pageObjId = 3 + i * 3
            val contentObjId = 4 + i * 3
            val imageObjId = 5 + i * 3

            val (pw, ph) = when (pageSize) {
                PdfPageSize.A4 -> {
                    if (img.widthPx > img.heightPx) PdfPageSize.A4.heightPt to PdfPageSize.A4.widthPt
                    else PdfPageSize.A4.widthPt to PdfPageSize.A4.heightPt
                }
                PdfPageSize.LETTER -> {
                    if (img.widthPx > img.heightPx) PdfPageSize.LETTER.heightPt to PdfPageSize.LETTER.widthPt
                    else PdfPageSize.LETTER.widthPt to PdfPageSize.LETTER.heightPt
                }
                PdfPageSize.FIT_IMAGE -> {
                    val scale = 595f / img.widthPx.toFloat()
                    595 to (img.heightPx * scale).roundToInt().coerceAtLeast(100)
                }
            }

            val margin = if (pageSize == PdfPageSize.FIT_IMAGE) 0f else marginPt.coerceIn(0f, 72f)
            val availW = pw - margin * 2f
            val availH = ph - margin * 2f
            val ratio = min(availW / img.widthPx.toFloat(), availH / img.heightPx.toFloat())
            val drawW = img.widthPx * ratio
            val drawH = img.heightPx * ratio
            val drawX = (pw - drawW) / 2f
            val drawY = (ph - drawH) / 2f

            val contentStreamText = String.format(
                Locale.US,
                "q\n%.2f 0 0 %.2f %.2f %.2f cm\n/Im%d Do\nQ\n",
                drawW, drawH, drawX, drawY, i
            )
            val rawContentBytes = contentStreamText.toByteArray(Charsets.US_ASCII)
            val finalContentBytes = if (encData != null) {
                rc4EncryptObject(encData.fileKey, contentObjId, 0, rawContentBytes)
            } else {
                rawContentBytes
            }

            offsets.add(out.size())
            writeAscii(
                "$pageObjId 0 obj\n" +
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pw $ph] " +
                    "/Resources << /XObject << /Im$i $imageObjId 0 R >> >> " +
                    "/Contents $contentObjId 0 R >>\nendobj\n"
            )

            offsets.add(out.size())
            writeAscii("$contentObjId 0 obj\n<< /Length ${finalContentBytes.size} >>\nstream\n")
            out.write(finalContentBytes)
            writeAscii("\nendstream\nendobj\n")

            val finalImageBytes = if (encData != null) {
                rc4EncryptObject(encData.fileKey, imageObjId, 0, img.jpegBytes)
            } else {
                img.jpegBytes
            }
            offsets.add(out.size())
            writeAscii(
                "$imageObjId 0 obj\n" +
                    "<< /Type /XObject /Subtype /Image /Width ${img.widthPx} /Height ${img.heightPx} " +
                    "/ColorSpace ${img.colorSpace} /BitsPerComponent ${img.bitsPerComponent} /Filter ${img.filterName} /Length ${finalImageBytes.size} >>\nstream\n"
            )
            out.write(finalImageBytes)
            writeAscii("\nendstream\nendobj\n")
        }

        val encryptObjId = if (encData != null) 3 + n * 3 else null
        if (encData != null && encryptObjId != null) {
            offsets.add(out.size())
            val oHex = encData.oValue.toHexString()
            val uHex = encData.uValue.toHexString()
            writeAscii(
                "$encryptObjId 0 obj\n" +
                    "<< /Filter /Standard /V 2 /R 3 /Length 128 /P ${encData.permissions} " +
                    "/O <$oHex> /U <$uHex> >>\nendobj\n"
            )
        }

        val xrefOffset = out.size()
        val totalObjs = offsets.size + 1
        writeAscii("xref\n0 $totalObjs\n0000000000 65535 f \n")
        for (offset in offsets) {
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", offset))
        }

        val idHex = fileId.toHexString()
        val encryptEntry = if (encryptObjId != null) " /Encrypt $encryptObjId 0 R" else ""
        writeAscii(
            "trailer\n<< /Size $totalObjs /Root 1 0 R$encryptEntry /ID [<$idHex><$idHex>] >>\n" +
                "startxref\n$xrefOffset\n%%EOF\n"
        )
        return out.toByteArray()
    }

    fun getPdfPageCount(pdfPath: String): Int {
        val file = File(pdfPath)
        if (!file.exists()) return 0
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        }.getOrDefault(0)
    }

    fun renderPdfPage(pdfPath: String, pageIndex: Int, targetWidth: Int = 2000): Bitmap? {
        ScannerCache.getPdfPageBitmap(pdfPath, pageIndex, targetWidth)?.let { return it }
        val file = File(pdfPath)
        if (!file.exists()) return null
        val rendered = runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val ratio = targetWidth.toFloat() / page.width.coerceAtLeast(1)
                        val h = (page.height * ratio).roundToInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(targetWidth, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrNull()
        if (rendered != null) {
            ScannerCache.putPdfPageBitmap(pdfPath, pageIndex, targetWidth, rendered)
        }
        return rendered
    }

    fun mergePdfFiles(
        sourcePdfPaths: List<String>,
        outFile: File,
        pageSize: PdfPageSize,
        qualityPreset: PdfQualityPreset,
        password: String = ""
    ): Pair<Long, Int> {
        val inputs = mutableListOf<PdfImageInput>()
        for (path in sourcePdfPaths) {
            val file = File(path)
            if (!file.exists()) continue
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val scale = qualityPreset.maxLongEdgePx.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                            val w = (page.width * scale).roundToInt().coerceAtLeast(100)
                            val h = (page.height * scale).roundToInt().coerceAtLeast(100)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val jpeg = ImageProcessor.encodeJpegForPdf(bmp, FilterMode.ORIGINAL, qualityPreset)
                            bmp.recycle()
                            inputs.add(PdfImageInput(jpeg, w, h))
                        }
                    }
                }
            }
        }
        require(inputs.isNotEmpty()) { "No readable pages found to merge" }
        val bytes = writePdfBytes(inputs, outFile, pageSize, PdfMargin.NARROW.marginPt, password)
        return bytes to inputs.size
    }

    fun extractOrReorderPdfPages(
        sourcePdfPath: String,
        zeroBasedOrder: List<Int>,
        outFile: File,
        pageSize: PdfPageSize,
        qualityPreset: PdfQualityPreset,
        password: String = ""
    ): Long {
        val file = File(sourcePdfPath)
        require(file.exists()) { "Source PDF missing" }
        val inputs = mutableListOf<PdfImageInput>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (index in zeroBasedOrder) {
                    if (index !in 0 until renderer.pageCount) continue
                    renderer.openPage(index).use { page ->
                        val scale = qualityPreset.maxLongEdgePx.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                        val w = (page.width * scale).roundToInt().coerceAtLeast(100)
                        val h = (page.height * scale).roundToInt().coerceAtLeast(100)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val jpeg = ImageProcessor.encodeJpegForPdf(bmp, FilterMode.ORIGINAL, qualityPreset)
                        bmp.recycle()
                        inputs.add(PdfImageInput(jpeg, w, h))
                    }
                }
            }
        }
        require(inputs.isNotEmpty()) { "No pages selected" }
        return writePdfBytes(inputs, outFile, pageSize, PdfMargin.NARROW.marginPt, password)
    }

    fun compressPdfFile(
        sourcePdfPath: String,
        outFile: File,
        qualityPreset: PdfQualityPreset,
        pageSize: PdfPageSize = PdfPageSize.A4
    ): Pair<Long, Int> {
        val count = getPdfPageCount(sourcePdfPath)
        require(count > 0) { "PDF cannot be read or is locked" }
        val bytes = extractOrReorderPdfPages(
            sourcePdfPath = sourcePdfPath,
            zeroBasedOrder = (0 until count).toList(),
            outFile = outFile,
            pageSize = pageSize,
            qualityPreset = qualityPreset
        )
        return bytes to count
    }

    fun protectPdfFile(
        sourcePdfPath: String,
        outFile: File,
        password: String,
        qualityPreset: PdfQualityPreset = PdfQualityPreset.HIGH,
        pageSize: PdfPageSize = PdfPageSize.A4
    ): Pair<Long, Int> {
        val count = getPdfPageCount(sourcePdfPath)
        require(count > 0) { "PDF cannot be read" }
        val bytes = extractOrReorderPdfPages(
            sourcePdfPath = sourcePdfPath,
            zeroBasedOrder = (0 until count).toList(),
            outFile = outFile,
            pageSize = pageSize,
            qualityPreset = qualityPreset,
            password = password
        )
        return bytes to count
    }

    fun renderPdfPagesToImageFiles(
        sourcePdfPath: String,
        outDir: File,
        targetLongEdge: Int = 2600
    ): List<File> {
        val file = File(sourcePdfPath)
        if (!file.exists()) return emptyList()
        outDir.mkdirs()
        val result = mutableListOf<File>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        val scale = targetLongEdge.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
                        val w = (page.width * scale).roundToInt().coerceAtLeast(200)
                        val h = (page.height * scale).roundToInt().coerceAtLeast(200)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val imgFile = File(outDir, "pdf_page_${System.currentTimeMillis()}_$i.jpg")
                        ImageProcessor.saveBitmapJpeg(bmp, imgFile, 95)
                        bmp.recycle()
                        result.add(imgFile)
                    }
                }
            }
        }
        return result
    }

    // ---- Standard PDF 1.4 RC4-128 Security Handler (Spec Algorithms 3.2 - 3.5) ----

    private val PDF_PADDING = byteArrayOf(
        0x28, 0xBF.toByte(), 0x4E, 0x5E, 0x4E, 0x75, 0x8A.toByte(), 0x41,
        0x64, 0x00, 0x4E, 0x56, 0xFF.toByte(), 0xFA.toByte(), 0x01, 0x08,
        0x2E, 0x2E, 0x00, 0xB6.toByte(), 0xD0.toByte(), 0x68, 0x3E, 0x80.toByte(),
        0x2F, 0x0C, 0xA9.toByte(), 0xFE.toByte(), 0x64, 0x53, 0x69, 0x7A
    )

    private class PdfEncryptionData(
        val fileKey: ByteArray,
        val oValue: ByteArray,
        val uValue: ByteArray,
        val permissions: Int
    )

    private fun padPassword(pwd: String): ByteArray {
        val raw = pwd.toByteArray(Charsets.ISO_8859_1)
        val out = ByteArray(32)
        val copyLen = min(raw.size, 32)
        System.arraycopy(raw, 0, out, 0, copyLen)
        if (copyLen < 32) {
            System.arraycopy(PDF_PADDING, 0, out, copyLen, 32 - copyLen)
        }
        return out
    }

    private fun computePdfEncryption(userPassword: String, fileId: ByteArray): PdfEncryptionData {
        val permissions = -3904
        val ownerPadded = padPassword("NaimOwner-${userPassword}")
        val userPadded = padPassword(userPassword)

        var ownerDigest = md5(ownerPadded)
        repeat(50) {
            ownerDigest = md5(ownerDigest.copyOf(16))
        }
        val ownerKey = ownerDigest.copyOf(16)
        var oValue = rc4(ownerKey, userPadded)
        for (i in 1..19) {
            val iterKey = ByteArray(16) { idx -> (ownerKey[idx].toInt() xor i).toByte() }
            oValue = rc4(iterKey, oValue)
        }

        val permBytes = byteArrayOf(
            (permissions and 0xFF).toByte(),
            ((permissions shr 8) and 0xFF).toByte(),
            ((permissions shr 16) and 0xFF).toByte(),
            ((permissions shr 24) and 0xFF).toByte()
        )
        val digestInput = ByteArrayOutputStream().apply {
            write(userPadded)
            write(oValue)
            write(permBytes)
            write(fileId)
        }.toByteArray()
        var fileKey = md5(digestInput)
        repeat(50) {
            fileKey = md5(fileKey.copyOf(16))
        }
        fileKey = fileKey.copyOf(16)

        val uDigestInput = ByteArrayOutputStream().apply {
            write(PDF_PADDING)
            write(fileId)
        }.toByteArray()
        var uHash = md5(uDigestInput)
        uHash = rc4(fileKey, uHash)
        for (i in 1..19) {
            val iterKey = ByteArray(16) { idx -> (fileKey[idx].toInt() xor i).toByte() }
            uHash = rc4(iterKey, uHash)
        }
        val uValue = ByteArray(32)
        System.arraycopy(uHash, 0, uValue, 0, 16)
        return PdfEncryptionData(fileKey, oValue, uValue, permissions)
    }

    private fun rc4EncryptObject(fileKey: ByteArray, objNum: Int, genNum: Int, data: ByteArray): ByteArray {
        val keyInput = ByteArray(fileKey.size + 5)
        System.arraycopy(fileKey, 0, keyInput, 0, fileKey.size)
        val idx = fileKey.size
        keyInput[idx] = (objNum and 0xFF).toByte()
        keyInput[idx + 1] = ((objNum shr 8) and 0xFF).toByte()
        keyInput[idx + 2] = ((objNum shr 16) and 0xFF).toByte()
        keyInput[idx + 3] = (genNum and 0xFF).toByte()
        keyInput[idx + 4] = ((genNum shr 8) and 0xFF).toByte()
        val objKey = md5(keyInput).copyOf(16)
        return rc4(objKey, data)
    }

    private fun md5(input: ByteArray): ByteArray =
        MessageDigest.getInstance("MD5").digest(input)

    private fun rc4(key: ByteArray, data: ByteArray): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val tmp = s[i]
            s[i] = s[j]
            s[j] = tmp
        }
        val out = ByteArray(data.size)
        var i = 0
        j = 0
        for (k in data.indices) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val tmp = s[i]
            s[i] = s[j]
            s[j] = tmp
            val t = s[(s[i] + s[j]) and 0xFF]
            out[k] = ((data[k].toInt() and 0xFF) xor t).toByte()
        }
        return out
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }
}
