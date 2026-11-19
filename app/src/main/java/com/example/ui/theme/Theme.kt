package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NaimDarkColorScheme = darkColorScheme(
    primary = TealAccent,
    onPrimary = TealOnPrimary,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealOnContainer,
    secondary = CyanSecondary,
    onSecondary = ObsidianBg,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanOnContainer,
    tertiary = AmberTertiary,
    onTertiary = ObsidianBg,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberOnContainer,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceMid,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurfaceLow,
    surfaceContainer = ObsidianSurfaceMid,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorderSubtle,
    error = ErrorRed,
    onError = ObsidianBg,
    errorContainer = ErrorContainer,
    onErrorContainer = ErrorRed
)

val NaimShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NaimScannerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NaimDarkColorScheme,
        typography = Typography,
        shapes = NaimShapes,
        content = content
    )
}
