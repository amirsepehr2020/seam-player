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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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

private val Purple = Color(0xFFB66CFF)
private val Green = Color(0xFF63F29A)
private val Bg = Color(0xFF08070D)
private val Card = Color(0xFF15121D)
private val Muted = Color(0xFF958C9F)

data class Track(val id: Long,val title: String,val artist: String,val album: String,val duration: Long,val uri: String,val folder: String)
enum class Screen { HOME, LIBRARY, SEARCH, SETTINGS }
enum class Lib { SONGS, ALBUMS, ARTISTS, FOLDERS, FAVORITES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { SeamTheme { App(this) } } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun App(context: Context) {
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var current by remember { mutableStateOf<Long?>(null) }
    var playing by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var lib by remember { mutableStateOf(Lib.SONGS) }
    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(readFavorites(context)) }
    var recent by remember { mutableStateOf(readRecent(context)) }
    var nowPlaying by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var timerOpen by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Track?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    var speed by remember { mutableStateOf(1f) }
    var timerMinutes by remember { mutableStateOf(0) }

    fun save() { writeFavorites(context,favorites); writeRecent(context,recent) }
    fun like(id: Long) { favorites = if (id in favorites) favorites-id else favorites+id; save() }
    fun play(t: Track) {
        val c=controller ?: return
        if(c.mediaItemCount!=tracks.size) c.setMediaItems(tracks.map{MediaItem.fromUri(it.uri)})
        val i=tracks.indexOfFirst{it.id==t.id}; if(i<0)return
        c.seekToDefaultPosition(i); c.prepare(); c.setPlaybackSpeed(speed); c.play(); current=t.id; playing=true; nowPlaying=true
        recent=(listOf(t.id)+recent.filterNot{it==t.id}).take(20); save()
    }
    val permission=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)tracks=loadTracks(context)}
    LaunchedEffect(Unit){ if(ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED)tracks=loadTracks(context) else launcher.launch(permission); val token=SessionToken(context,ComponentName(context,"ir.seam.player.playback.PlaybackService")); val f=MediaController.Builder(context,token).buildAsync(); f.addListener({controller=runCatching{f.get()}.getOrNull()},ContextCompat.getMainExecutor(context)) }
    DisposableEffect(controller){val c=controller?:return@DisposableEffect onDispose{}; val l=object:Player.Listener{override fun onIsPlayingChanged(v:Boolean){playing=v};override fun onMediaItemTransition(item:MediaItem?,reason:Int){current=tracks.firstOrNull{it.uri==item?.localConfiguration?.uri.toString()}?.id}};c.addListener(l);onDispose{c.removeListener(l);c.release()}}
    LaunchedEffect(controller,playing){while(playing){position=controller?.currentPosition?.coerceAtLeast(0)?:0;duration=controller?.duration?.coerceAtLeast(0)?:0;delay(350)}}
    LaunchedEffect(timerMinutes,playing){if(timerMinutes>0&&playing){delay(timerMinutes*60000L);controller?.pause();timerMinutes=0}}
    val currentTrack=tracks.firstOrNull{it.id==current}
    val filtered=tracks.filter{(query.isBlank()||it.title.contains(query,true)||it.artist.contains(query,true)||it.album.contains(query,true))&&(lib!=Lib.FAVORITES||it.id in favorites)}
    Scaffold(containerColor=Bg,bottomBar={if(!nowPlaying)BottomBar(screen){screen=it}}){p->Box(Modifier.fillMaxSize().padding(p)){Crossfade(screen,label="screen"){s->when(s){Screen.HOME->Home(tracks,recent,favorites,currentTrack,playing,::play,::like,{screen=Screen.SEARCH},{nowPlaying=true});Screen.LIBRARY->Library(filtered,lib,favorites,current,{lib=it},{query=it},::play,::like,{info=it});Screen.SEARCH->Search(tracks,favorites,current,query,{query=it},::play,::like,{info=it},{screen=Screen.HOME});Screen.SETTINGS->Settings(shuffle,{shuffle=!shuffle;controller?.shuffleModeEnabled=shuffle},repeat,{repeat=nextRepeat(repeat);controller?.repeatMode=repeat},speed,{speed=nextSpeed(speed);controller?.setPlaybackSpeed(speed)},{timerOpen=true})}};if(currentTrack!=null&&!nowPlaying)Mini(currentTrack,playing,currentTrack.id in favorites,{nowPlaying=true},{if(playing)controller?.pause()else controller?.play()},{controller?.seekToNextMediaItem()},{like(currentTrack.id)});if(nowPlaying&&currentTrack!=null)NowPlaying(currentTrack,playing,currentTrack.id in favorites,position,duration,shuffle,repeat,speed,{nowPlaying=false},{if(playing)controller?.pause()else controller?.play()},{controller?.seekToPreviousMediaItem()},{controller?.seekToNextMediaItem()},{controller?.seekTo(it)},{like(currentTrack.id)},{shuffle=!shuffle;controller?.shuffleModeEnabled=shuffle},{repeat=nextRepeat(repeat);controller?.repeatMode=repeat},{queueOpen=true},{timerOpen=true},{info=currentTrack},{speed=nextSpeed(speed);controller?.setPlaybackSpeed(speed)})}}}
    if(queueOpen)ModalBottomSheet(onDismissRequest={queueOpen=false},containerColor=Card){Column(Modifier.fillMaxWidth().height(540.dp).padding(20.dp)){Text("صف پخش",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text("${tracks.size} آهنگ محلی",color=Green);LazyColumn{items(tracks,key={it.id}){t->TrackRow(t,t.id==current,t.id in favorites,::play,::like,{info=it})}}}}
    if(timerOpen)AlertDialog(onDismissRequest={timerOpen=false},title={Text("تایمر خواب")},text={Column{listOf(15,30,45,60).forEach{m->TextButton(onClick={timerMinutes=m;timerOpen=false}){Text("${m} دقیقه")}};TextButton(onClick={timerOpen=false}){Text("لغو")}}},confirmButton={})
    info?.let{t->AlertDialog(onDismissRequest={info=null},title={Text("اطلاعات آهنگ")},text={Column{Text(t.title,fontWeight=FontWeight.Bold);Text("هنرمند: ${t.artist}");Text("آلبوم: ${t.album}");Text("مدت: ${fmt(t.duration)}");Text("پوشه: ${t.folder}")}},confirmButton={TextButton(onClick={info=null}){Text("بستن")}})}
}

@Composable private fun BottomBar(s:Screen,on:(Screen)->Unit){Surface(color=Color(0xF00F0D14)){Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),horizontalArrangement=Arrangement.SpaceAround){Nav(Icons.Rounded.Home,"خانه",s==Screen.HOME){on(Screen.HOME)};Nav(Icons.Rounded.LibraryMusic,"کتابخانه",s==Screen.LIBRARY){on(Screen.LIBRARY)};Nav(Icons.Rounded.Search,"جستجو",s==Screen.SEARCH){on(Screen.SEARCH)};Nav(Icons.Rounded.Settings,"تنظیمات",s==Screen.SETTINGS){on(Screen.SETTINGS)}}}}
@Composable private fun Nav(i:androidx.compose.ui.graphics.vector.ImageVector,t:String,sel:Boolean,on:()->Unit){Column(Modifier.clickable(onClick=on).padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(i,null,tint=if(sel)Green else Muted);Text(t,color=if(sel)Green else Muted,style=MaterialTheme.typography.labelSmall)}}

@Composable private fun Home(tracks:List<Track>,recent:List<Long>,fav:Set<Long>,current:Track?,playing:Boolean,play:(Track)->Unit,like:(Long)->Unit,search:()->Unit,open:()->Unit){val rt=recent.mapNotNull{id->tracks.firstOrNull{it.id==id}};LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=150.dp)){item{Header("خانه",search)};item{Hero(tracks.size,current,playing,open)};if(rt.isNotEmpty())item{Title("اخیراً پخش‌شده")};item{Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){rt.take(8).forEach{AlbumCard(it,it.id in fav,play,like)}}};item{Title("همه آهنگ‌ها")};items(tracks.take(80),key={it.id}){TrackRow(it,it.id==current?.id,it.id in fav,play,like)}}}
@Composable private fun Header(t:String,search:()->Unit){Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(t,color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold);Text("موسیقی تو، جریان تو",color=Muted)};IconButton(onClick=search){Icon(Icons.Rounded.Search,null,tint=Purple)}}}
@Composable private fun Hero(count:Int,current:Track?,playing:Boolean,open:()->Unit){val pulse by rememberInfiniteTransition(label="hero").animateFloat(1f,1.035f,infiniteRepeatable(tween(1800),RepeatMode.Reverse),label="pulse");Box(Modifier.fillMaxWidth().padding(16.dp).height(205.dp).scale(pulse).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(Color(0xFF3A1462),Color(0xFF082918)))).clickable(onClick=open)){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.Center){Text("SEAM PLAYER",color=Green,fontWeight=FontWeight.Black);Spacer(Modifier.height(8.dp));Text(current?.title?:"آماده‌ای برای موسیقی؟",color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(current?.artist?:"$count آهنگ روی دستگاه",color=Color(0xFFD6CADE));Spacer(Modifier.height(16.dp));Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(48.dp).clip(CircleShape).background(Green),contentAlignment=Alignment.Center){Icon(if(playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null,tint=Bg)};Spacer(Modifier.width(10.dp));Text(if(playing)"در حال پخش" else "شروع پخش",color=Color.White,fontWeight=FontWeight.Bold)}}}}
@Composable private fun Title(t:String){Text(t,color=Color.White,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.padding(20.dp,14.dp))}
@Composable private fun AlbumCard(t:Track,liked:Boolean,play:(Track)->Unit,like:(Long)->Unit){Column(Modifier.width(132.dp)){Box(Modifier.clickable{play(t)}){AlbumArt(t.uri,132.dp,true)};Text(t.title,color=Color.White,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t.artist,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis);IconButton(onClick={like(t.id)},modifier=Modifier.size(28.dp)){Icon(if(liked)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,null,tint=if(liked)Green:Muted)}}}

@Composable private fun Library(list:List<Track>,tab:Lib,fav:Set<Long>,current:Long?,setTab:(Lib)->Unit,setQuery:(String)->Unit,play:(Track)->Unit,like:(Long)->Unit,info:(Track)->Unit){Column(Modifier.fillMaxSize()){Header("کتابخانه",{});OutlinedTextField(value="",onValueChange=setQuery,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),label={Text("جستجو در موسیقی")},singleLine=true);Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Lib.values().forEach{FilterChip(selected=tab==it,onClick={setTab(it)},label={Text(it.fa())})}};LazyColumn(contentPadding=PaddingValues(bottom=140.dp)){items(list,key={it.id}){TrackRow(it,it.id==current,it.id in fav,play,like,info)}}}}
@Composable private fun Search(tracks:List<Track>,fav:Set<Long>,current:Long?,q:String,set:(String)->Unit,play:(Track)->Unit,like:(Long)->Unit,info:(Track)->Unit,back:()->Unit){Column(Modifier.fillMaxSize()){TopAppBar(title={Text("جستجو")},navigationIcon={IconButton(onClick=back){Icon(Icons.Rounded.ArrowBack,null)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Bg,titleContentColor=Color.White));OutlinedTextField(q,set,Modifier.fillMaxWidth().padding(16.dp),label={Text("نام آهنگ، هنرمند یا آلبوم")},singleLine=true);LazyColumn{items(tracks.filter{q.isBlank()||it.title.contains(q,true)||it.artist.contains(q,true)||it.album.contains(q,true)},key={it.id}){TrackRow(it,it.id==current,it.id in fav,play,like,info)}}}}
@Composable private fun Settings(shuffle:Boolean,toggleShuffle:()->Unit,repeat:Int,toggleRepeat:()->Unit,speed:Float,setSpeed:(Float)->Unit,timer:()->Unit){LazyColumn(contentPadding=PaddingValues(bottom=140.dp)){item{Header("تنظیمات",{})};item{Title("پخش")};item{Setting(Icons.Rounded.Shuffle,"پخش تصادفی",if(shuffle)"روشن" else "خاموش",toggleShuffle)};item{Setting(Icons.Rounded.Repeat,"تکرار",when(repeat){Player.REPEAT_MODE_ALL->"همه";Player.REPEAT_MODE_ONE->"یک آهنگ";else->"خاموش"},toggleRepeat)};item{Setting(Icons.Rounded.Timer,"تایمر خواب","انتخاب زمان",timer)};item{Setting(Icons.Rounded.Speed,"سرعت پخش","${speed}x",{setSpeed(nextSpeed(speed))})};item{Title("صدا")};item{Setting(Icons.Rounded.Tune,"اکولایزر","تنظیمات صدای سیستم",{})};item{Setting(Icons.Rounded.AutoAwesome,"ویژوالایزر","افکت بصری",{})};item{Title("درباره")};item{Setting(Icons.Rounded.Info,"درباره SEAM Player","پخش‌کننده موسیقی محلی",{})}}}
@Composable private fun Setting(i:androidx.compose.ui.graphics.vector.ImageVector,t:String,s:String,on:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=on).padding(20.dp),verticalAlignment=Alignment.CenterVertically){Icon(i,null,tint=Purple);Spacer(Modifier.width(16.dp));Column{Text(t,color=Color.White,fontWeight=FontWeight.SemiBold);Text(s,color=Muted,style=MaterialTheme.typography.bodySmall)}}}

@Composable private fun TrackRow(t:Track,active:Boolean,liked:Boolean,play:(Track)->Unit,like:(Long)->Unit,info:(Track)->Unit={}){Row(Modifier.fillMaxWidth().animateContentSize().clickable{play(t)}.padding(18.dp,8.dp),verticalAlignment=Alignment.CenterVertically){AlbumArt(t.uri,56.dp,active);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(t.title,color=if(active)Green else Color.White,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t.artist,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)};IconButton(onClick={like(t.id)}){Icon(if(liked)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,null,tint=if(liked)Green else Muted)};IconButton(onClick={info(t)}){Icon(Icons.Rounded.MoreVert,null,tint=Muted)}}}
@Composable private fun Mini(t:Track,playing:Boolean,liked:Boolean,open:()->Unit,pause:()->Unit,next:()->Unit,like:()->Unit){Row(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xF01B1722)).clickable(onClick=open).padding(9.dp),verticalAlignment=Alignment.CenterVertically){AlbumArt(t.uri,50.dp,true);Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(t.title,color=Color.White,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t.artist,color=Muted,style=MaterialTheme.typography.bodySmall)};IconButton(onClick=like){Icon(if(liked)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,null,tint=if(liked)Green else Muted)};IconButton(onClick=pause){Icon(if(playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null,tint=Green)};IconButton(onClick=next){Icon(Icons.Rounded.SkipNext,null,tint=Purple)}}}
@Composable private fun NowPlaying(t:Track,playing:Boolean,liked:Boolean,pos:Long,dur:Long,shuffle:Boolean,repeat:Int,speed:Float,back:()->Unit,pause:()->Unit,prev:()->Unit,next:()->Unit,seek:(Long)->Unit,like:()->Unit,togShuffle:()->Unit,togRepeat:()->Unit,queue:()->Unit,timer:()->Unit,info:()->Unit,changeSpeed:()->Unit){val glow by rememberInfiniteTransition(label="glow").animateFloat(.88f,1.08f,infiniteRepeatable(tween(1500),RepeatMode.Reverse),label="g");Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(20.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=back){Icon(Icons.Rounded.ArrowBack,null,tint=Color.White)};Text("در حال پخش",color=Color.White,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));IconButton(onClick=info){Icon(Icons.Rounded.Info,null,tint=Muted)}};Box(Modifier.fillMaxWidth().height(330.dp),contentAlignment=Alignment.Center){Box(Modifier.size((300*glow).dp).clip(RoundedCornerShape(36.dp)).background(Brush.radialGradient(listOf(Purple.copy(.25f),Green.copy(.08f),Color.Transparent))));AlbumArt(t.uri,285.dp,true)};Spacer(Modifier.height(22.dp));Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(t.title,color=Color.White,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(t.artist,color=Muted,style=MaterialTheme.typography.titleMedium)};IconButton(onClick=like){Icon(if(liked)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,null,tint=if(liked)Green else Color.White)}};Slider(value=if(dur>0)pos.coerceIn(0,dur).toFloat()/dur else 0f,onValueChange={seek((it*dur).toLong())});Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(fmt(pos),color=Muted);Text(fmt(dur),color=Muted)};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){Control(Icons.Rounded.Shuffle,shuffle,togShuffle);Control(Icons.Rounded.SkipPrevious,false,prev,38);Box(Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Purple,Green))).clickable(onClick=pause),contentAlignment=Alignment.Center){Icon(if(playing)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,null,tint=Bg,modifier=Modifier.size(38.dp))};Control(Icons.Rounded.SkipNext,false,next,38);Control(Icons.Rounded.Repeat,repeat!=Player.REPEAT_MODE_OFF,togRepeat)};Spacer(Modifier.height(18.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){Tool(Icons.Rounded.QueueMusic,"صف پخش",queue);Tool(Icons.Rounded.Timer,"تایمر",timer);Tool(Icons.Rounded.Tune,"اکولایزر",{});Tool(Icons.Rounded.AutoAwesome,"ویژوالایزر",{});Tool(Icons.Rounded.Info,"اطلاعات",info)};TextButton(onClick=changeSpeed,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("سرعت ${speed}x",color=Green)}}}
@Composable private fun Control(i:androidx.compose.ui.graphics.vector.ImageVector,active:Boolean,on:()->Unit,size:Int=30){IconButton(onClick=on){Icon(i,null,tint=if(active)Green else Color.White,modifier=Modifier.size(size.dp))}}
@Composable private fun Tool(i:androidx.compose.ui.graphics.vector.ImageVector,t:String,on:()->Unit){Column(Modifier.clickable(onClick=on),horizontalAlignment=Alignment.CenterHorizontally){Icon(i,null,tint=Purple);Text(t,color=Muted,style=MaterialTheme.typography.labelSmall)}}
@Composable private fun AlbumArt(uri:String,size:androidx.compose.ui.unit.Dp,active:Boolean){val art=rememberArt(uri);Box(Modifier.size(size).clip(RoundedCornerShape(18.dp)).background(if(active)Purple.copy(.18f) else Color(0xFF211C28)),contentAlignment=Alignment.Center){if(art!=null)Image(art,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)else Icon(Icons.Rounded.Album,null,tint=if(active)Green else Purple,modifier=Modifier.size(size/2))}}
@Composable private fun rememberArt(uri:String):ImageBitmap?{var a by remember(uri){mutableStateOf<ImageBitmap?>(null)};LaunchedEffect(uri){a=withContext(Dispatchers.IO){runCatching{MediaMetadataRetriever().use{r->r.setDataSource(uri);r.embeddedPicture?.let{b->BitmapFactory.decodeByteArray(b,0,b.size)?.asImageBitmap()}}}.getOrNull()}};return a}

private fun loadTracks(c:Context):List<Track>{val out=mutableListOf<Track>();val p=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.DATA);c.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,p,"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,"${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use{q->val id=q.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);val ti=q.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);val ar=q.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);val al=q.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);val du=q.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);val da=q.getColumnIndex(MediaStore.Audio.Media.DATA);while(q.moveToNext()){val path=if(da>=0)q.getString(da).orEmpty() else "";val x=q.getLong(id);out+=Track(x,q.getString(ti).orEmpty().ifBlank{"بدون عنوان"},q.getString(ar).orEmpty().ifBlank{"هنرمند ناشناس"},q.getString(al).orEmpty().ifBlank{"آلبوم ناشناس"},q.getLong(du),"${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$x",path.substringBeforeLast('/').substringAfterLast('/',"موسیقی"))}};return out}
private fun fmt(ms:Long)="%d:%02d".format(ms/60000,(ms/1000)%60)
private fun nextRepeat(r:Int)=when(r){Player.REPEAT_MODE_OFF->Player.REPEAT_MODE_ALL;Player.REPEAT_MODE_ALL->Player.REPEAT_MODE_ONE;else->Player.REPEAT_MODE_OFF}
private fun nextSpeed(s:Float)=if(s>=2f).75f else s+.25f
private fun readFavorites(c:Context)=c.getSharedPreferences("seam",0).getStringSet("fav",emptySet())!!.mapNotNull{it.toLongOrNull()}.toSet()
private fun readRecent(c:Context)=c.getSharedPreferences("seam",0).getString("recent","")!!.split(',').mapNotNull{it.toLongOrNull()}
private fun writeFavorites(c:Context,v:Set<Long>){c.getSharedPreferences("seam",0).edit().putStringSet("fav",v.map{it.toString()}.toSet()).apply()}
private fun writeRecent(c:Context,v:List<Long>){c.getSharedPreferences("seam",0).edit().putString("recent",v.joinToString(",")).apply()}
private fun Lib.fa()=when(this){Lib.SONGS->"آهنگ‌ها";Lib.ALBUMS->"آلبوم‌ها";Lib.ARTISTS->"هنرمندان";Lib.FOLDERS->"پوشه‌ها";Lib.FAVORITES->"علاقه‌مندی‌ها"}
