package ir.seam.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SeamDark = darkColorScheme(
    primary = Color(0xFFC47CFF),
    onPrimary = Color(0xFF210033),
    primaryContainer = Color(0xFF5A2182),
    onPrimaryContainer = Color(0xFFF4DEFF),
    secondary = Color(0xFF75F6A0),
    onSecondary = Color(0xFF00210D),
    secondaryContainer = Color(0xFF07552C),
    onSecondaryContainer = Color(0xFF91FFB1),
    tertiary = Color(0xFFB7FFC9),
    background = Color(0xFF07060B),
    surface = Color(0xFF0E0C14),
    surfaceVariant = Color(0xFF191521),
    onBackground = Color(0xFFF9F3FC),
    onSurface = Color(0xFFF9F3FC),
    onSurfaceVariant = Color(0xFFC8BECE)
)

@Composable
fun SeamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SeamDark, content = content)
}
