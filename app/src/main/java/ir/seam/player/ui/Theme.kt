package ir.seam.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val SeamDark = darkColorScheme(
    primary = Color(0xFFB66CFF),
    onPrimary = Color(0xFF210033),
    primaryContainer = Color(0xFF5A2182),
    onPrimaryContainer = Color(0xFFF4DEFF),
    secondary = Color(0xFF63F29A),
    onSecondary = Color(0xFF00210D),
    secondaryContainer = Color(0xFF07552C),
    onSecondaryContainer = Color(0xFF91FFB1),
    tertiary = Color(0xFFB8FFCA),
    background = Color(0xFF08070D),
    surface = Color(0xFF0E0C14),
    surfaceVariant = Color(0xFF191521),
    onBackground = Color(0xFFF9F3FC),
    onSurface = Color(0xFFF9F3FC),
    onSurfaceVariant = Color(0xFFC8BECE)
)

// Android's platform sans-serif uses the system's Persian/Arabic glyph fallback,
// keeping the UI fully offline and compatible across One UI and stock Android.
private val SeamTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        bodyMedium = bodyMedium.copy(fontFamily = FontFamily.SansSerif),
        bodySmall = bodySmall.copy(fontFamily = FontFamily.SansSerif),
        labelLarge = labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontFamily = FontFamily.SansSerif)
    )
}

@Composable
fun SeamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SeamDark, typography = SeamTypography, content = content)
}
