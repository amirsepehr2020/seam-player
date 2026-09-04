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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import ir.seam.player.ui.SeamTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SeamTheme { SeamPlayerApp(this) } }
    }
}

data class Track(val id: Long, val title: String, val artist: String, val uri: String)

@Composable
private fun SeamPlayerApp(context: Context) {
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var currentIndex by remember { mutableStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var libraryFilter by remember { mutableStateOf("All") }
    var favorites by remember { mutableStateOf(setOf<Long>()) }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) tracks = loadTracks(context)
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) tracks = loadTracks(context) else launcher.launch(permission)
        val token = SessionToken(context, ComponentName(context, "ir.seam.player.playback.PlaybackService"))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = tracks.indexOfFirst { it.uri == mediaItem?.localConfiguration?.uri.toString() }
            }
        }
        c.addListener(listener)
        onDispose { c.removeListener(listener); c.release() }
    }

    fun play(index: Int) {
        controller?.let { c ->
            if (c.mediaItemCount != tracks.size) c.setMediaItems(tracks.map { MediaItem.fromUri(it.uri) })
            c.seekToDefaultPosition(index); c.prepare(); c.play(); currentIndex = index; showPlayer = true
        }
    }

    val visibleTracks = tracks.filter { track ->
        val matchesSearch = searchQuery.isBlank() || track.title.contains(searchQuery, true) || track.artist.contains(searchQuery, true)
        val matchesFilter = libraryFilter != "Liked" || favorites.contains(track.id)
        matchesSearch && matchesFilter
    }
    val current = tracks.getOrNull(currentIndex)

    Scaffold(containerColor = Color(0xFF08070D), bottomBar = { BottomBar(selectedTab) { selectedTab = it } }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                HomeHeader(onSearch = { searchOpen = true })
                if (selectedTab == 0) HomeContent(visibleTracks, currentIndex, ::play)
                else LibraryContent(visibleTracks, currentIndex, ::play, libraryFilter) { libraryFilter = it }
            }
            AnimatedVisibility(visible = current != null && !showPlayer, modifier = Modifier.align(Alignment.BottomCenter)) {
                MiniPlayer(current!!, isPlaying, { showPlayer = true }, { controller?.let { if (it.isPlaying) it.pause() else it.play() } }, { controller?.seekToNextMediaItem() }, favorites.contains(current.id) { favorites = if (favorites.contains(current.id)) favorites - current.id else favorites + current.id })
            }
            if (showPlayer && current != null) {
                NowPlaying(current, isPlaying, controller, favorites.contains(current.id), { favorites = if (favorites.contains(current.id)) favorites - current.id else favorites + current.id }, { showPlayer = false })
            }
        }
    }

    if (searchOpen) SearchDialog(searchQuery, { searchQuery = it }, { searchOpen = false })
}

private fun loadTracks(context: Context): List<Track> {
    val list = mutableListOf<Track>()
    val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
    context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            list += Track(id, cursor.getString(titleCol) ?: "Unknown track", cursor.getString(artistCol) ?: "Unknown artist", "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$id")
        }
    }
    return list
}

@Composable private fun SearchDialog(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text("Search music") }, text = {
        androidx.compose.material3.OutlinedTextField(value = query, onValueChange = onQuery, singleLine = true, label = { Text("Song or artist") })
    }, confirmButton = { TextButton(onClick = onClose) { Text("Done") } })
}

@Composable private fun HomeHeader(onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("SEAM", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black); Text("YOUR MUSIC. YOUR FLOW.", color = Color(0xFF8D8A99), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
        IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search", tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable private fun HomeContent(tracks: List<Track>, current: Int, onTrack: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp).height(190.dp).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF32145A), Color(0xFF0B2015))))) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) { Text("WELCOME TO SEAM", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Music, without the noise.", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(16.dp)); Text("${tracks.size} tracks on this device", color = Color(0xFFB8B4C2)) }
            }
            Text("ALL MUSIC", color = Color(0xFF8D8A99), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
        itemsIndexed(tracks) { index, track -> TrackRow(track, index == current) { onTrack(index) } }
    }
}

@Composable private fun LibraryContent(tracks: List<Track>, current: Int, onTrack: (Int) -> Unit, filter: String, onFilter: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("YOUR LIBRARY", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Liked").forEach { value -> FilterChip(value, filter == value) { onFilter(value) } }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item { LibraryTile(Icons.Rounded.Favorite, "Liked songs", "${tracks.count()} shown", filter == "Liked") { onFilter("Liked") } }
            item { LibraryTile(Icons.Rounded.Album, "Albums", "Browse your local albums", false) { onFilter("All") } }
            item { LibraryTile(Icons.Rounded.LibraryMusic, "Artists", "Browse your local artists", false) { onFilter("All") } }
            itemsIndexed(tracks) { index, track -> TrackRow(track, index == current) { onTrack(index) } }
        }
    }
}

@Composable private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(label, color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) MaterialTheme.colorScheme.secondary else Color(0xFF19161F)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable private fun LibraryTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(18.dp)).background(if (selected) Color(0xFF211C2B) else Color(0xFF15131A)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF2A2533)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(14.dp)); Column { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(subtitle, color = Color(0xFF77717F), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun TrackRow(track: Track, active: Boolean, onClick: () -> Unit) {
    val art = rememberAlbumArt(track.uri)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArt(art, 52.dp, active)
        Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(track.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, color = Color(0xFF77717F), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Icon(Icons.Rounded.MoreVert, null, tint = Color(0xFF77717F))
    }
}

@Composable private fun AlbumArt(art: ImageBitmap?, size: androidx.compose.ui.unit.Dp, active: Boolean) {
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(if (active) MaterialTheme.colorScheme.primaryContainer else Color(0xFF201D26)), contentAlignment = Alignment.Center) {
        if (art != null) Image(art, null, Modifier.fillMaxSize()) else Icon(Icons.Rounded.Album, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size / 2))
    }
}

@Composable private fun rememberAlbumArt(uri: String): ImageBitmap? {
    var art by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) { art = withContext(Dispatchers.IO) { runCatching { MediaMetadataRetriever().use { r -> r.setDataSource(uri); r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } } }.getOrNull() } }
    return art
}

@Composable private fun MiniPlayer(track: Track, playing: Boolean, onOpen: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit, liked: Boolean, onLike: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).navigationBarsPadding().clip(RoundedCornerShape(22.dp)).background(Color(0xFF17141E)).clickable(onClick = onOpen).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AlbumArt(rememberAlbumArt(track.uri), 48.dp, true)
        Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(track.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, color = Color(0xFF77717F), style = MaterialTheme.typography.bodySmall) }
        IconButton(onClick = onLike) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = MaterialTheme.colorScheme.secondary) }
        IconButton(onClick = onPlay) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White) }; IconButton(onClick = onNext) { Icon(Icons.Rounded.SkipNext, null, tint = Color.White) }
    }
}

@Composable private fun NowPlaying(track: Track, playing: Boolean, controller: MediaController?, liked: Boolean, onLike: () -> Unit, onClose: () -> Unit) {
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(controller, playing, track.id) {
        while (true) { if (!dragging) { position = controller?.currentPosition?.coerceAtLeast(0) ?: 0; duration = controller?.duration?.coerceAtLeast(0) ?: 0 }; delay(300) }
    }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF09070E)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
                Text("NOW PLAYING", color = Color(0xFF77717F), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box { IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, null, tint = Color.White) }; DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { DropdownMenuItem(text = { Text(if (liked) "Remove from liked" else "Add to liked") }, onClick = { onLike(); menuOpen = false }) } }
            }
            Spacer(Modifier.height(40.dp)); val scale by animateFloatAsState(if (playing) 1f else .96f, label = "album")
            Box(Modifier.size(300.dp).scale(scale).clip(RoundedCornerShape(34.dp)).background(Brush.linearGradient(listOf(Color(0xFF5B2A88), Color(0xFF0B4A2A)))), contentAlignment = Alignment.Center) { AlbumArt(rememberAlbumArt(track.uri), 300.dp, true) }
            Spacer(Modifier.height(28.dp)); Text(track.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(track.artist, color = Color(0xFF88818F), style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onLike) { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = MaterialTheme.colorScheme.secondary) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(position), color = Color(0xFF77717F), style = MaterialTheme.typography.labelSmall)
                Slider(value = if (duration > 0) position.toFloat() / duration else 0f, onValueChange = { dragging = true; position = (it * duration).toLong() }, onValueChangeFinished = { controller?.seekTo(position); dragging = false }, modifier = Modifier.weight(1f))
                Text(formatTime(duration), color = Color(0xFF77717F), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { controller?.shuffleModeEnabled = !(controller?.shuffleModeEnabled ?: false) }) { Icon(Icons.Rounded.Shuffle, null, tint = if (controller?.shuffleModeEnabled == true) MaterialTheme.colorScheme.secondary else Color.White) }
                IconButton(onClick = { controller?.seekToPreviousMediaItem() }) { Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { controller?.let { if (it.isPlaying) it.pause() else it.play() } }, modifier = Modifier.size(72.dp)) { Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(34.dp)) } }
                IconButton(onClick = { controller?.seekToNextMediaItem() }) { Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { controller?.repeatMode = when (controller?.repeatMode) { Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL; Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE; else -> Player.REPEAT_MODE_OFF } }) { Icon(Icons.Rounded.Repeat, null, tint = if (controller?.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.secondary else Color.White) }
            }
            Spacer(Modifier.height(20.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.VolumeUp, null, tint = Color(0xFF88818F)); Spacer(Modifier.width(8.dp)); Text("SEAM AUDIO • LOCAL ONLY", color = Color(0xFF66606D), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

private fun formatTime(ms: Long): String { val total = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(total / 60, total % 60) }

@Composable private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().background(Color(0xFF0D0B12)).padding(horizontal = 30.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceAround) { BottomItem(Icons.Rounded.Home, "Home", selected == 0) { onSelect(0) }; BottomItem(Icons.Rounded.LibraryMusic, "Library", selected == 1) { onSelect(1) } }
}

@Composable private fun BottomItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 5.dp)) { Icon(icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF66606D)); Text(label, color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF66606D), style = MaterialTheme.typography.labelSmall) }
}
