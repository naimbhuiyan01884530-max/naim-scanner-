package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.model.FilterMode
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
    val heightPx: Int
)

object PdfEngine {

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.2f MB", mb)
    }

    /**
     * Generates a lightweight standard PDF 1.4 directly from JPEG streams (/DCTDecode),
     * optionally encrypted with standard PDF RC4-128 password protection.
     */
    fun buildPdfFromImages(
        imageFiles: List<Pair<String, FilterMode>>,
        outFile: File,
        pageSize: PdfPageSize,
        qualityPreset: PdfQualityPreset,
        watermarkText: String = "",
        password: String = ""
    ): Long {
        val inputs = mutableListOf<PdfImageInput>()
        for ((path, mode) in imageFiles) {
            val bmp = ImageProcessor.decodeFileNormalized(path, qualityPreset.maxLongEdgePx) ?: continue
            val jpegBytes = ImageProcessor.encodeJpegForPdf(bmp, mode, qualityPreset, watermarkText)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, bounds)
            bmp.recycle()
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                inputs.add(PdfImageInput(jpegBytes, bounds.outWidth, bounds.outHeight))
            }
        }
        require(inputs.isNotEmpty()) { "No valid pages to export" }
        return writePdfBytes(inputs, outFile, pageSize, password)
    }

    fun writePdfBytes(
        pages: List<PdfImageInput>,
        outFile: File,
        pageSize: PdfPageSize,
        password: String = ""
    ): Long {
        outFile.parentFile?.mkdirs()
        val rawPdfBytes = generatePdfBytes(pages, pageSize, password)
        FileOutputStream(outFile).use { fos ->
            fos.write(rawPdfBytes)
        }
        return outFile.length()
    }

    fun generatePdfBytes(
        pages: List<PdfImageInput>,
        pageSize: PdfPageSize,
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

        // Object numbering:
        // 1 = Catalog
        // 2 = Pages
        // Per page i (0..N-1):
        //   pageObj = 3 + i*3
        //   contentObj = 4 + i*3
        //   imageObj = 5 + i*3
        // Optional Encrypt dict = 3 + N*3
        val n = pages.size
        val pageKids = (0 until n).joinToString(" ") { "${3 + it * 3} 0 R" }

        // Obj 1: Catalog
        offsets.add(out.size())
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        // Obj 2: Pages
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

            // Fit image inside page with subtle clean margin for A4/Letter, full bleed for FIT_IMAGE
            val margin = if (pageSize == PdfPageSize.FIT_IMAGE) 0f else 18f
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

            // Page object
            offsets.add(out.size())
            writeAscii(
                "$pageObjId 0 obj\n" +
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pw $ph] " +
                    "/Resources << /XObject << /Im$i $imageObjId 0 R >> >> " +
                    "/Contents $contentObjId 0 R >>\nendobj\n"
            )

            // Content stream object
            offsets.add(out.size())
            writeAscii("$contentObjId 0 obj\n<< /Length ${finalContentBytes.size} >>\nstream\n")
            out.write(finalContentBytes)
            writeAscii("\nendstream\nendobj\n")

            // Image XObject (DCTDecode JPEG)
            val finalImageBytes = if (encData != null) {
                rc4EncryptObject(encData.fileKey, imageObjId, 0, img.jpegBytes)
            } else {
                img.jpegBytes
            }
            offsets.add(out.size())
            writeAscii(
                "$imageObjId 0 obj\n" +
                    "<< /Type /XObject /Subtype /Image /Width ${img.widthPx} /Height ${img.heightPx} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${finalImageBytes.size} >>\nstream\n"
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

    fun renderPdfPage(pdfPath: String, pageIndex: Int, targetWidth: Int = 1200): Bitmap? {
        val file = File(pdfPath)
        if (!file.exists()) return null
        return runCatching {
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
        val bytes = writePdfBytes(inputs, outFile, pageSize, password)
        return bytes to inputs.size
    }

    fun extractPdfPages(
        sourcePdfPath: String,
        zeroBasedIndices: List<Int>,
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
                for (index in zeroBasedIndices.sorted().distinct()) {
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
        return writePdfBytes(inputs, outFile, pageSize, password)
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
        val permissions = -3904 // Allow printing & viewing (standard Revision 3 bits)
        val ownerPadded = padPassword("NaimOwner-${userPassword}")
        val userPadded = padPassword(userPassword)

        // Algorithm 3.3: Compute /O (Revision 3)
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

        // Algorithm 3.2: Compute file encryption key
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

        // Algorithm 3.5: Compute /U (Revision 3)
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
