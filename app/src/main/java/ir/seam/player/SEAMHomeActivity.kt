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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

class SEAMHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        setContent { SeamTheme { SeamHome(this) } }
    }
}

data class HomeTrack(val id: Long, val title: String, val artist: String, val album: String, val uri: String, val added: Long)
enum class HomeTab { HOME, LIBRARY, SEARCH, SETTINGS }
enum class LibraryMode { SONGS, ALBUMS, ARTISTS, FAVORITES, RECENT, MOST_PLAYED }

@Composable private fun SeamHome(context: Context) {
    val prefs = remember { context.getSharedPreferences("seam_player", Context.MODE_PRIVATE) }
    val purple = Color(0xFFB66CFF); val green = Color(0xFF63F29A); val bg = Color(0xFF08070D); val card = Color(0xFF121019)
    var tracks by remember { mutableStateOf(emptyList<HomeTrack>()) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var currentUri by remember { mutableStateOf<String?>(null) }; var playing by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(HomeTab.HOME) }; var libraryMode by remember { mutableStateOf(LibraryMode.SONGS) }; var query by remember { mutableStateOf("") }
    var position by remember { mutableLongStateOf(0L) }; var duration by remember { mutableLongStateOf(0L) }
    var favorites by remember { mutableStateOf(loadLongSet(prefs, "favorites")) }; var recent by remember { mutableStateOf(loadLongList(prefs, "recent")) }; var playCounts by remember { mutableStateOf(loadCounts(prefs)) }
    var sleepRemaining by remember { mutableStateOf(0L) }; var timerMenu by remember { mutableStateOf(false) }
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) tracks = loadHomeTracks(context) }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) tracks = loadHomeTracks(context) else launcher.launch(permission)
        runCatching { val token = SessionToken(context, ComponentName(context, "ir.seam.player.playback.PlaybackService")); val future = MediaController.Builder(context, token).buildAsync(); future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context)) }
    }
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener { override fun onIsPlayingChanged(v: Boolean) { playing = v }; override fun onMediaItemTransition(item: MediaItem?, reason: Int) { currentUri = item?.localConfiguration?.uri?.toString(); duration = c.duration.takeIf { it > 0 } ?: 0 } }
        c.addListener(listener); currentUri = c.currentMediaItem?.localConfiguration?.uri?.toString(); playing = c.isPlaying; duration = c.duration.takeIf { it > 0 } ?: 0
        onDispose { c.removeListener(listener); runCatching { c.release() } }
    }
    LaunchedEffect(controller, playing) { while (true) { val c = controller ?: break; position = c.currentPosition.coerceAtLeast(0); duration = c.duration.takeIf { it > 0 } ?: 0; if (!playing) break; delay(300) } }
    LaunchedEffect(sleepRemaining) { while (sleepRemaining > 0) { delay(1000); sleepRemaining = (sleepRemaining - 1000).coerceAtLeast(0); if (sleepRemaining == 0L) controller?.pause() } }

    val mediaItems = remember(tracks) { tracks.map { t -> MediaItem.Builder().setUri(t.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build()).build() } }
    fun toggleFavorite(track: HomeTrack) { favorites = favorites.toMutableSet().apply { if (!add(track.id)) remove(track.id) }; saveLongSet(prefs, "favorites", favorites) }
    fun play(track: HomeTrack) {
        val c = controller ?: return; val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        runCatching { c.setMediaItems(mediaItems, index, 0L); c.prepare(); c.play(); currentUri = track.uri; recent = listOf(track.id) + recent.filterNot { it == track.id }.take(19); playCounts = playCounts.toMutableMap().apply { this[track.id] = (this[track.id] ?: 0) + 1 }; saveLongList(prefs, "recent", recent); saveCounts(prefs, playCounts) }
    }
    val current = tracks.firstOrNull { it.uri == currentUri }; val filtered = tracks.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }; val ordered = filtered.sortedBy { it.title.lowercase() }
    val recentTracks = recent.mapNotNull { id -> tracks.firstOrNull { it.id == id } }; val favoriteTracks = ordered.filter { it.id in favorites }; val mostPlayed = ordered.sortedByDescending { playCounts[it.id] ?: 0 }

    Scaffold(containerColor = bg, bottomBar = { Column { current?.let { MiniNowPlaying(it, playing, position, duration, purple, green, { if (playing) controller?.pause() else controller?.play() }, { controller?.seekToNextMediaItem() }, { context.startActivity(android.content.Intent(context, NowPlayingActivity::class.java)) }) }; BottomNav(tab, purple, green) { tab = it } } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                HomeTab.HOME -> HomeScreen(tracks, current, playing, favorites, recentTracks, purple, green, card, ::play, ::toggleFavorite, { tab = HomeTab.SEARCH }, { tab = HomeTab.LIBRARY; libraryMode = LibraryMode.SONGS }, { tab = HomeTab.LIBRARY; libraryMode = LibraryMode.FAVORITES }, { tab = HomeTab.LIBRARY; libraryMode = LibraryMode.RECENT })
                HomeTab.LIBRARY -> LibraryScreen(ordered, current, playing, favorites, libraryMode, recentTracks, favoriteTracks, mostPlayed, purple, green, ::play, ::toggleFavorite) { libraryMode = it }
                HomeTab.SEARCH -> SearchScreen(query, ordered, current, playing, favorites, purple, green, { query = it }, ::play, ::toggleFavorite)
                HomeTab.SETTINGS -> SettingsScreen(purple, green, tracks.size, sleepRemaining) { timerMenu = true }
            }
        }
    }
    if (timerMenu) AlertDialog(onDismissRequest = { timerMenu = false }, title = { Text("تایمر خواب") }, text = { Column { listOf("۱۵ دقیقه" to 15L, "۳۰ دقیقه" to 30L, "۴۵ دقیقه" to 45L, "۶۰ دقیقه" to 60L).forEach { (label, m) -> TextButton(onClick = { sleepRemaining = m * 60000; timerMenu = false }, modifier = Modifier.fillMaxWidth()) { Text(label, modifier = Modifier.fillMaxWidth()) } }; TextButton(onClick = { sleepRemaining = duration; timerMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("بعد از آهنگ فعلی", modifier = Modifier.fillMaxWidth()) }; TextButton(onClick = { sleepRemaining = 0; timerMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("خاموش کردن") } } }, confirmButton = { TextButton(onClick = { timerMenu = false }) { Text("بستن") } })
}

@Composable private fun HomeScreen(tracks: List<HomeTrack>, current: HomeTrack?, playing: Boolean, favorites: Set<Long>, recent: List<HomeTrack>, purple: Color, green: Color, card: Color, play: (HomeTrack) -> Unit, toggleFavorite: (HomeTrack) -> Unit, openSearch: () -> Unit, openLibrary: () -> Unit, openFavorites: () -> Unit, openRecent: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("SEAM", color = purple, fontWeight = FontWeight.Black); Text("خانه", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineLarge); Text("موسیقی تو، ساده و سریع", color = Color(0xFF9E96A8)) }; IconButton(onClick = openSearch) { Icon(Icons.Rounded.Search, "جستجو", tint = Color.White) } } }
        item { HeroCard(current, playing, tracks.size, purple, green, play) }
        item { SectionHeader("Continue Listening", "ادامه آخرین آهنگ") }
        current?.let { item { TrackRow(it, true, playing, favorites.contains(it.id), purple, green, play, toggleFavorite) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { QuickCard(Icons.Rounded.LibraryMusic, "کتابخانه", green, card, openLibrary); QuickCard(Icons.Rounded.Favorite, "محبوب‌ها", purple, card, openFavorites); QuickCard(Icons.Rounded.History, "اخیر", green, card, openRecent) } }
        item { SectionHeader("Quick Mix", "یک ترکیب تصادفی از موزیک‌های خودت") }
        item { OutlinedButton(onClick = { if (tracks.isNotEmpty()) play(tracks.random()) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(8.dp)); Text("پخش Quick Mix") } }
        if (recent.isNotEmpty()) { item { SectionHeader("Recently Played", "آخرین شنیده‌ها") }; items(recent.take(6), key = { it.id }) { TrackRow(it, it.id == current?.id, playing, favorites.contains(it.id), purple, green, play, toggleFavorite) } }
        item { SectionHeader("All Music", "کتابخانه کامل") }
        items(tracks.take(12), key = { it.id }) { TrackRow(it, it.id == current?.id, playing, favorites.contains(it.id), purple, green, play, toggleFavorite) }
    }
}
@Composable private fun SectionHeader(title: String, subtitle: String) { Column { Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); Text(subtitle, color = Color(0xFF8F8797), style = MaterialTheme.typography.bodySmall) } }
@Composable private fun HeroCard(current: HomeTrack?, playing: Boolean, count: Int, purple: Color, green: Color, play: (HomeTrack) -> Unit) { Box(Modifier.fillMaxWidth().height(210.dp).shadow(22.dp, RoundedCornerShape(30.dp), ambientColor = purple, spotColor = purple).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF2A1243), Color(0xFF0A2818), Color(0xFF111018)))).padding(22.dp)) { Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("NOW PLAYING", color = green, fontWeight = FontWeight.Black); Text("$count آهنگ", color = Color(0xFFBEB4C6)) }; Column { Text(current?.title ?: "موسیقی شروع می‌شود", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(current?.artist ?: "یک آهنگ انتخاب کن", color = Color(0xFFC7BDCC), maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(12.dp)); Box(Modifier.size(50.dp).shadow(14.dp, CircleShape, ambientColor = green, spotColor = green).clip(CircleShape).background(green).clickable { current?.let(play) }, contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color(0xFF06200F)) } } } } }
@Composable private fun RowScope.QuickCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, glow: Color, card: Color, onClick: () -> Unit) { Column(Modifier.weight(1f).height(92.dp).shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = glow, spotColor = glow).clip(RoundedCornerShape(22.dp)).background(card).clickable(onClick = onClick).padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(icon, null, tint = glow); Text(title, color = Color.White, fontWeight = FontWeight.SemiBold) } }
@Composable private fun TrackRow(track: HomeTrack, selected: Boolean, playing: Boolean, favorite: Boolean, purple: Color, green: Color, play: (HomeTrack) -> Unit, toggleFavorite: (HomeTrack) -> Unit) { Row(Modifier.fillMaxWidth().shadow(if (selected) 10.dp else 0.dp, RoundedCornerShape(18.dp), ambientColor = green, spotColor = green).clip(RoundedCornerShape(18.dp)).background(if (selected) Color(0xFF171F1B) else Color(0xFF100E15)).clickable { play(track) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(purple, Color(0xFF25202E)))), contentAlignment = Alignment.Center) { Icon(if (selected && playing) Icons.Rounded.Equalizer else Icons.Rounded.MusicNote, null, tint = Color.White) }; Spacer(Modifier.size(12.dp)); Column(Modifier.weight(1f)) { Text(track.title.ifBlank { "بدون نام" }, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist.ifBlank { "هنرمند ناشناس" }, color = Color(0xFF948B9D), maxLines = 1, overflow = TextOverflow.Ellipsis) }; IconButton(onClick = { toggleFavorite(track) }) { Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "محبوب", tint = if (favorite) Color(0xFFFF6B9A) else Color(0xFF77707F)) } } }
@Composable private fun MiniNowPlaying(track: HomeTrack, playing: Boolean, position: Long, duration: Long, purple: Color, green: Color, toggle: () -> Unit, next: () -> Unit, open: () -> Unit) { val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f; Column(Modifier.fillMaxWidth().clickable(onClick = open).background(Color(0xF015121C)).padding(horizontal = 14.dp, vertical = 9.dp)) { Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0xFF2A2530))) { Box(Modifier.fillMaxWidth(progress).height(2.dp).background(green)) }; Spacer(Modifier.height(7.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(purple, green))), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White) }; Spacer(Modifier.size(10.dp)); Column(Modifier.weight(1f)) { Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist.ifBlank { "هنرمند ناشناس" }, color = Color(0xFF938A9A), maxLines = 1, overflow = TextOverflow.Ellipsis) }; IconButton(onClick = toggle) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = green) }; IconButton(onClick = next) { Icon(Icons.Rounded.SkipNext, null, tint = Color.White) } } } }
@Composable private fun LibraryScreen(tracks: List<HomeTrack>, current: HomeTrack?, playing: Boolean, favorites: Set<Long>, mode: LibraryMode, recent: List<HomeTrack>, favoriteTracks: List<HomeTrack>, mostPlayed: List<HomeTrack>, purple: Color, green: Color, play: (HomeTrack) -> Unit, toggleFavorite: (HomeTrack) -> Unit, setMode: (LibraryMode) -> Unit) { val source = when (mode) { LibraryMode.FAVORITES -> favoriteTracks; LibraryMode.RECENT -> recent; LibraryMode.MOST_PLAYED -> mostPlayed; else -> tracks }; LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("کتابخانه", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineLarge); Text("${tracks.size} آهنگ روی دستگاه", color = Color(0xFF9E96A8)) }; item { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { LibraryChip("آهنگ‌ها", LibraryMode.SONGS, mode, setMode); LibraryChip("آلبوم", LibraryMode.ALBUMS, mode, setMode); LibraryChip("هنرمند", LibraryMode.ARTISTS, mode, setMode); LibraryChip("محبوب", LibraryMode.FAVORITES, mode, setMode) } }; item { Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { LibraryChip("اخیر", LibraryMode.RECENT, mode, setMode); LibraryChip("پربازدید", LibraryMode.MOST_PLAYED, mode, setMode) } }; if (mode == LibraryMode.ALBUMS || mode == LibraryMode.ARTISTS) { val groups = if (mode == LibraryMode.ALBUMS) tracks.groupBy { it.album.ifBlank { "آلبوم ناشناس" } }.keys else tracks.groupBy { it.artist.ifBlank { "هنرمند ناشناس" } }.keys; items(groups.toList(), key = { it }) { name -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF100E15)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (mode == LibraryMode.ALBUMS) Icons.Rounded.Album else Icons.Rounded.Person, null, tint = green); Spacer(Modifier.width(12.dp)); Text(name, color = Color.White, fontWeight = FontWeight.SemiBold) } } } else items(source, key = { it.id }) { TrackRow(it, it.id == current?.id, playing, favorites.contains(it.id), purple, green, play, toggleFavorite) } } }
@Composable private fun LibraryChip(label: String, target: LibraryMode, current: LibraryMode, onClick: (LibraryMode) -> Unit) { FilterChip(selected = target == current, onClick = { onClick(target) }, label = { Text(label) }) }
@Composable private fun SearchScreen(query: String, tracks: List<HomeTrack>, current: HomeTrack?, playing: Boolean, favorites: Set<Long>, purple: Color, green: Color, onQuery: (String) -> Unit, play: (HomeTrack) -> Unit, toggleFavorite: (HomeTrack) -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp)) { Text("جستجو", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(10.dp)); OutlinedTextField(value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("آهنگ، هنرمند، آلبوم یا پوشه") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }); Spacer(Modifier.height(14.dp)); LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(tracks, key = { it.id }) { TrackRow(it, it.id == current?.id, playing, favorites.contains(it.id), purple, green, play, toggleFavorite) } } } }
@Composable private fun SettingsScreen(purple: Color, green: Color, count: Int, sleepRemaining: Long, openTimer: () -> Unit) { Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text("تنظیمات", color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineLarge); SettingCard("تجربه پخش", "Media3 / ExoPlayer • پخش در پس‌زمینه", purple); SettingCard("کتابخانه", "$count آهنگ شناسایی شده", green); SettingCard("طراحی", "Dark + Glow + فونت Vazirmatn", purple); SettingCard("آمار", "Favorites + Recently Played + Most Played", green); Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF121019))) { Row(Modifier.fillMaxWidth().clickable(onClick = openTimer).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Timer, null, tint = green); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Sleep Timer", color = Color.White, fontWeight = FontWeight.Bold); Text(if (sleepRemaining > 0) "${sleepRemaining / 60000} دقیقه باقی مانده" else "خاموش", color = Color(0xFF98909E)) }; Icon(Icons.Rounded.ChevronRight, null, tint = Color.White) } } } }
@Composable private fun SettingCard(title: String, subtitle: String, glow: Color) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF121019)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).shadow(8.dp, CircleShape, ambientColor = glow, spotColor = glow).background(glow, CircleShape)); Spacer(Modifier.size(12.dp)); Column { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(subtitle, color = Color(0xFF98909E)) } } }
@Composable private fun BottomNav(tab: HomeTab, purple: Color, green: Color, onTab: (HomeTab) -> Unit) { Surface(color = Color(0xFF0B0910)) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) { BottomItem(Icons.Rounded.Home, "خانه", tab == HomeTab.HOME, purple) { onTab(HomeTab.HOME) }; BottomItem(Icons.Rounded.LibraryMusic, "کتابخانه", tab == HomeTab.LIBRARY, green) { onTab(HomeTab.LIBRARY) }; BottomItem(Icons.Rounded.Search, "جستجو", tab == HomeTab.SEARCH, purple) { onTab(HomeTab.SEARCH) }; BottomItem(Icons.Rounded.Settings, "تنظیمات", tab == HomeTab.SETTINGS, green) { onTab(HomeTab.SETTINGS) } } } }
@Composable private fun BottomItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, selected: Boolean, glow: Color, onClick: () -> Unit) { Column(Modifier.clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = if (selected) glow else Color(0xFF8A828F)); Text(title, color = if (selected) glow else Color(0xFF8A828F), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) } }
private fun loadHomeTracks(context: Context): List<HomeTrack> { val list = mutableListOf<HomeTrack>(); val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI; val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DATE_ADDED); context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c -> val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM); val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED); while (c.moveToNext()) { val songId = c.getLong(id); list.add(HomeTrack(songId, c.getString(title) ?: "", c.getString(artist) ?: "", c.getString(album) ?: "", MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.buildUpon().appendPath(songId.toString()).build().toString(), c.getLong(added))) } }; return list }
private fun loadLongSet(p: android.content.SharedPreferences, key: String): Set<Long> = p.getStringSet(key, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
private fun saveLongSet(p: android.content.SharedPreferences, key: String, value: Set<Long>) { p.edit().putStringSet(key, value.map(Long::toString).toSet()).apply() }
private fun loadLongList(p: android.content.SharedPreferences, key: String): List<Long> = p.getString(key, "")?.split(',')?.mapNotNull { it.toLongOrNull() } ?: emptyList()
private fun saveLongList(p: android.content.SharedPreferences, key: String, value: List<Long>) { p.edit().putString(key, value.joinToString(",")).apply() }
private fun loadCounts(p: android.content.SharedPreferences): Map<Long, Int> = p.getString("play_counts", "")?.split(';')?.mapNotNull { part -> val x = part.split(':'); if (x.size == 2) x[0].toLongOrNull()?.let { it to (x[1].toIntOrNull() ?: 0) } else null }?.toMap() ?: emptyMap()
private fun saveCounts(p: android.content.SharedPreferences, value: Map<Long, Int>) { p.edit().putString("play_counts", value.entries.joinToString(";") { "${it.key}:${it.value}" }).apply() }
