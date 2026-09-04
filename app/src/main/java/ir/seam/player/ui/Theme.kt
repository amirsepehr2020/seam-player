package ir.seam.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ir.seam.player.R

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

private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_extrabold, FontWeight.ExtraBold)
)

private val SeamTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = Vazirmatn),
        bodyMedium = bodyMedium.copy(fontFamily = Vazirmatn),
        bodySmall = bodySmall.copy(fontFamily = Vazirmatn),
        labelLarge = labelLarge.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = Vazirmatn, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontFamily = Vazirmatn)
    )
}

@Composable
fun SeamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SeamDark, typography = SeamTypography, content = content)
}
