package ir.seam.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import ir.seam.player.ui.SeamTheme
import kotlinx.coroutines.delay

data class HomeTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: String
)

enum class HomeTab { HOME, LIBRARY, SEARCH, SETTINGS }

class SEAMHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SeamTheme { SEAMHome(this) } }
    }
}

@Composable
private fun SEAMHome(context: Context) {
    val purple = Color(0xFFB66CFF)
    val green = Color(0xFF63F29A)
    val bg = Color(0xFF08070D)
    val card = Color(0xFF121019)

    var tracks by remember { mutableStateOf(emptyList<HomeTrack>()) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var current by remember { mutableStateOf<HomeTrack?>(null) }
    var playing by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(HomeTab.HOME) }
    var query by remember { mutableStateOf("") }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) tracks = loadHomeTracks(context)
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            tracks = loadHomeTracks(context)
        } else permissionLauncher.launch(permission)

        val token = SessionToken(context, ComponentName(context, "ir.seam.player.playback.PlaybackService"))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                current = tracks.firstOrNull { it.uri == mediaItem?.localConfiguration?.uri.toString() }
            }
        }
        c.addListener(listener)
        onDispose { c.removeListener(listener); c.release() }
    }

    LaunchedEffect(controller, playing) {
        while (playing) {
            position = controller?.currentPosition ?: 0L
            duration = (controller?.duration ?: 0L).coerceAtLeast(0L)
            delay(300)
        }
    }

    fun play(track: HomeTrack) {
        val c = controller ?: return
        val items = tracks.map {
            MediaItem.Builder()
                .setUri(it.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).setArtist(it.artist).setAlbumTitle(it.album).build())
                .build()
        }
        c.setMediaItems(items)
        c.seekToDefaultPosition(tracks.indexOf(track).coerceAtLeast(0))
        c.prepare()
        c.play()
        current = track
        playing = true
    }

    val visibleTracks = tracks.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }

    Scaffold(
        containerColor = bg,
        bottomBar = {
            Column {
                current?.let { MiniNowPlaying(it, playing, position, duration, purple, green, { if (playing) controller?.pause() else controller?.play() }, { play(it) }) }
                BottomNav(tab, purple, green) { tab = it }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).safeDrawingPadding()) {
            when (tab) {
                HomeTab.HOME -> HomeScreen(tracks, current, playing, purple, green, card, ::play) { tab = HomeTab.SEARCH }
                HomeTab.LIBRARY -> LibraryScreen(visibleTracks, current, playing, purple, green, card, ::play)
                HomeTab.SEARCH -> SearchScreen(query, visibleTracks, current, playing, purple, green, card, { query = it }, ::play)
                HomeTab.SETTINGS -> SettingsScreen(purple, green, tracks.size)
            }
        }
    }
}

@Composable
private fun HomeScreen(tracks: List<HomeTrack>, current: HomeTrack?, playing: Boolean, purple: Color, green: Color, card: Color, play: (HomeTrack) -> Unit, openSearch: () -> Unit) {
    val recent = tracks.take(8)
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SEAM", color = purple, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                    Text("خانه", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineLarge)
                    Text("همه‌چیز برای پخش سریع موسیقی", color = Color(0xFF9E96A8))
                }
                IconButton(onClick = openSearch) { Icon(Icons.Rounded.Search, "جستجو", tint = Color.White) }
            }
        }
        item {
            HeroCard(current, playing, tracks.size, purple, green, play)
        }
        item {
            Text("دسترسی سریع", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard(Icons.Rounded.LibraryMusic, "کتابخانه", purple, card) {}
                QuickCard(Icons.Rounded.Favorite, "علاقه‌مندی", green, card) {}
                QuickCard(Icons.Rounded.Shuffle, "تصادفی", purple, card) { if (tracks.isNotEmpty()) play(tracks.random()) }
            }
        }
        item { Text("آهنگ‌های آماده پخش", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }
        items(recent, key = { it.id }) { track -> TrackRow(track, track.id == current?.id, playing, purple, green, play) }
        item { Spacer(Modifier.height(6.dp)) }
    }
}

@Composable
private fun HeroCard(current: HomeTrack?, playing: Boolean, count: Int, purple: Color, green: Color, play: (HomeTrack) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(220.dp).shadow(22.dp, RoundedCornerShape(30.dp), ambientColor = purple, spotColor = purple).clip(RoundedCornerShape(30.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF28113F), Color(0xFF0B2D1B), Color(0xFF111018)))
        ).padding(24.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("NOW", color = green, fontWeight = FontWeight.Black)
                Text("$count آهنگ", color = Color(0xFFBEB4C6))
            }
            Column {
                Text(current?.title ?: "موسیقی شروع می‌شود", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(current?.artist ?: "یک آهنگ انتخاب کن و بزن بریم", color = Color(0xFFC7BDCC), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).shadow(16.dp, CircleShape, ambientColor = green, spotColor = green).clip(CircleShape).background(green).clickable { current?.let(play) }, contentAlignment = Alignment.Center) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color(0xFF06200F))
                    }
                    Spacer(Modifier.size(12.dp))
                    Text("پخش سریع", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun QuickCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, glow: Color, card: Color, onClick: () -> Unit) {
    Column(Modifier.weight(1f).height(92.dp).shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = glow, spotColor = glow).clip(RoundedCornerShape(22.dp)).background(card).clickable(onClick = onClick).padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Icon(icon, null, tint = glow, modifier = Modifier.size(24.dp))
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TrackRow(track: HomeTrack, selected: Boolean, playing: Boolean, purple: Color, green: Color, play: (HomeTrack) -> Unit) {
    Row(Modifier.fillMaxWidth().shadow(if (selected) 10.dp else 0.dp, RoundedCornerShape(18.dp), ambientColor = green, spotColor = green).clip(RoundedCornerShape(18.dp)).background(if (selected) Color(0xFF171F1B) else Color(0xFF100E15)).clickable { play(track) }.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(purple, Color(0xFF25202E)))), contentAlignment = Alignment.Center) {
            Icon(if (selected && playing) Icons.Rounded.Equalizer else Icons.Rounded.MusicNote, null, tint = Color.White)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title.ifBlank { "بدون نام" }, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist.ifBlank { "هنرمند ناشناس" }, color = Color(0xFF948B9D), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = if (selected) green else Color(0xFF77707F))
    }
}

@Composable
private fun MiniNowPlaying(track: HomeTrack, playing: Boolean, position: Long, duration: Long, purple: Color, green: Color, toggle: () -> Unit, replay: () -> Unit) {
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), ambientColor = purple, spotColor = purple).background(Color(0xF015121C)).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Box(Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(Color(0xFF2A2530))) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(green))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(purple, green))), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White) }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist.ifBlank { "هنرمند ناشناس" }, color = Color(0xFF938A9A), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = toggle) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = green) }
            IconButton(onClick = replay) { Icon(Icons.Rounded.SkipNext, null, tint = Color.White) }
        }
    }
}

@Composable
private fun BottomNav(tab: HomeTab, purple: Color, green: Color, onTab: (HomeTab) -> Unit) {
    Surface(color = Color(0xFF0B0910), tonalElevation = 0.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            BottomItem(Icons.Rounded.Home, "خانه", tab == HomeTab.HOME, purple) { onTab(HomeTab.HOME) }
            BottomItem(Icons.Rounded.LibraryMusic, "کتابخانه", tab == HomeTab.LIBRARY, green) { onTab(HomeTab.LIBRARY) }
            BottomItem(Icons.Rounded.Search, "جستجو", tab == HomeTab.SEARCH, purple) { onTab(HomeTab.SEARCH) }
            BottomItem(Icons.Rounded.Settings, "تنظیمات", tab == HomeTab.SETTINGS, green) { onTab(HomeTab.SETTINGS) }
        }
    }
}

@Composable
private fun BottomItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, selected: Boolean, glow: Color, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (selected) glow else Color(0xFF6D6674), modifier = Modifier.size(22.dp).shadow(if (selected) 10.dp else 0.dp, CircleShape, ambientColor = glow, spotColor = glow))
        Text(title, color = if (selected) Color.White else Color(0xFF6D6674), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LibraryScreen(tracks: List<HomeTrack>, current: HomeTrack?, playing: Boolean, purple: Color, green: Color, card: Color, play: (HomeTrack) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("کتابخانه", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold) }
        item { Text("${tracks.size} آهنگ روی دستگاه", color = Color(0xFF948B9D)) }
        items(tracks, key = { it.id }) { TrackRow(it, it.id == current?.id, playing, purple, green, play) }
    }
}

@Composable
private fun SearchScreen(query: String, tracks: List<HomeTrack>, current: HomeTrack?, playing: Boolean, purple: Color, green: Color, card: Color, onQuery: (String) -> Unit, play: (HomeTrack) -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("جستجو", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("نام آهنگ، هنرمند یا آلبوم") })
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) { items(tracks, key = { it.id }) { TrackRow(it, it.id == current?.id, playing, purple, green, play) } }
    }
}

@Composable
private fun SettingsScreen(purple: Color, green: Color, count: Int) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("تنظیمات", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        SettingRow(Icons.Rounded.MusicNote, "کتابخانه موسیقی", "$count آهنگ", purple)
        SettingRow(Icons.Rounded.GraphicEq, "ظاهر", "Glow + Dark", green)
        SettingRow(Icons.Rounded.BatterySaver, "پخش پس‌زمینه", "فعال", purple)
        SettingRow(Icons.Rounded.Info, "SEAM Player", "2.0 • Built for music", green)
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, glow: Color) {
    Row(Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(20.dp), ambientColor = glow, spotColor = glow).clip(RoundedCornerShape(20.dp)).background(Color(0xFF121019)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = glow, modifier = Modifier.size(26.dp))
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(value, color = Color(0xFF8E8696), style = MaterialTheme.typography.labelMedium) }
    }
}

private fun loadHomeTracks(context: Context): List<HomeTrack> {
    val result = mutableListOf<HomeTrack>()
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM)
    context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { cursor ->
        val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        while (cursor.moveToNext()) {
            val trackId = cursor.getLong(id)
            result += HomeTrack(trackId, cursor.getString(title) ?: "", cursor.getString(artist) ?: "", cursor.getString(album) ?: "", MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(trackId.toString()).build().toString())
        }
    }
    return result
}
