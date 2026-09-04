package ir.seam.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalConfiguration
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

private val V2Bg = Color(0xFF08070D)
private val V2Card = Color(0xFF14111B)
private val V2Muted = Color(0xFF918899)
private val V2Green = Color(0xFF63F29A)
private val V2Purple = Color(0xFFB66CFF)

data class V2Track(val id: Long, val title: String, val artist: String, val album: String, val uri: String, val duration: Long, val folder: String)

enum class V2Page { HOME, LIBRARY, SEARCH, PLAYLISTS, SETTINGS }
enum class V2Library { SONGS, ALBUMS, ARTISTS, FOLDERS, FAVORITES, RECENT, MOST_PLAYED }
enum class V2Theme(val title: String, val accent: Color, val accent2: Color) { GREEN("SEAM Green", V2Green, V2Purple), PURPLE("Purple", V2Purple, V2Green), BLUE("Blue", Color(0xFF62A8FF), Color(0xFF8B7CFF)), RED("Red", Color(0xFFFF5E72), Color(0xFFFFA05C)), AMOLED("AMOLED", Color.White, V2Green) }

data class V2Playlist(val name: String, val ids: List<Long>)

class SeamHomeV2Activity : ComponentActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); setContent { SeamTheme { V2Player(this) } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun V2Player(context: Context) {
    val prefs = remember { context.getSharedPreferences("seam_v2", Context.MODE_PRIVATE) }
    var tracks by remember { mutableStateOf(emptyList<V2Track>()) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var currentUri by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var page by remember { mutableStateOf(V2Page.HOME) }
    var library by remember { mutableStateOf(V2Library.SONGS) }
    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(loadIds(prefs, "favorites")) }
    var recent by remember { mutableStateOf(loadIds(prefs, "recent")) }
    var counts by remember { mutableStateOf(loadCountsV2(prefs)) }
    var playlists by remember { mutableStateOf(loadPlaylistsV2(prefs)) }
    var showNow by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showThemes by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showVisualizer by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(runCatching { V2Theme.valueOf(prefs.getString("theme", "GREEN")!!) }.getOrDefault(V2Theme.GREEN)) }
    var sleepMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var newPlaylist by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var eqPreset by remember { mutableStateOf("Normal") }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) tracks = loadV2Tracks(context) }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) tracks = loadV2Tracks(context) else permissionLauncher.launch(permission)
        runCatching {
            val token = SessionToken(context, ComponentName(context, "ir.seam.player.playback.PlaybackService"))
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
        }
    }
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(v: Boolean) { playing = v }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) { currentUri = item?.localConfiguration?.uri?.toString(); duration = c.duration.coerceAtLeast(0) }
        }
        c.addListener(listener); currentUri = c.currentMediaItem?.localConfiguration?.uri?.toString(); playing = c.isPlaying
        onDispose { c.removeListener(listener); runCatching { c.release() } }
    }
    LaunchedEffect(controller, playing) { while (playing) { position = controller?.currentPosition?.coerceAtLeast(0) ?: 0; duration = controller?.duration?.coerceAtLeast(0) ?: 0; delay(250) } }
    LaunchedEffect(sleepMs) { if (sleepMs > 0) { delay(sleepMs); controller?.pause(); sleepMs = 0 } }

    val current = tracks.firstOrNull { it.uri == currentUri }
    val searched = tracks.filter { query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) || it.folder.contains(query, true) }
    val shown = when (library) {
        V2Library.FAVORITES -> searched.filter { it.id in favorites }
        V2Library.RECENT -> recent.mapNotNull { id -> searched.firstOrNull { it.id == id } }
        V2Library.MOST_PLAYED -> searched.sortedByDescending { counts[it.id] ?: 0 }
        V2Library.ALBUMS -> searched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album }).distinctBy { it.album }
        V2Library.ARTISTS -> searched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist }).distinctBy { it.artist }
        V2Library.FOLDERS -> searched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.folder }).distinctBy { it.folder }
        else -> searched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }
    fun item(t: V2Track) = MediaItem.Builder().setUri(t.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build()).build()
    fun toggleFav(t: V2Track) { favorites = favorites.toMutableSet().apply { if (!add(t.id)) remove(t.id) }; saveIds(prefs, "favorites", favorites) }
    fun play(t: V2Track) { val c = controller ?: return; val index = tracks.indexOfFirst { it.id == t.id }; if (index < 0) return; runCatching { c.setMediaItems(tracks.map(::item), index, 0); c.setPlaybackSpeed(speed); c.shuffleModeEnabled = shuffle; c.repeatMode = repeat; c.prepare(); c.play(); currentUri = t.uri; playing = true; recent = (listOf(t.id) + recent.filterNot { it == t.id }).take(50); counts = counts.toMutableMap().apply { this[t.id] = (this[t.id] ?: 0) + 1 }; saveIds(prefs, "recent", recent); saveCountsV2(prefs, counts) } }

    Scaffold(containerColor = if (theme == V2Theme.AMOLED) Color.Black else V2Bg, bottomBar = { Column { current?.let { V2Mini(it, playing, position, duration, theme, { showNow = true }, { if (playing) controller?.pause() else controller?.play() }, { controller?.seekToNextMediaItem() }) }; V2Bottom(page, theme) { page = it } } }) { pad ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
            val compact = maxWidth < 600.dp || LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
            when (page) {
                V2Page.HOME -> V2Home(tracks, current, playing, favorites, recent, theme, ::play, ::toggleFav, { page = V2Page.SEARCH }, { page = V2Page.LIBRARY }, { library = V2Library.FAVORITES; page = V2Page.LIBRARY }, { library = V2Library.RECENT; page = V2Page.LIBRARY }, { showNow = true }, compact)
                V2Page.LIBRARY -> V2LibraryScreen(shown, current, playing, favorites, library, theme, { library = it }, ::play, ::toggleFav)
                V2Page.SEARCH -> V2Search(query, searched, current, playing, favorites, theme, { query = it }, ::play, ::toggleFav)
                V2Page.PLAYLISTS -> V2Playlists(playlists, tracks, favorites, theme, ::play, { playlists = it; savePlaylistsV2(prefs, it) }, { newPlaylist = true; selectedPlaylist = null })
                V2Page.SETTINGS -> V2Settings(theme, shuffle, repeat, speed, sleepMs, eqPreset, { shuffle = !shuffle; controller?.shuffleModeEnabled = shuffle }, { repeat = if (repeat == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else if (repeat == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF; controller?.repeatMode = repeat }, { speed = if (speed >= 1.5f) .75f else speed + .25f; controller?.setPlaybackSpeed(speed) }, { showTimer = true }, { showThemes = true }, { showEqualizer = true }, { showVisualizer = true }, { page = V2Page.PLAYLISTS })
            }
            if (showNow && current != null) V2Now(current, playing, position, duration, favorites.contains(current.id), theme, shuffle, repeat, speed, { showNow = false }, { if (playing) controller?.pause() else controller?.play() }, { controller?.seekToPreviousMediaItem() }, { controller?.seekToNextMediaItem() }, { controller?.seekTo(it) }, { toggleFav(current) }, { shuffle = !shuffle; controller?.shuffleModeEnabled = shuffle }, { repeat = if (repeat == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else if (repeat == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF; controller?.repeatMode = repeat }, { showQueue = true }, { showTimer = true }, { speed = if (speed >= 1.5f) .75f else speed + .25f; controller?.setPlaybackSpeed(speed) }, { showVisualizer = true }, { showEqualizer = true })
        }
    }
    if (showQueue) V2Queue(tracks, current, favorites, theme, ::play, ::toggleFav) { showQueue = false }
    if (showTimer) V2Timer { sleepMs = it; showTimer = false }
    if (showThemes) V2Themes(theme) { theme = it; prefs.edit().putString("theme", it.name).apply(); showThemes = false }
    if (showEqualizer) V2Equalizer(eqPreset) { eqPreset = it; showEqualizer = false }
    if (showVisualizer) V2Visualizer(playing, theme) { showVisualizer = false }
    if (newPlaylist) V2CreatePlaylist { name -> playlists = playlists + V2Playlist(name, emptyList()); savePlaylistsV2(prefs, playlists); newPlaylist = false }
}

@Composable private fun V2Home(tracks: List<V2Track>, current: V2Track?, playing: Boolean, fav: Set<Long>, recent: List<Long>, theme: V2Theme, play: (V2Track)->Unit, like:(V2Track)->Unit, search:()->Unit, library:()->Unit, favorites:()->Unit, recentPage:()->Unit, now:()->Unit, compact:Boolean) { LazyColumn(contentPadding=PaddingValues(16.dp,18.dp,16.dp,170.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) { item { V2Header("خانه", theme, search) }; item { V2Hero(current, playing, tracks.size, theme, play, now) }; item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp), modifier=Modifier.fillMaxWidth()) { V2Action(Icons.Rounded.LibraryMusic,"Library",theme,library); V2Action(Icons.Rounded.Favorite,"Favorites",theme,favorites); V2Action(Icons.Rounded.History,"Recent",theme,recentPage) } }; item { V2Section("Continue Listening") }; current?.let { item { V2Track(it,it.id in fav,it.id==current.id,playing,theme,play,like) } }; item { V2Section("Quick Mix") }; item { Button(onClick={if(tracks.isNotEmpty()) play(tracks.random())}, modifier=Modifier.fillMaxWidth(), colors=ButtonDefaults.buttonColors(containerColor=theme.accent)) { Icon(Icons.Rounded.Shuffle,null); Spacer(Modifier.width(8.dp)); Text("پخش ترکیبی") } }; val rs=recent.mapNotNull { id->tracks.firstOrNull{it.id==id} }.take(8); if(rs.isNotEmpty()){ item{V2Section("Recently Played")}; items(rs,key={it.id}){V2Track(it,it.id in fav,it.id==current?.id,playing,theme,play,like)} }; item{V2Section("All Music")}; items(tracks.take(if(compact)100 else 200),key={it.id}){V2Track(it,it.id in fav,it.id==current?.id,playing,theme,play,like)} } }
@Composable private fun V2Header(title:String, theme:V2Theme, search:()->Unit){ Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){ Column(Modifier.weight(1f)){Text("SEAM PLAYER",color=theme.accent,fontWeight=FontWeight.Black);Text(title,color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold);Text("موسیقی تو، جریان تو",color=V2Muted)};IconButton(onClick=search){Icon(Icons.Rounded.Search,null,tint=Color.White)}}}
@Composable private fun V2Hero(t:V2Track?,playing:Boolean,count:Int,theme:V2Theme,play:(V2Track)->Unit,now:()->Unit){ Box(Modifier.fillMaxWidth().height(210.dp).shadow(22.dp,RoundedCornerShape(30.dp),ambientColor=theme.accent,spotColor=theme.accent).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(theme.accent.copy(.28f),theme.accent2.copy(.12f),V2Card))).clickable(onClick=now).padding(22.dp)){Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.SpaceBetween){Text("NOW PLAYING",color=theme.accent,fontWeight=FontWeight.Black);Column{Text(t?.title? : "آماده برای موسیقی",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t?.artist ?: "$count آهنگ روی دستگاه",color=V2Muted);Spacer(Modifier.height(12.dp));Box(Modifier.size(50.dp).clip(CircleShape).background(theme.accent).clickable{t?.let(play)},contentAlignment=Alignment.Center){Icon(if(playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null,tint=Color.Black)}}}}}
@Composable private fun V2Action(i:androidx.compose.ui.graphics.vector.ImageVector,t:String,theme:V2Theme,on:()->Unit){Column(Modifier.weight(1f).height(82.dp).shadow(12.dp,RoundedCornerShape(20.dp),ambientColor=theme.accent).clip(RoundedCornerShape(20.dp)).background(V2Card).clickable(onClick=on).padding(12.dp),verticalArrangement=Arrangement.SpaceBetween){Icon(i,null,tint=theme.accent);Text(t,color=Color.White,fontWeight=FontWeight.SemiBold)}}
@Composable private fun V2Section(t:String){Text(t,color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
@Composable private fun V2Track(t:V2Track,favorite:Boolean,selected:Boolean,playing:Boolean,theme:V2Theme,play:(V2Track)->Unit,like:(V2Track)->Unit){Row(Modifier.fillMaxWidth().shadow(if(selected)10.dp else 0.dp,RoundedCornerShape(18.dp),ambientColor=theme.accent).clip(RoundedCornerShape(18.dp)).background(if(selected)theme.accent.copy(.10f) else V2Card).clickable{play(t)}.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(theme.accent.copy(.7f),theme.accent2.copy(.5f)))),contentAlignment=Alignment.Center){Icon(if(selected&&playing)Icons.Rounded.Equalizer else Icons.Rounded.MusicNote,null,tint=Color.White)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(t.title.ifBlank{"بدون نام"},color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t.artist.ifBlank{"هنرمند ناشناس"},color=V2Muted,maxLines=1,overflow=TextOverflow.Ellipsis)};IconButton(onClick={like(t)}){Icon(if(favorite)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,null,tint=if(favorite)Color(0xFFFF6B9A) else V2Muted)}}}
@Composable private fun V2Mini(t:V2Track,playing:Boolean,pos:Long,dur:Long,theme:V2Theme,open:()->Unit,toggle:()->Unit,next:()->Unit){Column(Modifier.fillMaxWidth().background(Color(0xF014111B)).clickable(onClick=open).padding(10.dp)){LinearProgressIndicator(if(dur>0)pos.toFloat()/dur else 0f,Modifier.fillMaxWidth().height(2.dp),color=theme.accent,trackColor=theme.accent.copy(.15f));Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(t.title,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t.artist,color=V2Muted,style=MaterialTheme.typography.bodySmall)};IconButton(onClick=toggle){Icon(if(playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null,tint=theme.accent)};IconButton(onClick=next){Icon(Icons.Rounded.SkipNext,null,tint=Color.White)}}}}
@Composable private fun V2Bottom(page:V2Page,theme:V2Theme,on:(V2Page)->Unit){NavigationBar(containerColor=Color(0xFF100D15)){listOf(V2Page.HOME to (Icons.Rounded.Home to "خانه"),V2Page.LIBRARY to (Icons.Rounded.LibraryMusic to "کتابخانه"),V2Page.SEARCH to (Icons.Rounded.Search to "جستجو"),V2Page.PLAYLISTS to (Icons.Rounded.PlaylistPlay to "پلی‌لیست"),V2Page.SETTINGS to (Icons.Rounded.Settings to "تنظیمات")).forEach{(p,d)->NavigationBarItem(selected=page==p,onClick={on(p)},icon={Icon(d.first,null)},label={Text(d.second)},colors=NavigationBarItemDefaults.colors(selectedIconColor=theme.accent,selectedTextColor=theme.accent,unselectedIconColor=V2Muted,unselectedTextColor=V2Muted)})}}

@Composable private fun V2LibraryScreen(items:List<V2Track>,current:V2Track?,playing:Boolean,fav:Set<Long>,mode:V2Library,theme:V2Theme,setMode:(V2Library)->Unit,play:(V2Track)->Unit,like:(V2Track)->Unit){Column(Modifier.fillMaxSize().padding(16.dp)){Text("کتابخانه",color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold);Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){V2Library.values().forEach{FilterChip(selected=mode==it,onClick={setMode(it)},label={Text(it.name.replace('_',' '))})}};LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=150.dp)){items(items,key={it.id}){V2Track(it,it.id in fav,it.id==current?.id,playing,theme,play,like)}}}}
@Composable private fun V2Search(q:String,items:List<V2Track>,current:V2Track?,playing:Boolean,fav:Set<Long>,theme:V2Theme,setQ:(String)->Unit,play:(V2Track)->Unit,like:(V2Track)->Unit){Column(Modifier.fillMaxSize().padding(16.dp)){Text("جستجو",color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold);OutlinedTextField(q,setQ,Modifier.fillMaxWidth(),placeholder={Text("آهنگ، هنرمند، آلبوم، پوشه")},singleLine=true);Spacer(Modifier.height(12.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=150.dp)){items(items,key={it.id}){V2Track(it,it.id in fav,it.id==current?.id,playing,theme,play,like)}}}}

@Composable private fun V2Playlists(pls:List<V2Playlist>,tracks:List<V2Track>,fav:Set<Long>,theme:V2Theme,play:(V2Track)->Unit,update:(List<V2Playlist>)->Unit,create:()->Unit){Column(Modifier.fillMaxSize().padding(16.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("پلی‌لیست‌ها",color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold,Modifier.weight(1f));IconButton(onClick=create){Icon(Icons.Rounded.Add,null,tint=theme.accent)}};LazyColumn(contentPadding=PaddingValues(bottom=150.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){items(pls,key={it.name}){p->var expanded by remember{mutableStateOf(false)};Surface(Modifier.fillMaxWidth().clickable{expanded=!expanded},shape=RoundedCornerShape(20.dp),color=V2Card){Column(Modifier.padding(16.dp)){Text(p.name,color=Color.White,fontWeight=FontWeight.Bold);Text("${p.ids.size} آهنگ",color=theme.accent);AnimatedVisibility(expanded){Column{p.ids.mapNotNull{id->tracks.firstOrNull{it.id==id}}.forEach{V2Track(it,it.id in fav,false,false,theme,play,{})};if(tracks.isNotEmpty())TextButton(onClick={val id=tracks.first().id;update(pls.map{if(it.name==p.name)it.copy(ids=(it.ids+id).distinct()) else it})}){Text("+ اضافه کردن آهنگ")}}}}}}}}
@Composable private fun V2Settings(theme:V2Theme,shuffle:Boolean,repeat:Int,speed:Float,sleep:Long,eq:String,onShuffle:()->Unit,onRepeat:()->Unit,onSpeed:()->Unit,timer:()->Unit,themes:()->Unit,equalizer:()->Unit,visualizer:()->Unit,playlists:()->Unit){LazyColumn(contentPadding=PaddingValues(16.dp,18.dp,16.dp,160.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){item{Text("تنظیمات",color=Color.White,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold)};item{V2Setting("Shuffle",if(shuffle)"فعال" else "خاموش",theme,onShuffle,Icons.Rounded.Shuffle)};item{V2Setting("Repeat",when(repeat){Player.REPEAT_MODE_ALL->"همه";Player.REPEAT_MODE_ONE->"یکی";else->"خاموش"},theme,onRepeat,Icons.Rounded.Repeat)};item{V2Setting("Playback Speed","${speed}x",theme,onSpeed,Icons.Rounded.Speed)};item{V2Setting("Sleep Timer",if(sleep>0)"فعال" else "خاموش",theme,timer,Icons.Rounded.Bedtime)};item{V2Setting("Theme Engine",theme.title,theme,themes,Icons.Rounded.Palette)};item{V2Setting("Equalizer",eq,theme,equalizer,Icons.Rounded.GraphicEq)};item{V2Setting("Visualizer","Audio bars",theme,visualizer,Icons.Rounded.Equalizer)};item{V2Setting("Playlists","مدیریت پلی‌لیست",theme,playlists,Icons.Rounded.PlaylistPlay)};item{Text("Media3 برای کنترل‌های هندزفری و بلوتوث استفاده می‌شود؛ دکمه‌های media به Play/Pause/Next/Previous متصل‌اند.",color=V2Muted,style=MaterialTheme.typography.bodySmall)}}}
@Composable private fun V2Setting(t:String,v:String,theme:V2Theme,on:()->Unit,i:androidx.compose.ui.graphics.vector.ImageVector){ListItem(headlineContent={Text(t,color=Color.White)},supportingContent={Text(v,color=V2Muted)},leadingContent={Icon(i,null,tint=theme.accent)},modifier=Modifier.clip(RoundedCornerShape(18.dp)).background(V2Card).clickable(onClick=on))}

@Composable private fun V2Now(t:V2Track,playing:Boolean,pos:Long,dur:Long,fav:Boolean,theme:V2Theme,shuffle:Boolean,repeat:Int,speed:Float,close:()->Unit,toggle:()->Unit,prev:()->Unit,next:()->Unit,seek:(Long)->Unit,like:()->Unit,tShuffle:()->Unit,tRepeat:()->Unit,queue:()->Unit,timer:()->Unit,tSpeed:()->Unit,visualizer:()->Unit,equalizer:()->Unit){Surface(Modifier.fillMaxSize(),color=if(theme==V2Theme.AMOLED)Color.Black else V2Bg){Column(Modifier.fillMaxSize().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally){Row(Modifier.fillMaxWidth()){IconButton(onClick=close){Icon(Icons.Rounded.KeyboardArrowDown,null,tint=Color.White)};Spacer(Modifier.weight(1f));IconButton(onClick=queue){Icon(Icons.Rounded.QueueMusic,null,tint=theme.accent)}};Spacer(Modifier.height(20.dp));Box(Modifier.size(260.dp).shadow(35.dp,RoundedCornerShape(34.dp),ambientColor=theme.accent,spotColor=theme.accent).clip(RoundedCornerShape(34.dp)).background(Brush.linearGradient(listOf(theme.accent.copy(.75f),theme.accent2.copy(.35f)))) ,contentAlignment=Alignment.Center){Icon(Icons.Rounded.MusicNote,null,tint=Color.White,modifier=Modifier.size(100.dp))};Spacer(Modifier.height(25.dp));Text(t.title,color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t.artist,color=V2Muted);Spacer(Modifier.height(16.dp));Slider(value=if(dur>0)pos.toFloat()/dur else 0f,onValueChange={seek((it*dur).toLong())},colors=SliderDefaults.colors(thumbColor=theme.accent,activeTrackColor=theme.accent));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(formatMs(pos),color=V2Muted);Text(formatMs(dur),color=V2Muted)};Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=tShuffle){Icon(Icons.Rounded.Shuffle,null,tint=if(shuffle)theme.accent else V2Muted)};IconButton(onClick=prev){Icon(Icons.Rounded.SkipPrevious,null,tint=Color.White,modifier=Modifier.size(38.dp))};IconButton(onClick=toggle,modifier=Modifier.size(68.dp).clip(CircleShape).background(theme.accent)){Icon(if(playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null,tint=Color.Black)};IconButton(onClick=next){Icon(Icons.Rounded.SkipNext,null,tint=Color.White,modifier=Modifier.size(38.dp))};IconButton(onClick=tRepeat){Icon(Icons.Rounded.Repeat,null,tint=if(repeat!=Player.REPEAT_MODE_OFF)theme.accent else V2Muted)}};Spacer(Modifier.height(18.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){AssistChip(onClick=like,label={Text(if(fav)"♥" else "♡")});AssistChip(onClick=tSpeed,label={Text("${speed}x")});AssistChip(onClick=timer,label={Text("Sleep")});AssistChip(onClick=equalizer,label={Text("EQ")});AssistChip(onClick=visualizer,label={Text("Visualizer")})}}}}

@Composable private fun V2Queue(tracks:List<V2Track>,current:V2Track?,fav:Set<Long>,theme:V2Theme,play:(V2Track)->Unit,like:(V2Track)->Unit,close:()->Unit){ModalBottomSheet(onDismissRequest=close,containerColor=V2Card){Column(Modifier.fillMaxWidth().height(600.dp).padding(16.dp)){Text("صف پخش",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("برای تغییر ترتیب، از دکمه‌های بالا/پایین در نسخه بعدی استفاده می‌شود.",color=V2Muted);Spacer(Modifier.height(8.dp));LazyColumn{items(tracks,key={it.id}){V2Track(it,it.id in fav,it.id==current?.id,current?.id==it.id,theme,play,like)}}}}}
@Composable private fun V2Timer(on:(Long)->Unit){AlertDialog(onDismissRequest={on(0)},title={Text("تایمر خواب")},text={Column{listOf(15L,30,45,60).forEach{m->TextButton(onClick={on(m*60000)}){Text("$m دقیقه")}};TextButton(onClick={on(-1)}){Text("بعد از آهنگ فعلی")};TextButton(onClick={on(0)}){Text("خاموش")}}},confirmButton={})}
@Composable private fun V2Themes(current:V2Theme,on:(V2Theme)->Unit){AlertDialog(onDismissRequest={on(current)},title={Text("Theme Engine")},text={Column{V2Theme.values().forEach{TextButton(onClick={on(it)},modifier=Modifier.fillMaxWidth()){Text(it.title)}}}},confirmButton={})}
@Composable private fun V2Equalizer(current:String,on:(String)->Unit){AlertDialog(onDismissRequest={on(current)},title={Text("Equalizer")},text={Column{listOf("Normal","Bass","Vocal","Rock","Pop","Classical").forEach{TextButton(onClick={on(it)},modifier=Modifier.fillMaxWidth()){Text(it)}}}},confirmButton={})}
@Composable private fun V2Visualizer(playing:Boolean,theme:V2Theme,on:()->Unit){val tr=rememberInfiniteTransition(label="viz");val a=tr.animateFloat(.25f,1f,infiniteRepeatable(tween(350),RepeatMode.Reverse),label="a").value;AlertDialog(onDismissRequest=on,title={Text("Visualizer")},text={Row(Modifier.fillMaxWidth().height(100.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){repeat(18){Box(Modifier.width(7.dp).fillMaxHeight(if(playing)((a*(it%5+1))/5f).coerceAtLeast(.15f) else .12f).clip(RoundedCornerShape(5.dp)).background(theme.accent))}}},confirmButton={TextButton(onClick=on){Text("بستن")}})}
@Composable private fun V2CreatePlaylist(on:(String)->Unit){var n by remember{mutableStateOf("")};AlertDialog(onDismissRequest={on("")},title={Text("پلی‌لیست جدید")},text={OutlinedTextField(n,{n=it},label={Text("نام")},singleLine=true)},confirmButton={TextButton(onClick={if(n.isNotBlank())on(n)}){Text("ساخت")}})}

private fun loadV2Tracks(context:Context):List<V2Track>{val out=mutableListOf<V2Track>();val uri=android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;val p=arrayOf(android.provider.MediaStore.Audio.Media._ID,android.provider.MediaStore.Audio.Media.TITLE,android.provider.MediaStore.Audio.Media.ARTIST,android.provider.MediaStore.Audio.Media.ALBUM,android.provider.MediaStore.Audio.Media.DURATION,android.provider.MediaStore.Audio.Media.DATA);runCatching{context.contentResolver.query(uri,p,"${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0",null,"${android.provider.MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use{c->val id=c.getColumnIndexOrThrow(p[0]);val title=c.getColumnIndexOrThrow(p[1]);val artist=c.getColumnIndexOrThrow(p[2]);val album=c.getColumnIndexOrThrow(p[3]);val dur=c.getColumnIndexOrThrow(p[4]);val data=c.getColumnIndexOrThrow(p[5]);while(c.moveToNext()){val path=c.getString(data);out+=V2Track(c.getLong(id),c.getString(title)? : "",c.getString(artist)? : "",c.getString(album)? : "",android.net.Uri.parse(path).toString(),c.getLong(dur),path.substringBeforeLast('/',"/"))}}};return out}
private fun loadIds(p:android.content.SharedPreferences,k:String)=p.getStringSet(k,emptySet())!!.mapNotNull{it.toLongOrNull()}.toSet()
private fun saveIds(p:android.content.SharedPreferences,k:String,v:Set<Long>){p.edit().putStringSet(k,v.map(Long::toString).toSet()).apply()}
private fun loadCountsV2(p:android.content.SharedPreferences)=p.getStringSet("counts",emptySet())!!.mapNotNull{it.split(":").takeIf{x->x.size==2}?.let{x->x[0].toLongOrNull()?.let{id->id to (x[1].toIntOrNull()?:0)}}}.toMap()
private fun saveCountsV2(p:android.content.SharedPreferences,v:Map<Long,Int>){p.edit().putStringSet("counts",v.map{(k,n)->"$k:$n"}.toSet()).apply()}
private fun loadPlaylistsV2(p:android.content.SharedPreferences)=p.getStringSet("playlists",emptySet())!!.mapNotNull{val x=it.split("|",limit=2);x.firstOrNull()?.takeIf{n->n.isNotBlank()}?.let{n->V2Playlist(n,x.getOrNull(1)?.split(',')?.mapNotNull(String::toLongOrNull)?:emptyList())}}
private fun savePlaylistsV2(p:android.content.SharedPreferences,v:List<V2Playlist>){p.edit().putStringSet("playlists",v.map{it.name+"|"+it.ids.joinToString(",")}.toSet()).apply()}
private fun formatMs(ms:Long)="%d:%02d".format(ms/60000,(ms/1000)%60)
