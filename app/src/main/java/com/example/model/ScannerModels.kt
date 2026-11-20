package com.example.model

enum class FilterMode(val label: String) {
    SHARP_BW("Sharp B&W"),
    CLEAN_GRAY("Doc Clean"),
    GRAYSCALE("Grayscale"),
    COLOR_BOOST("Color Boost"),
    ORIGINAL("Original")
}

enum class PdfQualityPreset(
    val label: String,
    val subtitle: String,
    val maxLongEdgePx: Int,
    val jpegQuality: Int,
    val bwJpegQuality: Int
) {
    SMALL("Small", "Compact", 1600, 68, 62),
    BALANCED("Balanced", "300 DPI", 2480, 84, 78),
    HIGH("High", "400 DPI", 3300, 92, 88),
    ULTRA_HD("Ultra HD", "Crisp HD", 4200, 96, 94)
}

enum class PdfPageSize(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
    FIT_IMAGE("Fit Image", 0, 0)
}

enum class PdfMargin(val label: String, val marginPt: Float) {
    NONE("None", 0f),
    NARROW("Narrow", 14f),
    NORMAL("Normal", 28f)
}

enum class PdfPageLayout(val label: String) {
    SINGLE("1 Page"),
    TWO_UP_ID("2-Up / ID")
}

data class NormalizedPoint(
    val x: Float,
    val y: Float
)

data class QuadCorners(
    val topLeft: NormalizedPoint = NormalizedPoint(0.06f, 0.06f),
    val topRight: NormalizedPoint = NormalizedPoint(0.94f, 0.06f),
    val bottomRight: NormalizedPoint = NormalizedPoint(0.94f, 0.94f),
    val bottomLeft: NormalizedPoint = NormalizedPoint(0.06f, 0.94f)
) {
    fun toSerialized(): String = listOf(
        topLeft.x, topLeft.y,
        topRight.x, topRight.y,
        bottomRight.x, bottomRight.y,
        bottomLeft.x, bottomLeft.y
    ).joinToString(",")

    fun toFloatArray(width: Float, height: Float): FloatArray = floatArrayOf(
        topLeft.x * width, topLeft.y * height,
        topRight.x * width, topRight.y * height,
        bottomRight.x * width, bottomRight.y * height,
        bottomLeft.x * width, bottomLeft.y * height
    )

    companion object {
        val FULL = QuadCorners(
            NormalizedPoint(0f, 0f),
            NormalizedPoint(1f, 0f),
            NormalizedPoint(1f, 1f),
            NormalizedPoint(0f, 1f)
        )

        fun fromSerialized(raw: String?): QuadCorners {
            if (raw.isNullOrBlank()) return QuadCorners()
            val parts = raw.split(",").mapNotNull { it.toFloatOrNull() }
            if (parts.size != 8) return QuadCorners()
            return QuadCorners(
                topLeft = NormalizedPoint(parts[0].coerceIn(0f, 1f), parts[1].coerceIn(0f, 1f)),
                topRight = NormalizedPoint(parts[2].coerceIn(0f, 1f), parts[3].coerceIn(0f, 1f)),
                bottomRight = NormalizedPoint(parts[4].coerceIn(0f, 1f), parts[5].coerceIn(0f, 1f)),
                bottomLeft = NormalizedPoint(parts[6].coerceIn(0f, 1f), parts[7].coerceIn(0f, 1f))
            )
        }
    }
}

data class FolderModel(
    val id: Long,
    val name: String,
    val createdAt: Long
)

data class PageModel(
    val id: Long,
    val documentId: Long,
    val orderIndex: Int,
    val rawImagePath: String,
    val croppedImagePath: String,
    val enhancedImagePath: String,
    val thumbImagePath: String,
    val corners: QuadCorners,
    val rotationDegrees: Int,
    val flipHorizontal: Boolean,
    val filterMode: FilterMode,
    val contrastBoost: Float = 1.0f,
    val brightnessOffset: Float = 0f,
    val ocrText: String = ""
) {
    fun bestThumbPath(): String =
        if (thumbImagePath.isNotEmpty() && java.io.File(thumbImagePath).exists()) thumbImagePath
        else enhancedImagePath
}

data class ScanDocumentModel(
    val id: Long,
    val title: String,
    val folderId: Long?,
    val folderName: String?,
    val tag: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: List<PageModel>,
    val lastPdfId: Long? = null,
    val lastPdfSizeBytes: Long? = null
) {
    fun combinedOcrText(): String = pages.mapNotNull { it.ocrText.takeIf(String::isNotBlank) }.joinToString("\n")
}

data class PdfFileModel(
    val id: Long,
    val title: String,
    val filePath: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val pageSize: PdfPageSize,
    val qualityPreset: PdfQualityPreset,
    val isPasswordProtected: Boolean,
    val watermarkText: String,
    val folderName: String?,
    val documentId: Long?,
    val createdAt: Long
)

enum class SignaturePlacement(val label: String) {
    BOTTOM_RIGHT("Bottom Right"),
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_LEFT("Bottom Left")
}

data class DrawStroke(
    val points: List<NormalizedPoint>,
    val colorArgb: Int,
    val strokeWidthFraction: Float = 0.007f,
    val isHighlighter: Boolean = false
)

enum class PhotoSharpness(val label: String) {
    SHARP("Sharp"),
    SOFT("Fair"),
    BLURRY("Blurry")
}

