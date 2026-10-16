package com.tunetag.player.ui

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunetag.player.data.Library
import com.tunetag.player.data.Song
import com.tunetag.player.data.UserState
import com.tunetag.player.playback.PlayerState
import kotlinx.coroutines.delay
import kotlin.math.atan2

// ------------------------------------------------------------------ iPod Classic mode
// A click-wheel iPod: rotate the wheel to scroll (with a tick sound + haptic), centre = select, MENU = back,
// right/left = next/previous (hold to seek), bottom = play/pause. On Now Playing the wheel sets the volume;
// press the centre to switch the wheel to scrubbing.

private class IpodEntry(val spec: String) {
    var sel by mutableIntStateOf(0)
}

private class IpodItem(
    val label: String,
    val chevron: Boolean = true,
    val value: String? = null,
    val onClick: () -> Unit,
)

private val LcdBg = Color(0xFFF3F3EF)
private val BlueTop = Color(0xFF52A7F7)
private val BlueBottom = Color(0xFF1463BE)
private const val WHEEL_STEP = 20f

private fun ipodTitle(spec: String, lib: Library, user: UserState): String = when {
    spec == "main" -> "iPod"
    spec == "music" -> "Music"
    spec == "playlists" -> "Playlists"
    spec == "artists" -> "Artists"
    spec == "albums" -> "Albums"
    spec == "songs" -> "Songs"
    spec == "genres" -> "Genres"
    spec == "recent" -> "Recently Added"
    spec == "settings" -> "Settings"
    spec.startsWith("artist:") -> spec.removePrefix("artist:")
    spec.startsWith("genre:") -> spec.removePrefix("genre:")
    spec.startsWith("album:") -> lib.albums.firstOrNull { it.id.toString() == spec.removePrefix("album:") }?.name ?: "Album"
    spec.startsWith("playlist:") -> user.playlists.firstOrNull { it.id == spec.removePrefix("playlist:") }?.name ?: "Playlist"
    else -> "iPod"
}

private fun ipodItems(
    spec: String, lib: Library, user: UserState, sorted: List<Song>, vm: AppViewModel,
    push: (String) -> Unit, playAndShow: (List<Song>, Int) -> Unit,
): List<IpodItem> {
    fun songItems(list: List<Song>) = list.mapIndexed { i, s -> IpodItem(s.title, false) { playAndShow(list, i) } }
    val st = user.settings
    return when {
        spec == "main" -> listOf(
            IpodItem("Music") { push("music") },
            IpodItem("Shuffle Songs", false) { vm.player.shuffleAll(lib.songs); push("now") },
            IpodItem("Now Playing") { push("now") },
            IpodItem("Settings") { push("settings") },
        )
        spec == "music" -> listOf(
            "Playlists" to "playlists", "Artists" to "artists", "Albums" to "albums",
            "Songs" to "songs", "Genres" to "genres", "Recently Added" to "recent"
        ).map { (label, target) -> IpodItem(label) { push(target) } }
        spec == "playlists" -> user.playlists.map { p -> IpodItem(p.name) { push("playlist:${p.id}") } }
        spec == "artists" -> lib.artists.map { a -> IpodItem(a.name) { push("artist:${a.name}") } }
        spec == "albums" -> lib.albums.map { a -> IpodItem(a.name) { push("album:${a.id}") } }
        spec == "genres" -> lib.genres.map { g -> IpodItem(g.name) { push("genre:${g.name}") } }
        spec == "songs" -> songItems(sorted)
        spec == "recent" -> songItems(lib.songs.sortedByDescending { it.dateAdded }.take(100))
        spec == "settings" -> listOf(
            IpodItem("Click Sound", false, if (st.ipodSound) "On" else "Off") { vm.updateSettings { it.copy(ipodSound = !it.ipodSound) } },
            IpodItem("Haptics", false, if (st.ipodHaptics) "On" else "Off") { vm.updateSettings { it.copy(ipodHaptics = !it.ipodHaptics) } },
            IpodItem("Exit iPod Mode", false) { vm.updateSettings { it.copy(playerStyle = 0) } },
        )
        spec.startsWith("artist:") -> songItems(
            lib.songs.filter { it.artist == spec.removePrefix("artist:") }
                .sortedWith(compareBy<Song>({ it.album }, { it.discNo }, { it.trackNo }))
        )
        spec.startsWith("album:") -> songItems(lib.albums.firstOrNull { it.id.toString() == spec.removePrefix("album:") }?.songs ?: emptyList())
        spec.startsWith("genre:") -> songItems(lib.songs.filter { it.genre == spec.removePrefix("genre:") })
        spec.startsWith("playlist:") -> songItems(
            user.playlists.firstOrNull { it.id == spec.removePrefix("playlist:") }?.paths?.mapNotNull { lib.byPath[it] } ?: emptyList()
        )
        else -> emptyList()
    }
}

@Composable
fun IpodRoot(vm: AppViewModel) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val lib by vm.library.collectAsState()
    val user by vm.user.collectAsState()
    val ps by vm.playerState.collectAsState()
    val current by vm.currentSong.collectAsState()
    val sorted by vm.sortedSongs.collectAsState()
    val settings = user.settings
    val stack = remember { mutableStateListOf(IpodEntry("main")) }
    var scrub by remember { mutableStateOf(false) }
    var volTick by remember { mutableIntStateOf(0) }
    var showVol by remember { mutableStateOf(false) }
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    LaunchedEffect(Unit) { ClickSound.init(ctx) }
    LaunchedEffect(volTick) {
        if (volTick > 0) {
            showVol = true
            delay(1500)
            showVol = false
        }
    }
    BackHandler(stack.size > 1) { stack.removeAt(stack.lastIndex); scrub = false }

    val top = stack.last()
    fun push(spec: String) { stack.add(IpodEntry(spec)) }
    fun playAndShow(list: List<Song>, i: Int) { vm.player.play(list, i); push("now") }

    val items = remember(top.spec, lib, user, sorted) { ipodItems(top.spec, lib, user, sorted, vm, ::push, ::playAndShow) }
    val title = ipodTitle(top.spec, lib, user)

    fun tickFeedback() {
        if (settings.ipodSound) ClickSound.play()
        if (settings.ipodHaptics) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun pressFeedback() {
        if (settings.ipodSound) ClickSound.play(0.8f)
        if (settings.ipodHaptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFDFDFD), Color(0xFFE4E4E6), Color(0xFFCFCFD2))))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        // screen
        Box(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp).fillMaxWidth().aspectRatio(1.3f)
                .clip(RoundedCornerShape(10.dp)).background(Color(0xFF1E1E1E)).padding(5.dp)
                .clip(RoundedCornerShape(6.dp)).background(LcdBg)
        ) {
            if (top.spec == "now") {
                IpodNowPlaying(vm, current, ps, scrub, showVol, volTick, audio)
            } else {
                IpodList(top.spec, title, items, top.sel, ps.isPlaying)
            }
        }

        // wheel
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val d = minOf(maxWidth - 28.dp, maxHeight - 12.dp).coerceAtLeast(120.dp)
            ClickWheel(
                Modifier.size(d), ps.isPlaying,
                onTick = { dir ->
                    if (top.spec == "now") {
                        if (scrub) {
                            vm.player.seekBy(dir * 4000L)
                            tickFeedback()
                        } else {
                            audio.adjustStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0
                            )
                            volTick++
                            tickFeedback()
                        }
                    } else if (items.isNotEmpty()) {
                        val ns = (top.sel + dir).coerceIn(0, items.size - 1)
                        if (ns != top.sel) { top.sel = ns; tickFeedback() }
                    }
                },
                onCenter = {
                    pressFeedback()
                    if (top.spec == "now") scrub = !scrub else items.getOrNull(top.sel)?.let { it.onClick() }
                },
                onMenu = { pressFeedback(); if (stack.size > 1) { scrub = false; stack.removeAt(stack.lastIndex) } },
                onPlay = { pressFeedback(); vm.player.togglePlay() },
                onNext = { pressFeedback(); vm.player.next() },
                onPrev = { pressFeedback(); vm.player.previous() },
                onHold = { dir -> vm.player.seekBy(dir * 5000L) },
            )
        }
    }
}

@Composable
private fun IpodHeader(title: String, playing: Boolean) {
    Box(
        Modifier.fillMaxWidth().height(24.dp)
            .background(Brush.verticalGradient(listOf(Color(0xFFF9F9F9), Color(0xFFBFC0C4)))),
        contentAlignment = Alignment.Center
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 36.dp))
        if (playing) {
            Icon(Icons.Rounded.PlayArrow, null, tint = Color(0xFF333333), modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(15.dp))
        }
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 8.dp).size(width = 20.dp, height = 9.dp)
                .border(1.dp, Color(0xFF555555), RoundedCornerShape(2.dp)).padding(1.5.dp)
                .background(Color(0xFF4CAF50))
        )
    }
}

@Composable
private fun IpodList(spec: String, title: String, items: List<IpodItem>, sel: Int, playing: Boolean) {
    val listState = remember(spec) { LazyListState() }
    LaunchedEffect(sel, items.size) {
        if (items.isNotEmpty()) listState.scrollToItem((sel - 3).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize()) {
        IpodHeader(title, playing)
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No Items", fontSize = 14.sp, color = Color.Gray) }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(items) { i, item ->
                    val on = i == sel
                    Row(
                        Modifier.fillMaxWidth().height(26.dp)
                            .background(if (on) Brush.verticalGradient(listOf(BlueTop, BlueBottom)) else Brush.verticalGradient(listOf(LcdBg, LcdBg)))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.label, Modifier.weight(1f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (on) Color.White else Color.Black, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                        if (item.value != null) {
                            Text(item.value, fontSize = 14.sp, color = if (on) Color.White else Color(0xFF666666))
                        }
                        if (item.chevron) {
                            Icon(Icons.Rounded.ChevronRight, null, tint = if (on) Color.White else Color(0xFF444444), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IpodNowPlaying(
    vm: AppViewModel, song: Song?, ps: PlayerState, scrub: Boolean, showVol: Boolean, volTick: Int, audio: AudioManager,
) {
    Column(Modifier.fillMaxSize()) {
        IpodHeader("Now Playing", ps.isPlaying)
        if (song == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No Song Playing", fontSize = 14.sp, color = Color.Gray) }
        } else {
            Row(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Artwork(song, Modifier.size(88.dp), px = 264, shape = RoundedCornerShape(2.dp), exact = true)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(song.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, fontSize = 13.sp, color = Color(0xFF333333), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.album, fontSize = 13.sp, color = Color(0xFF777777), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("${ps.index + 1} of ${ps.queue.size}", Modifier.padding(horizontal = 10.dp), fontSize = 11.sp, color = Color(0xFF666666))
            IpodProgress(vm, ps.durationMs, scrub, showVol, volTick, audio)
        }
    }
}

@Composable
private fun IpodProgress(vm: AppViewModel, durationMs: Long, scrub: Boolean, showVol: Boolean, volTick: Int, audio: AudioManager) {
    val pos by vm.player.position.collectAsState()
    val dur = durationMs.coerceAtLeast(1L)
    val frac = if (showVol) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    } else (pos.toFloat() / dur).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val h = size.height
            drawRoundRect(Color(0xFFBDBDC2), Offset.Zero, Size(size.width, h), CornerRadius(h / 2f))
            drawRoundRect(Brush.verticalGradient(listOf(BlueTop, BlueBottom)), Offset.Zero, Size(size.width * frac, h), CornerRadius(h / 2f))
            if (scrub && !showVol) drawCircle(Color.White, h * 0.9f, Offset(size.width * frac, h / 2f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showVol) {
                Icon(Icons.Rounded.VolumeDown, null, tint = Color(0xFF444444), modifier = Modifier.size(14.dp))
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.VolumeUp, null, tint = Color(0xFF444444), modifier = Modifier.size(14.dp))
            } else {
                Text(fmtTime(pos), fontSize = 11.sp, color = Color(0xFF444444))
                Text(if (scrub) "Scrub" else "", Modifier.weight(1f), fontSize = 11.sp, color = Color(0xFF1463BE), textAlign = TextAlign.Center)
                Text("-" + fmtTime((dur - pos).coerceAtLeast(0L)), fontSize = 11.sp, color = Color(0xFF444444))
            }
        }
    }
}

private fun angleOf(p: Offset, c: Offset): Float = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()

/** The touch-sensitive wheel: rotation produces ticks, taps on the ring/centre act as the buttons. */
@Composable
private fun ClickWheel(
    modifier: Modifier,
    playing: Boolean,
    onTick: (Int) -> Unit,
    onCenter: () -> Unit,
    onMenu: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onHold: (Int) -> Unit,
) {
    val tick = rememberUpdatedState(onTick)
    val center = rememberUpdatedState(onCenter)
    val menu = rememberUpdatedState(onMenu)
    val play = rememberUpdatedState(onPlay)
    val next = rememberUpdatedState(onNext)
    val prev = rememberUpdatedState(onPrev)
    val hold = rememberUpdatedState(onHold)

    BoxWithConstraints(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val c = Offset(size.width / 2f, size.height / 2f)
                val rad = size.width / 2f
                val inCenter = (down.position - c).getDistance() < rad * 0.36f
                val a0 = angleOf(down.position, c)
                // 0 centre, 1 menu (top), 2 next (right), 3 play (bottom), 4 previous (left)
                val zone = if (inCenter) 0 else when {
                    a0 > -135f && a0 <= -45f -> 1
                    a0 > -45f && a0 <= 45f -> 2
                    a0 > 45f && a0 <= 135f -> 3
                    else -> 4
                }
                var last = a0
                var acc = 0f
                var moved = false
                var held = false
                val t0 = System.currentTimeMillis()
                var lastSeek = t0
                down.consume()
                while (true) {
                    val ev = withTimeoutOrNull(120) { awaitPointerEvent() }
                    val now = System.currentTimeMillis()
                    if (ev != null) {
                        val p = ev.changes.firstOrNull() ?: break
                        p.consume()
                        if (!p.pressed) break
                        if (!inCenter) {
                            val a = angleOf(p.position, c)
                            var d = a - last
                            if (d > 180f) d -= 360f
                            if (d < -180f) d += 360f
                            last = a
                            acc += d
                            if ((p.position - down.position).getDistance() > viewConfiguration.touchSlop * 1.5f) moved = true
                            while (acc >= WHEEL_STEP) { moved = true; tick.value(1); acc -= WHEEL_STEP }
                            while (acc <= -WHEEL_STEP) { moved = true; tick.value(-1); acc += WHEEL_STEP }
                        }
                    }
                    if (!moved && (zone == 2 || zone == 4) && now - t0 > 450) {
                        held = true
                        if (now - lastSeek >= 200) {
                            hold.value(if (zone == 2) 1 else -1)
                            lastSeek = now
                        }
                    }
                }
                if (!moved && !held) {
                    when (zone) {
                        0 -> center.value()
                        1 -> menu.value()
                        2 -> next.value()
                        3 -> play.value()
                        else -> prev.value()
                    }
                }
            }
        }
    ) {
        val r = maxWidth / 2
        Canvas(Modifier.fillMaxSize()) {
            val rr = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFBFBFB), Color(0xFFD6D6DA)), c, rr), rr, c)
            drawCircle(Color(0x33000000), rr, c, style = Stroke(1.5.dp.toPx()))
            val cr = rr * 0.36f
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE2E2E6)), c, cr), cr, c)
            drawCircle(Color(0x26000000), cr, c, style = Stroke(1.dp.toPx()))
        }
        val label = Color(0xFF8A8A90)
        Text("MENU", Modifier.align(Alignment.TopCenter).padding(top = r * 0.3f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = label)
        Icon(Icons.Rounded.FastRewind, null, tint = label, modifier = Modifier.align(Alignment.CenterStart).padding(start = r * 0.2f).size(24.dp))
        Icon(Icons.Rounded.FastForward, null, tint = label, modifier = Modifier.align(Alignment.CenterEnd).padding(end = r * 0.2f).size(24.dp))
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = r * 0.3f)) {
            Icon(Icons.Rounded.PlayArrow, null, tint = label, modifier = Modifier.size(22.dp))
            Icon(Icons.Rounded.Pause, null, tint = label, modifier = Modifier.size(22.dp))
        }
    }
}
