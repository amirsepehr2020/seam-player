package ir.seam.player.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SeamDark = darkColorScheme(
    primary = Color(0xFFFF3152),
    onPrimary = Color.White,
    secondary = Color(0xFFFF6B81),
    background = Color(0xFF080808),
    surface = Color(0xFF111111),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun SeamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SeamDark, content = content)
}
