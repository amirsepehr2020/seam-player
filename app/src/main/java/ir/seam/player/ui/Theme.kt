package ir.seam.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SeamDark = darkColorScheme(
    primary = Color(0xFFB86CFF),
    onPrimary = Color(0xFF1A002B),
    primaryContainer = Color(0xFF4E1877),
    onPrimaryContainer = Color(0xFFF2DCFF),
    secondary = Color(0xFF72F59A),
    onSecondary = Color(0xFF00210C),
    secondaryContainer = Color(0xFF07532A),
    onSecondaryContainer = Color(0xFF8DFFAE),
    tertiary = Color(0xFFA5FFC0),
    background = Color(0xFF08070D),
    surface = Color(0xFF100E17),
    surfaceVariant = Color(0xFF1B1725),
    onBackground = Color(0xFFF8F2FB),
    onSurface = Color(0xFFF8F2FB),
    onSurfaceVariant = Color(0xFFC9BFCE)
)

@Composable
fun SeamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SeamDark, content = content)
}
