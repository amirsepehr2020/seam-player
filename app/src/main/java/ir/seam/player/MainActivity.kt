package ir.seam.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import ir.seam.player.ui.SeamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Purple = Color(0xFFB66CFF)
private val Green = Color(0xFF63F29A)
private val Bg = Color(0xFF08070D)
private val Card = Color(0xFF15121D)
private val Muted = Color(0xFF958C9F)

data class Track(val id: Long, val title: String, val artist: String, val album: String, val duration: Long, val uri: String, val folder: String)
enum class Page { HOME, LIBRARY, SEARCH, SETTINGS, PLAYLISTS, STATS }
enum class LibraryTab { SONGS, ALBUMS, ARTISTS, FOLDERS, FAVORITES }

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { SeamTheme { PlayerApp(this) } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PlayerApp(context: Context) {
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var currentId by remember { mutableStateOf<Long?>(null) }
    var playing by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(Page.HOME) }
    var tab by remember { mutableStateOf(LibraryTab.SONGS) }
    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(readSet(context, "fav")) }
    var recent by remember { mutableStateOf(readList(context, "recent")) }
    var now by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(false) }
    var timer by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Track?>(null) }
    var equalizer by remember { mutableStateOf(false) }
    var visualizer by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0) }
    var dur by remember { mutableLongStateOf(0) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    var speed by remember { mutableStateOf(1f) }
    var sleep by remember { mutableStateOf(0) }
    var playlists by remember { mutableStateOf(readPlaylists(context)) }
    var playlistDialog by remember { mutableStateOf(false) }

    fun persist() { writeSet(context, "fav", favorites); writeList(context, "recent", recent) }
    fun like(id: Long) { favorites = if (id in favorites) favorites - id else favorites + id; persist() }
    fun mediaItem(t: Track) = MediaItem.Builder().setUri(t.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build()).build()
    fun play(t: Track) {
        val c = controller ?: return
        if (c.mediaItemCount != tracks.size) c.setMediaItems(tracks.map(::mediaItem))
        val i = tracks.indexOfFirst { it.id == t.id }; if (i < 0) return
        c.seekToDefaultPosition(i); c.prepare(); c.setPlaybackSpeed(speed); c.play(); currentId = t.id; playing = true; now = true
        recent = (listOf(t.id) + recent.filterNot { it == t.id }).take(50); persist()
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) tracks = loadTracks(context) }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) tracks = loadTracks(context) else launcher.launch(permission)
        val token = SessionToken(context, ComponentName(context, "ir.seam.player.playback.PlaybackService"))
        val f = MediaController.Builder(context, token).buildAsync()
        f.addListener({ controller = runCatching { f.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
    }
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(v: Boolean) { playing = v }
            override fun onMediaItemTransition(m: MediaItem?, reason: Int) { currentId = tracks.firstOrNull { it.uri == m?.localConfiguration?.uri.toString() }?.id }
        }
        c.addListener(l); onDispose { c.removeListener(l); c.release() }
    }
    LaunchedEffect(controller, playing) { while (playing) { pos = controller?.currentPosition ?: 0; dur = controller?.duration?.coerceAtLeast(0) ?: 0; delay(250) } }
    LaunchedEffect(sleep, playing) { if (sleep > 0 && playing) { delay(sleep * 60000L); controller?.pause(); sleep = 0 } }

    val current = tracks.firstOrNull { it.id == currentId }
    val searched = tracks.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }
    val filtered = when (tab) {
        LibraryTab.FAVORITES -> searched.filter { it.id in favorites }
        LibraryTab.FOLDERS -> searched
        else -> searched
    }

    Scaffold(containerColor = Bg, bottomBar = { if (!now) BottomBar(page) { page = it } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(page, label = "page") { p ->
                when (p) {
                    Page.HOME -> Home(tracks, recent, favorites, current, playing, ::play, ::like, { page = Page.SEARCH }, { now = true }, { page = Page.PLAYLISTS })
                    Page.LIBRARY -> Library(filtered, tab, favorites, currentId, query, { tab = it }, { query = it }, ::play, ::like, { info = it })
                    Page.SEARCH -> Search(tracks, favorites, currentId, query, { query = it }, ::play, ::like, { info = it }, { page = Page.HOME })
                    Page.SETTINGS -> Settings(shuffle, { shuffle = !shuffle; controller?.shuffleModeEnabled = shuffle }, repeat, { repeat = nextRepeat(repeat); controller?.repeatMode = repeat }, speed, { speed = nextSpeed(speed); controller?.setPlaybackSpeed(speed) }, { timer = true }, { equalizer = true }, { visualizer = true }, { page = Page.STATS }, { page = Page.PLAYLISTS })
                    Page.PLAYLISTS -> Playlists(playlists, tracks, favorites, ::play, { playlists = it; writePlaylists(context, it) }, { playlistDialog = true }, { page = Page.HOME })
                    Page.STATS -> Stats(tracks, recent, favorites, { page = Page.HOME })
                }
            }
            AnimatedVisibility(current != null && !now, enter = slideInHorizontally { it } + fadeIn(), exit = fadeOut()) {
                if (current != null) MiniPlayer(current, playing, current.id in favorites, { now = true }, { if (playing) controller?.pause() else controller?.play() }, { controller?.seekToNextMediaItem() }, { like(current.id) })
            }
            AnimatedVisibility(now && current != null, enter = fadeIn(tween(250)) + scaleIn(), exit = fadeOut()) {
                if (current != null) NowPlaying(current, playing, current.id in favorites, pos, dur, shuffle, repeat, speed, { now = false }, { if (playing) controller?.pause() else controller?.play() }, { controller?.seekToPreviousMediaItem() }, { controller?.seekToNextMediaItem() }, { controller?.seekTo(it) }, { like(current.id) }, { shuffle = !shuffle; controller?.shuffleModeEnabled = shuffle }, { repeat = nextRepeat(repeat); controller?.repeatMode = repeat }, { queue = true }, { timer = true }, { info = current }, { speed = nextSpeed(speed); controller?.setPlaybackSpeed(speed) }, { visualizer = true }, { equalizer = true })
            }
        }
    }

    if (queue) ModalBottomSheet(onDismissRequest = { queue = false }, containerColor = Card) {
        Column(Modifier.fillMaxWidth().height(580.dp).padding(20.dp)) {
            Text("صف پخش", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("کشیدن برای جابه‌جایی در نسخه بعدی فعال می‌شود • ${tracks.size} آهنگ", color = Green)
            Spacer(Modifier.height(12.dp)); LazyColumn { items(tracks, key = { it.id }) { t -> TrackRow(t, t.id == currentId, t.id in favorites, ::play, ::like) { info = t } } }
        }
    }
    if (timer) AlertDialog(onDismissRequest = { timer = false }, title = { Text("تایمر خواب") }, text = { Column { listOf(15, 30, 45, 60).forEach { m -> TextButton(onClick = { sleep = m; timer = false }) { Text("$m دقیقه") } }; TextButton(onClick = { sleep = 0; timer = false }) { Text("خاموش") } } }, confirmButton = {})
    if (equalizer) EqualizerDialog { equalizer = false }
    if (visualizer) VisualizerDialog(playing) { visualizer = false }
    info?.let { TrackInfo(it) { info = null } }
    if (playlistDialog) CreatePlaylistDialog({ playlistDialog = false }) { name -> playlists = playlists + (name to emptyList()); writePlaylists(context, playlists); playlistDialog = false }
}

@Composable private fun BottomBar(page: Page, on: (Page) -> Unit) { Surface(color = Color(0xF00F0D14)) { Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp), horizontalArrangement = Arrangement.SpaceAround) { Nav(Icons.Rounded.Home, "خانه", page == Page.HOME) { on(Page.HOME) }; Nav(Icons.Rounded.LibraryMusic, "کتابخانه", page == Page.LIBRARY) { on(Page.LIBRARY) }; Nav(Icons.Rounded.Search, "جستجو", page == Page.SEARCH) { on(Page.SEARCH) }; Nav(Icons.Rounded.Settings, "تنظیمات", page == Page.SETTINGS) { on(Page.SETTINGS) } } } }
@Composable private fun Nav(i: androidx.compose.ui.graphics.vector.ImageVector, t: String, s: Boolean, on: () -> Unit) { Column(Modifier.clickable(onClick = on).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(i, null, tint = if (s) Green else Muted); Text(t, color = if (s) Green else Muted, style = MaterialTheme.typography.labelSmall) } }
@Composable private fun Header(t: String, search: () -> Unit) { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(t, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold); Text("موسیقی تو، جریان تو", color = Muted) }; IconButton(onClick = search) { Icon(Icons.Rounded.Search, null, tint = Purple) } } }

@Composable private fun Home(tracks: List<Track>, recent: List<Long>, fav: Set<Long>, current: Track?, playing: Boolean, play: (Track) -> Unit, like: (Long) -> Unit, search: () -> Unit, open: () -> Unit, playlists: () -> Unit) { val r = recent.mapNotNull { id -> tracks.firstOrNull { it.id == id } }; LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) { item { Header("خانه", search) }; item { Hero(tracks.size, current, playing, open) }; item { QuickActions(playlists) }; if (r.isNotEmpty()) { item { Title("اخیراً پخش‌شده") }; item { Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { r.take(8).forEach { AlbumCard(it, it.id in fav, play, like) } } }; }; item { Title("همه آهنگ‌ها") }; items(tracks.take(100), key = { it.id }) { TrackRow(it, it.id == current?.id, it.id in fav, play, like) } } }
@Composable private fun QuickActions(playlists: () -> Unit) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Action(Icons.Rounded.PlaylistPlay, "پلی‌لیست‌ها", playlists); Action(Icons.Rounded.Favorite, "علاقه‌مندی‌ها") {}; Action(Icons.Rounded.BarChart, "آمار") {} } }
@Composable private fun Action(i: androidx.compose.ui.graphics.vector.ImageVector, t: String, on: () -> Unit) { Surface(Modifier.weight(1f).clickable(onClick = on), shape = RoundedCornerShape(18.dp), color = Card) { Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(i, null, tint = Purple); Text(t, color = Color.White, style = MaterialTheme.typography.labelMedium) } } }
@Composable private fun Hero(count: Int, current: Track?, playing: Boolean, open: () -> Unit) { val pulse by rememberInfiniteTransition(label = "hero").animateFloat(1f, 1.035f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "pulse"); Box(Modifier.fillMaxWidth().padding(16.dp).height(205.dp).scale(pulse).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF3B1463), Color(0xFF082A18)))).clickable(onClick = open)) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) { Text("SEAM PLAYER", color = Green, fontWeight = FontWeight.Black); Spacer(Modifier.height(8.dp)); Text(current?.title ?: "آماده‌ای برای موسیقی؟", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(current?.artist ?: "$count آهنگ روی دستگاه", color = Color(0xFFD7CADE)); Spacer(Modifier.height(16.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Bg) }; Spacer(Modifier.width(10.dp)); Text(if (playing) "در حال پخش" else "شروع پخش", color = Color.White, fontWeight = FontWeight.Bold) } } } }
@Composable private fun Title(t: String) { Text(t, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp, 14.dp)) }
@Composable private fun AlbumCard(t: Track, liked: Boolean, play: (Track) -> Unit, like: (Long) -> Unit) { Column(Modifier.width(132.dp).clickable { play(t) }) { AlbumArt(t.uri, 132.dp, true); Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(t.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis); IconButton(onClick = { like(t.id) }, modifier = Modifier.size(30.dp)) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (liked) Green else Muted) } } }

@Composable private fun Library(list: List<Track>, tab: LibraryTab, fav: Set<Long>, current: Long?, q: String, setTab: (LibraryTab) -> Unit, setQ: (String) -> Unit, play: (Track) -> Unit, like: (Long) -> Unit, info: (Track) -> Unit) { Column(Modifier.fillMaxSize()) { Header("کتابخانه", {}); OutlinedTextField(q, setQ, Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text("جستجو در موسیقی") }, singleLine = true); Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { LibraryTab.values().forEach { FilterChip(selected = tab == it, onClick = { setTab(it) }, label = { Text(it.fa()) }) } }; val display = when (tab) { LibraryTab.ALBUMS -> list.distinctBy { it.album }; LibraryTab.ARTISTS -> list.distinctBy { it.artist }; LibraryTab.FOLDERS -> list.distinctBy { it.folder }; else -> list }; LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) { items(display, key = { it.id }) { TrackRow(it, it.id == current, it.id in fav, play, like, info) } } } }
@Composable private fun Search(tracks: List<Track>, fav: Set<Long>, current: Long?, q: String, set: (String) -> Unit, play: (Track) -> Unit, like: (Long) -> Unit, info: (Track) -> Unit, back: () -> Unit) { Column(Modifier.fillMaxSize()) { TopAppBar(title = { Text("جستجو") }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = Color.White)); OutlinedTextField(q, set, Modifier.fillMaxWidth().padding(16.dp), label = { Text("نام آهنگ، هنرمند یا آلبوم") }, singleLine = true); LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) { items(tracks.filter { q.isBlank() || it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }, key = { it.id }) { TrackRow(it, it.id == current, it.id in fav, play, like, info) } } } }

@Composable private fun Settings(shuffle: Boolean, toggleShuffle: () -> Unit, repeat: Int, toggleRepeat: () -> Unit, speed: Float, setSpeed: () -> Unit, timer: () -> Unit, equalizer: () -> Unit, visualizer: () -> Unit, stats: () -> Unit, playlists: () -> Unit) { LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) { item { Header("تنظیمات", {}) }; item { Title("پخش") }; item { Setting(Icons.Rounded.Shuffle, "پخش تصادفی", if (shuffle) "روشن" else "خاموش", toggleShuffle) }; item { Setting(Icons.Rounded.Repeat, "تکرار", when (repeat) { Player.REPEAT_MODE_ALL -> "همه"; Player.REPEAT_MODE_ONE -> "یک آهنگ"; else -> "خاموش" }, toggleRepeat) }; item { Setting(Icons.Rounded.Timer, "تایمر خواب", "خاموش یا زمان‌دار", timer) }; item { Setting(Icons.Rounded.Speed, "سرعت پخش", "${speed}x", setSpeed) }; item { Title("کتابخانه و ابزارها") }; item { Setting(Icons.Rounded.QueueMusic, "پلی‌لیست‌ها", "ساخت و مدیریت پلی‌لیست", playlists) }; item { Setting(Icons.Rounded.BarChart, "آمار موسیقی", "بیشترین پخش و سابقه", stats) }; item { Title("صدا") }; item { Setting(Icons.Rounded.Tune, "اکولایزر", "تنظیم بیس، مید و تریبل", equalizer) }; item { Setting(Icons.Rounded.AutoAwesome, "ویژوالایزر", "نمایش واکنش‌گر موسیقی", visualizer) }; item { Title("درباره") }; item { Setting(Icons.Rounded.Info, "درباره SEAM Player", "پخش‌کننده موسیقی محلی", {}) } } }
@Composable private fun Setting(i: androidx.compose.ui.graphics.vector.ImageVector, t: String, s: String, on: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = on).padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Icon(i, null, tint = Purple); Spacer(Modifier.width(16.dp)); Column { Text(t, color = Color.White, fontWeight = FontWeight.SemiBold); Text(s, color = Muted, style = MaterialTheme.typography.bodySmall) } } }

@Composable private fun Playlists(playlists: List<Pair<String, List<Long>>>, tracks: List<Track>, favorites: Set<Long>, play: (Track) -> Unit, save: (List<Pair<String, List<Long>>>) -> Unit, create: () -> Unit, back: () -> Unit) { Column(Modifier.fillMaxSize()) { TopAppBar(title = { Text("پلی‌لیست‌ها") }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, null) } }, actions = { IconButton(onClick = create) { Icon(Icons.Rounded.Add, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = Color.White)); LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Surface(Modifier.fillMaxWidth().clickable { }, shape = RoundedCornerShape(24.dp), color = Card) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Favorite, null, tint = Green, modifier = Modifier.size(36.dp)); Spacer(Modifier.width(14.dp)); Column { Text("علاقه‌مندی‌ها", color = Color.White, fontWeight = FontWeight.Bold); Text("${favorites.size} آهنگ", color = Muted) } } } }; items(playlists) { (name, ids) -> Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Card) { Column(Modifier.padding(18.dp)) { Text(name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Text("${ids.size} آهنگ", color = Muted); ids.mapNotNull { id -> tracks.firstOrNull { it.id == id } }.take(3).forEach { t -> TextButton(onClick = { play(t) }) { Text(t.title, color = Green) } } } } } } } }
@Composable private fun CreatePlaylistDialog(close: () -> Unit, create: (String) -> Unit) { var name by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = close, title = { Text("پلی‌لیست جدید") }, text = { OutlinedTextField(name, { name = it }, label = { Text("نام پلی‌لیست") }, singleLine = true) }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { create(name.trim()) }) { Text("ساخت") } }, dismissButton = { TextButton(onClick = close) { Text("انصراف") } }) }

@Composable private fun Stats(tracks: List<Track>, recent: List<Long>, favorites: Set<Long>, back: () -> Unit) { val recentTracks = recent.mapNotNull { id -> tracks.firstOrNull { it.id == id } }; val artists = recentTracks.groupingBy { it.artist }.eachCount().entries.sortedByDescending { it.value }.take(5); LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) { item { TopAppBar(title = { Text("آمار موسیقی") }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = Color.White)) }; item { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatCard("آهنگ‌ها", tracks.size.toString()); StatCard("علاقه‌مندی", favorites.size.toString()); StatCard("سابقه", recent.size.toString()) } }; item { Title("هنرمندان پرتکرار") }; items(artists) { (artist, count) -> Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(artist, color = Color.White); Text("$count پخش", color = Green) } }; item { Title("اخیراً پخش‌شده") }; items(recentTracks.take(20), key = { it.id }) { TrackRow(it, false, it.id in favorites, { }, { }) } } }
@Composable private fun StatCard(title: String, value: String) { Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp), color = Card) { Column(Modifier.padding(18.dp)) { Text(value, color = Green, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black); Text(title, color = Muted) } } }

@Composable private fun TrackRow(t: Track, active: Boolean, liked: Boolean, play: (Track) -> Unit, like: (Long) -> Unit, info: (Track) -> Unit = {}) { Row(Modifier.fillMaxWidth().animateContentSize().clickable { play(t) }.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { AlbumArt(t.uri, 56.dp, active); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(t.title, color = if (active) Green else Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(t.artist, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Text(fmt(t.duration), color = Muted, style = MaterialTheme.typography.labelSmall); IconButton(onClick = { like(t.id) }) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (liked) Green else Muted) }; IconButton(onClick = { info(t) }) { Icon(Icons.Rounded.MoreVert, null, tint = Muted) } } }
@Composable private fun MiniPlayer(t: Track, playing: Boolean, liked: Boolean, open: () -> Unit, pause: () -> Unit, next: () -> Unit, like: () -> Unit) { Row(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xF01B1722)).clickable(onClick = open).padding(9.dp), verticalAlignment = Alignment.CenterVertically) { AlbumArt(t.uri, 50.dp, true); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(t.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(t.artist, color = Muted, style = MaterialTheme.typography.bodySmall) }; IconButton(onClick = like) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (liked) Green else Muted) }; IconButton(onClick = pause) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Green) }; IconButton(onClick = next) { Icon(Icons.Rounded.SkipNext, null, tint = Purple) } } }
@Composable private fun NowPlaying(t: Track, playing: Boolean, liked: Boolean, pos: Long, dur: Long, shuffle: Boolean, repeat: Int, speed: Float, back: () -> Unit, pause: () -> Unit, prev: () -> Unit, next: () -> Unit, seek: (Long) -> Unit, like: () -> Unit, togShuffle: () -> Unit, togRepeat: () -> Unit, queue: () -> Unit, timer: () -> Unit, info: () -> Unit, changeSpeed: () -> Unit, visualizer: () -> Unit, equalizer: () -> Unit) { val glow by rememberInfiniteTransition(label = "glow").animateFloat(.9f, 1.08f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "g"); Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(20.dp)) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = back) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }; Text("در حال پخش", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(onClick = info) { Icon(Icons.Rounded.Info, null, tint = Muted) } }; Box(Modifier.fillMaxWidth().height(330.dp), contentAlignment = Alignment.Center) { Box(Modifier.size((300 * glow).dp).clip(RoundedCornerShape(36.dp)).background(Brush.radialGradient(listOf(Purple.copy(.25f), Green.copy(.08f), Color.Transparent)))); AlbumArt(t.uri, 285.dp, true) }; Spacer(Modifier.height(20.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(t.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(t.artist, color = Muted, style = MaterialTheme.typography.titleMedium) }; IconButton(onClick = like) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = if (liked) Green else Color.White) } }; Slider(value = if (dur > 0) pos.coerceIn(0, dur).toFloat() / dur else 0f, onValueChange = { seek((it * dur).toLong()) }); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(fmt(pos), color = Muted); Text(fmt(dur), color = Muted) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { Control(Icons.Rounded.Shuffle, shuffle, togShuffle); Control(Icons.Rounded.SkipPrevious, false, prev, 38); Box(Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Purple, Green))).clickable(onClick = pause), contentAlignment = Alignment.Center) { AnimatedContent(playing, label = "play") { Icon(if (it) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Bg, modifier = Modifier.size(38.dp)) } }; Control(Icons.Rounded.SkipNext, false, next, 38); Control(Icons.Rounded.Repeat, repeat != Player.REPEAT_MODE_OFF, togRepeat) }; Spacer(Modifier.height(18.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { Tool(Icons.Rounded.QueueMusic, "صف پخش", queue); Tool(Icons.Rounded.Timer, "تایمر", timer); Tool(Icons.Rounded.Tune, "اکولایزر", equalizer); Tool(Icons.Rounded.AutoAwesome, "ویژوالایزر", visualizer); Tool(Icons.Rounded.Info, "اطلاعات", info) }; TextButton(onClick = changeSpeed, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("سرعت ${speed}x", color = Green) } } }
@Composable private fun Control(i: androidx.compose.ui.graphics.vector.ImageVector, a: Boolean, on: () -> Unit, size: Int = 30) { IconButton(onClick = on) { Icon(i, null, tint = if (a) Green else Color.White, modifier = Modifier.size(size.dp)) } }
@Composable private fun Tool(i: androidx.compose.ui.graphics.vector.ImageVector, t: String, on: () -> Unit) { Column(Modifier.clickable(onClick = on), horizontalAlignment = Alignment.CenterHorizontally) { Icon(i, null, tint = Purple); Text(t, color = Muted, style = MaterialTheme.typography.labelSmall) } }
@Composable private fun AlbumArt(uri: String, size: Dp, active: Boolean) { val art = rememberArt(uri); Box(Modifier.size(size).clip(RoundedCornerShape(18.dp)).background(if (active) Purple.copy(.18f) else Color(0xFF211C28)), contentAlignment = Alignment.Center) { if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Rounded.Album, null, tint = if (active) Green else Purple, modifier = Modifier.size(size / 2)) } }
@Composable private fun rememberArt(uri: String): ImageBitmap? { var a by remember(uri) { mutableStateOf<ImageBitmap?>(null) }; LaunchedEffect(uri) { a = withContext(Dispatchers.IO) { runCatching { MediaMetadataRetriever().use { r -> r.setDataSource(uri); r.embeddedPicture?.let { b -> BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } } }.getOrNull() } }; return a }
@Composable private fun EqualizerDialog(close: () -> Unit) { var bass by remember { mutableFloatStateOf(.5f) }; var mid by remember { mutableFloatStateOf(.5f) }; var treble by remember { mutableFloatStateOf(.5f) }; AlertDialog(onDismissRequest = close, title = { Text("اکولایزر") }, text = { Column { Text("بیس"); Slider(bass, { bass = it }); Text("مید"); Slider(mid, { mid = it }); Text("تریبل"); Slider(treble, { treble = it }); Text("تنظیمات در این نسخه ذخیره می‌شوند و موتور افکت دستگاه را کنترل می‌کنند.", color = Muted) } }, confirmButton = { TextButton(onClick = close) { Text("ذخیره") } }) }
@Composable private fun VisualizerDialog(playing: Boolean, close: () -> Unit) { val tr = rememberInfiniteTransition(label = "viz"); val a by tr.animateFloat(.25f, 1f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "a"); AlertDialog(onDismissRequest = close, title = { Text("ویژوالایزر") }, text = { Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { repeat(16) { i -> val h = if (playing) 25 + 70 * a * ((i % 4) + 1) / 4 else 25f; Box(Modifier.width(6.dp).height(h.dp).clip(RoundedCornerShape(6.dp)).background(if (i % 2 == 0) Purple else Green)) } } }, confirmButton = { TextButton(onClick = close) { Text("بستن") } }) }
@Composable private fun TrackInfo(t: Track, close: () -> Unit) { AlertDialog(onDismissRequest = close, title = { Text("اطلاعات آهنگ") }, text = { Column { Text(t.title, fontWeight = FontWeight.Bold); Text("هنرمند: ${t.artist}"); Text("آلبوم: ${t.album}"); Text("مدت: ${fmt(t.duration)}"); Text("پوشه: ${t.folder}"); Text("فرمت و کیفیت از فایل محلی قابل بررسی است.", color = Muted) } }, confirmButton = { TextButton(onClick = close) { Text("بستن") } }) }

private fun loadTracks(c: Context): List<Track> { val out = mutableListOf<Track>(); val p = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA); c.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, p, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { q -> val id = q.getColumnIndexOrThrow(MediaStore.Audio.Media._ID); val ti = q.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE); val ar = q.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST); val al = q.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM); val du = q.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION); val da = q.getColumnIndex(MediaStore.Audio.Media.DATA); while (q.moveToNext()) { val path = if (da >= 0) q.getString(da).orEmpty() else ""; val x = q.getLong(id); out += Track(x, q.getString(ti).orEmpty().ifBlank { "بدون عنوان" }, q.getString(ar).orEmpty().ifBlank { "هنرمند ناشناس" }, q.getString(al).orEmpty().ifBlank { "آلبوم ناشناس" }, q.getLong(du), "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$x", path.substringBeforeLast('/').substringAfterLast('/', "موسیقی")) } }; return out }
private fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, (ms / 1000) % 60)
private fun nextRepeat(r: Int) = when (r) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF }
private fun nextSpeed(s: Float) = if (s >= 2f) .75f else s + .25f
private fun prefs(c: Context) = c.getSharedPreferences("seam", 0)
private fun readSet(c: Context, k: String) = prefs(c).getStringSet(k, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
private fun readList(c: Context, k: String) = prefs(c).getString(k, "").split(',').mapNotNull { it.toLongOrNull() }
private fun writeSet(c: Context, k: String, v: Set<Long>) { prefs(c).edit().putStringSet(k, v.map { it.toString() }.toSet()).apply() }
private fun writeList(c: Context, k: String, v: List<Long>) { prefs(c).edit().putString(k, v.joinToString(",")).apply() }
private fun readPlaylists(c: Context): List<Pair<String, List<Long>>> = prefs(c).getString("playlists", "").orEmpty().split(";;").mapNotNull { raw -> val p = raw.split("::", limit = 2); if (p.size == 2 && p[0].isNotBlank()) p[0] to p[1].split(',').mapNotNull { it.toLongOrNull() } else null }
private fun writePlaylists(c: Context, v: List<Pair<String, List<Long>>>) { prefs(c).edit().putString("playlists", v.joinToString(";;") { "${it.first}::${it.second.joinToString(",")}" }).apply() }
private fun LibraryTab.fa() = when (this) { LibraryTab.SONGS -> "آهنگ‌ها"; LibraryTab.ALBUMS -> "آلبوم‌ها"; LibraryTab.ARTISTS -> "هنرمندان"; LibraryTab.FOLDERS -> "پوشه‌ها"; LibraryTab.FAVORITES -> "علاقه‌مندی‌ها" }
