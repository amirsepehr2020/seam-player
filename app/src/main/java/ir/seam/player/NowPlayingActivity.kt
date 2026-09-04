package ir.seam.player

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import ir.seam.player.ui.SeamTheme
import kotlinx.coroutines.delay

class NowPlayingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { SeamTheme { NowPlaying(this) } } }
}

@Composable
private fun NowPlaying(context: android.content.Context) {
    val purple = Color(0xFFB66CFF); val green = Color(0xFF63F29A)
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(false) }; var title by remember { mutableStateOf("SEAM Player") }; var artist by remember { mutableStateOf("انتخابی برای پخش وجود ندارد") }
    var position by remember { mutableLongStateOf(0L) }; var duration by remember { mutableLongStateOf(0L) }
    var shuffle by remember { mutableStateOf(false) }; var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var dragging by remember { mutableStateOf(false) }; var dragPosition by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        runCatching {
            val token = SessionToken(context, ComponentName(context, "ir.seam.player.playback.PlaybackService"))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
        }
    }
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        fun updateMeta(m: MediaMetadata?) { title = m?.title?.toString() ?: "SEAM Player"; artist = m?.artist?.toString() ?: "هنرمند ناشناس" }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(v: Boolean) { playing = v }
            override fun onMediaMetadataChanged(m: MediaMetadata) { updateMeta(m) }
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) { updateMeta(item?.mediaMetadata); position = 0L; duration = c.duration.takeIf { it > 0 } ?: 0 }
            override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffle = enabled }
            override fun onRepeatModeChanged(mode: Int) { repeat = mode }
            override fun onPlaybackStateChanged(state: Int) { duration = c.duration.takeIf { it > 0 } ?: 0 }
        }
        c.addListener(listener); updateMeta(c.currentMediaItem?.mediaMetadata); playing = c.isPlaying; shuffle = c.shuffleModeEnabled; repeat = c.repeatMode; duration = c.duration.takeIf { it > 0 } ?: 0
        onDispose { c.removeListener(listener) }
    }
    LaunchedEffect(controller, playing, dragging) {
        while (true) { val c = controller ?: break; if (!dragging) { position = c.currentPosition.coerceAtLeast(0); duration = c.duration.takeIf { it > 0 } ?: 0 }; if (!playing) break; delay(250) }
    }
    val shownPosition = if (dragging) dragPosition else position
    val progress = if (duration > 0) (shownPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val enabled = controller != null

    Scaffold(containerColor = Color(0xFF08070D), topBar = {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { (context as? ComponentActivity)?.finish() }) { Icon(Icons.Rounded.KeyboardArrowDown, "بستن", tint = Color.White) }
            Text("در حال پخش", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            IconButton(onClick = { }) { Icon(Icons.Rounded.MoreVert, "بیشتر", tint = Color.White) }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp).shadow(32.dp, RoundedCornerShape(38.dp), ambientColor = purple, spotColor = purple).clip(RoundedCornerShape(38.dp)).background(Brush.linearGradient(listOf(Color(0xFF3A1558), Color(0xFF0B3620), Color(0xFF14111C)))), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(100.dp)) }
            Column(Modifier.fillMaxWidth()) {
                Text(title, color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Text(artist, color = Color(0xFFAAA1B0), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(22.dp))
                Slider(value = progress, onValueChange = { if (duration > 0) { dragging = true; dragPosition = (it * duration).toLong() } }, onValueChangeFinished = { if (duration > 0) controller?.seekTo(dragPosition.coerceIn(0, duration)); dragging = false }, enabled = enabled && duration > 0, modifier = Modifier.fillMaxWidth(), colors = SliderDefaults.colors(thumbColor = green, activeTrackColor = green))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatMs(shownPosition), color = Color(0xFF8E8794)); Text(formatMs(duration), color = Color(0xFF8E8794)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = { controller?.seekToPreviousMediaItem() }) { Icon(Icons.Rounded.SkipPrevious, "قبلی", tint = Color.White, modifier = Modifier.size(34.dp)) }
                Box(Modifier.size(76.dp).shadow(18.dp, CircleShape, ambientColor = green, spotColor = green).clip(CircleShape).background(if (enabled) green else Color(0xFF4A4A4A)).clickable(enabled = enabled) { if (playing) controller?.pause() else controller?.play() }, contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color(0xFF06200F), modifier = Modifier.size(38.dp)) }
                IconButton(enabled = enabled, onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Rounded.SkipNext, "بعدی", tint = Color.White, modifier = Modifier.size(34.dp)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(enabled = enabled, onClick = { controller?.seekBack() }) { Icon(Icons.Rounded.Replay10, "۱۰ ثانیه عقب", tint = purple) }
                IconButton(enabled = enabled, onClick = { controller?.shuffleModeEnabled = !controller!!.shuffleModeEnabled }) { Icon(Icons.Rounded.Shuffle, "تصادفی", tint = if (shuffle) green else Color.White) }
                IconButton(enabled = enabled, onClick = { val next = when (repeat) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }; controller?.repeatMode = next }) { Icon(if (repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "تکرار", tint = if (repeat != Player.REPEAT_MODE_OFF) green else Color.White) }
                IconButton(enabled = enabled, onClick = { controller?.seekForward() }) { Icon(Icons.Rounded.Forward10, "۱۰ ثانیه جلو", tint = purple) }
            }
        }
    }
}

private fun formatMs(ms: Long): String { val total = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(total / 60, total % 60) }
