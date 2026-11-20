package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.data.ScannerRepository
import com.example.engine.ImageProcessor
import com.example.engine.PdfEngine
import com.example.engine.PdfImageInput
import com.example.engine.ScannerCache
import com.example.model.FilterMode
import com.example.model.NormalizedPoint
import com.example.model.PdfMargin
import com.example.model.PdfPageLayout
import com.example.model.PdfPageSize
import com.example.model.PdfQualityPreset
import com.example.model.QuadCorners
import com.example.model.SignaturePlacement
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Naim Scanner`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Naim Scanner", appName)
    }

    @Test
    fun `image processor detects corners warps enhances grayscale signs and caches`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bmp = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.DKGRAY)
        val paperPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(30f, 40f, 210f, 280f, paperPaint)
        val inkPaint = Paint().apply {
            color = Color.BLACK
            textSize = 18f
        }
        canvas.drawText("NAIM SCANNER", 55f, 120f, inkPaint)

        val corners = ImageProcessor.detectDocumentCorners(bmp)
        val warped = ImageProcessor.warpAndOrient(bmp, corners, 90, false)
        assertTrue(warped.width > 50 && warped.height > 50)

        val enhancedBw = ImageProcessor.applyEnhancement(
            source = warped,
            mode = FilterMode.SHARP_BW,
            contrastBoost = 1.15f,
            brightnessOffset = 8f
        )
        assertEquals(warped.width, enhancedBw.width)
        assertEquals(warped.height, enhancedBw.height)

        val gray = ImageProcessor.applyEnhancement(warped, FilterMode.GRAYSCALE)
        assertEquals(warped.width, gray.width)

        val signed = ImageProcessor.stampSignatureOnBitmap(
            source = enhancedBw,
            strokes = listOf(listOf(NormalizedPoint(0.1f, 0.5f), NormalizedPoint(0.9f, 0.5f))),
            placement = SignaturePlacement.BOTTOM_RIGHT,
            inkColorInt = Color.BLUE
        )
        assertEquals(enhancedBw.width, signed.width)

        val thumbFile = File(context.cacheDir, "unit_thumb.jpg")
        ImageProcessor.saveThumbnail(signed, thumbFile)
        assertTrue(thumbFile.exists() && thumbFile.length() > 0)

        val cached1 = ImageProcessor.decodeFileCached(thumbFile.absolutePath, 400)
        val cached2 = ImageProcessor.decodeFileCached(thumbFile.absolutePath, 400)
        assertNotNull(cached1)
        assertTrue(cached1 === cached2)
        ScannerCache.invalidatePath(thumbFile.absolutePath)
        assertNull(ScannerCache.getBitmap(thumbFile.absolutePath, 400))

        // Monochrome FlateDecode stream check for pure SHARP_BW crisp text PDFs
        val monoStream = ImageProcessor.encodePageForPdf(
            source = enhancedBw,
            filterMode = FilterMode.SHARP_BW,
            qualityPreset = PdfQualityPreset.HIGH
        )
        assertEquals("/FlateDecode", monoStream.filterName)
        assertEquals("/DeviceGray", monoStream.colorSpace)
        assertEquals(1, monoStream.bitsPerComponent)
        assertTrue(monoStream.streamBytes.isNotEmpty())

        // Live luma buffer quad detection check
        val lw = 160
        val lh = 120
        val luma = ByteArray(lw * lh) { 40.toByte() }
        for (y in 18 until 102) {
            for (x in 24 until 136) {
                luma[y * lw + x] = 240.toByte()
            }
        }
        val liveQuad = ImageProcessor.detectDocumentCornersFromLuma(ByteBuffer.wrap(luma), lw, lw, lh, 0)
        assertNotNull(liveQuad)
    }

    @Test
    fun `pdf engine writes valid standard encrypted monochrome and two-up PDFs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bmp = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.WHITE)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val jpeg = baos.toByteArray()

        val plainBytes = PdfEngine.generatePdfBytes(
            pages = listOf(PdfImageInput(jpeg, 200, 280)),
            pageSize = PdfPageSize.A4,
            marginPt = PdfMargin.NONE.marginPt,
            password = ""
        )
        val plainAscii = String(plainBytes, Charsets.ISO_8859_1)
        assertTrue(plainAscii.startsWith("%PDF-1.4"))
        assertTrue(plainAscii.contains("/DCTDecode"))
        assertTrue(plainAscii.trimEnd().endsWith("%%EOF"))

        val encryptedBytes = PdfEngine.generatePdfBytes(
            pages = listOf(PdfImageInput(jpeg, 200, 280)),
            pageSize = PdfPageSize.LETTER,
            marginPt = PdfMargin.NORMAL.marginPt,
            password = "secret"
        )
        val encAscii = String(encryptedBytes, Charsets.ISO_8859_1)
        assertTrue(encAscii.contains("/Encrypt"))
        assertTrue(encAscii.contains("/V 2 /R 3 /Length 128"))

        val img1 = File(context.cacheDir, "two_up_1.jpg")
        val img2 = File(context.cacheDir, "two_up_2.jpg")
        val img3 = File(context.cacheDir, "two_up_3.jpg")
        ImageProcessor.saveBitmapJpeg(bmp, img1, 90)
        ImageProcessor.saveBitmapJpeg(bmp, img2, 90)
        ImageProcessor.saveBitmapJpeg(bmp, img3, 90)
        bmp.recycle()

        val outTwoUp = File(context.cacheDir, "two_up_out.pdf")
        val (bytesWritten, pageCount) = PdfEngine.buildPdfFromImages(
            imageFiles = listOf(
                img1.absolutePath to FilterMode.SHARP_BW,
                img2.absolutePath to FilterMode.CLEAN_GRAY,
                img3.absolutePath to FilterMode.COLOR_BOOST
            ),
            outFile = outTwoUp,
            pageSize = PdfPageSize.A4,
            qualityPreset = PdfQualityPreset.HIGH,
            pageLayout = PdfPageLayout.TWO_UP_ID,
            marginPt = PdfMargin.NARROW.marginPt,
            watermarkText = "CONFIDENTIAL",
            includePageNumbers = true,
            password = ""
        )
        assertEquals(2, pageCount)
        assertTrue(bytesWritten > 0 && outTwoUp.exists())
    }

    @Test
    fun `repository batch imports batch edits duplicates and exports ultra hd PDF offline`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ScannerRepository(context)
        repo.refreshAll()

        val sample1 = File(context.cacheDir, "unit_sample_1.jpg")
        val sample2 = File(context.cacheDir, "unit_sample_2.jpg")
        val bmp = Bitmap.createBitmap(180, 240, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.WHITE)
        ImageProcessor.saveBitmapJpeg(bmp, sample1, 92)
        ImageProcessor.saveBitmapJpeg(bmp, sample2, 92)
        bmp.recycle()

        val progressUpdates = mutableListOf<Pair<Int, Int>>()
        val docId = repo.createDocumentFromImageFiles(
            imageFiles = listOf(sample1, sample2),
            title = "Invoice 101",
            autoDetectCorners = false,
            defaultFilter = FilterMode.CLEAN_GRAY,
            onProgress = { done, total -> progressUpdates.add(done to total) }
        )
        assertTrue(docId > 0)
        assertEquals(2, progressUpdates.size)

        val doc = repo.documents.value.first { it.id == docId }
        assertEquals(2, doc.pages.size)
        val firstPage = doc.pages.first()
        assertTrue(firstPage.thumbImagePath.isNotEmpty() && File(firstPage.bestThumbPath()).exists())

        // Batch rotate & filter across both pages
        val allIds = doc.pages.map { it.id }.toSet()
        repo.batchApplyFilter(docId, allIds, FilterMode.SHARP_BW)
        repo.batchRotatePages(docId, allIds, 90)
        val afterBatch = repo.documents.value.first { it.id == docId }
        assertTrue(afterBatch.pages.all { it.filterMode == FilterMode.SHARP_BW && it.rotationDegrees == 90 })

        // Page duplicate
        val dupPageId = repo.duplicatePage(firstPage.id)
        assertTrue(dupPageId > 0)
        assertEquals(3, repo.documents.value.first { it.id == docId }.pages.size)

        // Batch delete duplicated page
        repo.batchDeletePages(docId, setOf(dupPageId))
        assertEquals(2, repo.documents.value.first { it.id == docId }.pages.size)

        val pdf = repo.exportDocumentToPdf(
            documentId = docId,
            pdfTitle = "Invoice 101",
            pageSize = PdfPageSize.A4,
            qualityPreset = PdfQualityPreset.ULTRA_HD,
            pageLayout = PdfPageLayout.SINGLE,
            margin = PdfMargin.NONE,
            watermarkText = "",
            includePageNumbers = true,
            password = ""
        )
        assertNotNull(pdf)
        assertTrue(pdf!!.sizeBytes > 0)
        assertEquals(2, pdf.pageCount)
        assertEquals(PdfQualityPreset.ULTRA_HD, pdf.qualityPreset)
        assertTrue(File(pdf.filePath).exists())

        val copyId = repo.duplicateDocument(docId)
        assertTrue(copyId > 0 && copyId != docId)
        assertEquals(QuadCorners.FULL, repo.documents.value.first { it.id == docId }.pages.first().corners)
    }
}
