package com.tunetag.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunetag.player.data.Library
import com.tunetag.player.data.Song
import kotlinx.coroutines.launch
import kotlin.math.abs

sealed interface Route {
    data object Home : Route
    data class Songs(val kind: String, val key: String) : Route
    data object Search : Route
    data class TagEdit(val songId: Long, val autoStart: Boolean = false) : Route
    data class BatchTag(val songIds: List<Long>) : Route
    data object Settings : Route
    data object Stats : Route
}

private fun Route.showMini(): Boolean = this !is Route.TagEdit && this !is Route.BatchTag

class Nav {
    val stack = mutableStateListOf<Route>(Route.Home)
    val top: Route get() = stack.last()
    fun push(r: Route) { stack.add(r) }
    fun pop(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        return false
    }
}

private data class Frame(val route: Route, val depth: Int)

internal val MINI_H = 64.dp

@Composable
fun AppScaffold(vm: AppViewModel) {
    val nav = remember { Nav() }
    val current by vm.currentSong.collectAsState()
    val user by vm.user.collectAsState()
    val lib by vm.library.collectAsState()
    val expand = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val songMenu = remember { mutableStateOf<Song?>(null) }
    val playlistTarget = remember { mutableStateOf<List<Song>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val home = HomeStates(
        albumsList = rememberLazyListState(), albumsGrid = rememberLazyGridState(),
        recent = rememberLazyListState(), songs = rememberLazyListState(), artists = rememberLazyListState(),
        genres = rememberLazyListState(), folders = rememberLazyListState(), playlists = rememberLazyListState(),
        pager = rememberPagerState { SECTIONS.size }
    )
    val showFull by remember { derivedStateOf { expand.value > 0.001f } }
    val fullOpen by remember { derivedStateOf { expand.value > 0.5f } }

    BackHandler(fullOpen) { scope.launch { expand.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 500f)) } }
    BackHandler(!fullOpen && nav.stack.size > 1) { nav.pop() }

    // Starting playback from any list opens the full Now Playing page.
    val actions = remember(vm, nav, expand, scope) {
        val open: () -> Unit = { scope.launch { expand.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 450f)) } }
        SongActions(
            play = { list, i -> vm.player.play(list, i); open() },
            playInOrder = { list -> vm.player.play(list, 0, false); open() },
            shuffle = { list -> vm.player.shuffleAll(list); open() },
            more = { s -> songMenu.value = s },
            enqueue = { list -> vm.player.enqueue(list) },
            addToPlaylist = { list -> playlistTarget.value = list },
            autoTag = { list ->
                if (list.size == 1) nav.push(Route.TagEdit(list[0].id, true)) else nav.push(Route.BatchTag(list.map { it.id }))
            },
            push = { r -> nav.push(r) },
        )
    }

    val route = nav.top
    val hasMini = current != null && route.showMini()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPad: Dp = navInset + 8.dp + (if (hasMini) MINI_H else 0.dp)

    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val heightPx = with(density) { maxHeight.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        val miniTotalPx = with(density) { MINI_H.toPx() + navInset.toPx() }
        val travelPx = heightPx - miniTotalPx

        CompositionLocalProvider(LocalBottomPad provides bottomPad) {
            val frame = Frame(route, nav.stack.size)
            AnimatedContent(
                targetState = frame,
                transitionSpec = {
                    if (targetState.depth > initialState.depth) {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 8 }) togetherWith fadeOut(tween(140))
                    } else if (targetState.depth < initialState.depth) {
                        fadeIn(tween(200)) togetherWith (fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 8 })
                    } else {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(110))
                    }
                },
                label = "nav"
            ) { f ->
                RouteContent(f.route, vm, nav, actions, lib, snackbar, home)
            }
        }

        val song = current
        if (hasMini && song != null) {
            MiniPlayer(vm, song, expand, travelPx, Modifier.align(Alignment.BottomCenter))
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad))

        if (showFull && song != null) {
            NowPlayingPage(vm, song, expand, heightPx, widthPx, miniTotalPx)
        }
    }

    songMenu.value?.let { s ->
        SongMenuSheet(
            song = s,
            onDismiss = { songMenu.value = null },
            onPlayNext = { vm.player.playNext(s) },
            onQueue = { vm.player.enqueue(listOf(s)) },
            onPlaylist = { playlistTarget.value = listOf(s) },
            onEdit = { nav.push(Route.TagEdit(s.id)) },
            onAutoTag = { nav.push(Route.TagEdit(s.id, true)) },
            onAlbum = { nav.push(Route.Songs("album", s.albumId.toString())) },
            onArtist = { nav.push(Route.Songs("artist", s.artist)) },
        )
    }
    playlistTarget.value?.let { songs ->
        AddToPlaylistDialog(
            playlists = user.playlists,
            onPick = { vm.addToPlaylist(it.id, songs); playlistTarget.value = null },
            onNew = { name -> vm.createPlaylist(name, songs); playlistTarget.value = null },
            onDismiss = { playlistTarget.value = null }
        )
    }
}

@Composable
private fun RouteContent(
    route: Route, vm: AppViewModel, nav: Nav, actions: SongActions,
    lib: Library, snackbar: SnackbarHostState, home: HomeStates,
) {
    when (route) {
        Route.Home -> HomeScreen(vm, actions, home)
        is Route.Songs -> SongsScreen(vm, route, actions) { nav.pop() }
        Route.Search -> SearchScreen(vm, actions) { nav.pop() }
        is Route.TagEdit -> {
            val song = lib.byId[route.songId]
            if (song != null) TagEditorScreen(vm, song, route.autoStart, snackbar) { nav.pop() }
            else LaunchedEffect(Unit) { nav.pop() }
        }
        is Route.BatchTag -> BatchTagScreen(vm, route.songIds.mapNotNull { lib.byId[it] }) { nav.pop() }
        Route.Settings -> SettingsScreen(vm, actions) { nav.pop() }
        Route.Stats -> StatsScreen(vm) { nav.pop() }
    }
}

// ------------------------------------------------------------------ mini player (square, docked, part of the screen)
@Composable
private fun MiniPlayer(
    vm: AppViewModel, song: Song, expand: Animatable<Float, AnimationVector1D>, travelPx: Float, modifier: Modifier,
) {
    val ps by vm.playerState.collectAsState()
    val colors by vm.nowColors.collectAsState()
    val scope = rememberCoroutineScope()
    val offX = remember { Animatable(0f) }
    val offY = remember { Animatable(0f) }
    val tracker = remember { VelocityTracker() }
    var axis by remember { mutableIntStateOf(0) } // 1 horizontal, 2 up, 3 down, 4 ignored (single song)
    val bg = animateColorAsState(colors?.mid ?: Color(0xFF1B1B1F), tween(500), label = "mini")
    val swipePx = with(LocalDensity.current) { 90.dp.toPx() }
    val springSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 450f)

    fun finish() {
        val v = tracker.calculateVelocity()
        when (axis) {
            1 -> scope.launch {
                val dx = offX.value
                // swipe right-to-left = next song, left-to-right = previous song (never just a restart)
                if (dx < -swipePx || v.x < -1500f) vm.player.next()
                else if (dx > swipePx || v.x > 1500f) vm.player.previousTrack()
                offX.animateTo(0f, springSpec)
            }
            2 -> scope.launch {
                if (expand.value > 0.2f || v.y < -1200f) expand.animateTo(1f, springSpec, initialVelocity = -v.y / travelPx)
                else expand.animateTo(0f, springSpec)
            }
            3 -> scope.launch {
                if (offY.value > swipePx || v.y > 1500f) {
                    offY.animateTo(400f, tween(160))
                    vm.player.stopAndClear()
                    offY.snapTo(0f)
                } else offY.animateTo(0f, springSpec)
            }
        }
        axis = 0
    }

    Box(
        modifier.fillMaxWidth()
            .graphicsLayer {
                translationX = offX.value
                translationY = offY.value
                // the full player draws the "growing" bar itself while it is opening
                alpha = if (expand.value > 0.001f) 0f else 1f
            }
            .drawBehind { drawRect(bg.value) }
            .clickable { scope.launch { expand.animateTo(1f, springSpec) } }
            .pointerInput(Unit) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { axis = 0; total = Offset.Zero; tracker.resetTracking() },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                    onDrag = { change, d ->
                        change.consume()
                        total += d
                        tracker.addPosition(change.uptimeMillis, total)
                        if (axis == 0) {
                            axis = if (abs(total.x) > abs(total.y)) {
                                if (vm.playerState.value.queue.size > 1) 1 else 4
                            } else if (total.y < 0f) 2 else 3
                        }
                        when (axis) {
                            1 -> scope.launch { offX.snapTo(total.x) }
                            2 -> scope.launch { expand.snapTo((-total.y / travelPx).coerceIn(0f, 1f)) }
                            3 -> scope.launch { offY.snapTo(total.y.coerceAtLeast(0f)) }
                        }
                    }
                )
            }
    ) {
        Column {
            MiniRow(vm, song, ps.isPlaying, showArt = true)
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** The mini player's row. Also drawn (without artwork) inside the full player while it opens/closes. */
@Composable
internal fun MiniRow(vm: AppViewModel, song: Song, playing: Boolean, showArt: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(MINI_H).padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArt) Artwork(song, Modifier.size(48.dp), px = 144, shape = RoundedCornerShape(4.dp))
        else Spacer(Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp)
        }
        // same behaviour as the full player: restart if a few seconds in, otherwise previous song
        IconButton({ vm.player.previous() }) { Icon(Icons.Rounded.FastRewind, "Previous", tint = Color.White, modifier = Modifier.size(26.dp)) }
        MiniPlayButton(vm, playing)
        IconButton({ vm.player.next() }) { Icon(Icons.Rounded.FastForward, "Next", tint = Color.White, modifier = Modifier.size(26.dp)) }
    }
}

/** Play/pause with a circular progress ring around it (Symfonium style). Only this recomposes as time ticks. */
@Composable
private fun MiniPlayButton(vm: AppViewModel, playing: Boolean) {
    val pos by vm.player.position.collectAsState()
    val dur = vm.playerState.collectAsState().value.durationMs
    val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            val w = 2.5.dp.toPx()
            val inset = w / 2f
            val box = Size(size.width - w, size.height - w)
            drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, Offset(inset, inset), box, style = Stroke(w))
            drawArc(Color.White, -90f, 360f * frac, false, Offset(inset, inset), box, style = Stroke(w, cap = StrokeCap.Round))
        }
        IconButton({ vm.player.togglePlay() }, Modifier.size(50.dp)) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}
