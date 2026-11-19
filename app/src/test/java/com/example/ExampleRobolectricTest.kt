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
import com.example.model.FilterMode
import com.example.model.PdfPageSize
import com.example.model.PdfQualityPreset
import com.example.model.QuadCorners
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

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
    fun `image processor detects corners warps and enhances document`() {
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

        val enhancedBw = ImageProcessor.applyEnhancement(warped, FilterMode.SHARP_BW)
        assertEquals(warped.width, enhancedBw.width)
        assertEquals(warped.height, enhancedBw.height)

        val jpeg = ImageProcessor.encodeJpegForPdf(
            enhancedBw,
            FilterMode.SHARP_BW,
            PdfQualityPreset.SMALL,
            watermarkText = "COPY"
        )
        assertTrue(jpeg.isNotEmpty())
    }

    @Test
    fun `pdf engine writes valid standard and encrypted PDFs`() {
        val bmp = Bitmap.createBitmap(200, 280, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.WHITE)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, baos)
        val jpeg = baos.toByteArray()

        val plainBytes = PdfEngine.generatePdfBytes(
            pages = listOf(PdfImageInput(jpeg, 200, 280)),
            pageSize = PdfPageSize.A4,
            password = ""
        )
        val plainAscii = String(plainBytes, Charsets.ISO_8859_1)
        assertTrue(plainAscii.startsWith("%PDF-1.4"))
        assertTrue(plainAscii.contains("/DCTDecode"))
        assertTrue(plainAscii.trimEnd().endsWith("%%EOF"))

        val encryptedBytes = PdfEngine.generatePdfBytes(
            pages = listOf(PdfImageInput(jpeg, 200, 280)),
            pageSize = PdfPageSize.LETTER,
            password = "secret"
        )
        val encAscii = String(encryptedBytes, Charsets.ISO_8859_1)
        assertTrue(encAscii.contains("/Encrypt"))
        assertTrue(encAscii.contains("/V 2 /R 3 /Length 128"))
    }

    @Test
    fun `repository creates renames duplicates and exports PDF offline`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ScannerRepository(context)
        repo.refreshAll()

        val sampleFile = File(context.cacheDir, "unit_sample.jpg")
        val bmp = Bitmap.createBitmap(160, 220, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(Color.WHITE)
        ImageProcessor.saveBitmapJpeg(bmp, sampleFile, 90)
        bmp.recycle()

        val docId = repo.createDocumentFromImageFiles(
            imageFiles = listOf(sampleFile),
            title = "Invoice 101",
            autoDetectCorners = false,
            defaultFilter = FilterMode.CLEAN_GRAY
        )
        assertTrue(docId > 0)
        assertEquals(1, repo.documents.value.first { it.id == docId }.pages.size)

        val pdf = repo.exportDocumentToPdf(
            documentId = docId,
            pdfTitle = "Invoice 101",
            pageSize = PdfPageSize.A4,
            qualityPreset = PdfQualityPreset.SMALL,
            watermarkText = "",
            password = ""
        )
        assertTrue(pdf != null && pdf.sizeBytes > 0)
        assertTrue(File(pdf!!.filePath).exists())

        val copyId = repo.duplicateDocument(docId)
        assertTrue(copyId > 0 && copyId != docId)
        assertEquals(QuadCorners.FULL, repo.documents.value.first { it.id == docId }.pages.first().corners)
    }
}
