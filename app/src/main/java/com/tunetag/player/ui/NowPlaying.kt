package com.tunetag.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import com.tunetag.player.data.Song
import com.tunetag.player.playback.PlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Immersive Now Playing page (Apple Music style): full-bleed artwork at the very top that melts into a blurred,
 * darkened copy of itself. It is driven by [expand] (0 = mini player, 1 = open): the mini player's bar grows
 * upward into this page and its small artwork flies up and scales into the big one (and back when collapsing).
 *
 * Gestures anywhere: swipe down = collapse, swipe up = queue, swipe right-to-left = next song,
 * left-to-right = previous song (only when more than one song is queued).
 */
@Composable
fun NowPlayingPage(
    vm: AppViewModel, song: Song, expand: Animatable<Float, AnimationVector1D>,
    heightPx: Float, widthPx: Float, miniTotalPx: Float,
) {
    val scope = rememberCoroutineScope()
    val ps by vm.playerState.collectAsState()
    val colors by vm.nowColors.collectAsState()
    val tiny by rememberTinyArt(song)
    val seekSec = vm.user.collectAsState().value.settings.seekSeconds
    val queueP = remember { Animatable(0f) }
    val queueVisible by remember { derivedStateOf { queueP.value > 0.001f } }
    val queueOpen by remember { derivedStateOf { queueP.value > 0.5f } }
    val offX = remember { Animatable(0f) }
    val tracker = remember { VelocityTracker() }
    var axis by remember { mutableIntStateOf(0) } // 1 horizontal, 2 up (queue), 3 down (collapse), 4 ignored
    val density = LocalDensity.current
    val swipePx = with(density) { 110.dp.toPx() }
    val travelPx = heightPx - miniTotalPx
    val widthDp = with(density) { widthPx.toDp() }
    val miniArtPx = with(density) { 48.dp.toPx() }
    val miniArtX = with(density) { 12.dp.toPx() }
    val miniArtY = heightPx - miniTotalPx + (with(density) { MINI_H.toPx() } - miniArtPx) / 2f
    val springSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 450f)
    val pauseScale by animateFloatAsState(if (ps.isPlaying) 1f else 0.88f, spring(dampingRatio = 0.75f, stiffness = 300f), label = "pause")

    val c1 = animateColorAsState(colors?.top ?: Color(0xFF2A2A33), tween(600), label = "c1")
    val c2 = animateColorAsState(colors?.mid ?: Color(0xFF1B1B22), tween(600), label = "c2")
    val c3 = animateColorAsState(colors?.bottom ?: Color(0xFF0D0D11), tween(600), label = "c3")
    val miniColor = animateColorAsState(colors?.mid ?: Color(0xFF1B1B1F), tween(500), label = "miniC")
    val artistColor = lerp(colors?.accent ?: Color.White, Color.White, 0.5f).copy(alpha = 0.9f)

    var showFlash by remember { mutableStateOf(false) }
    var flashDir by remember { mutableIntStateOf(1) }
    var flashTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashTick) {
        if (flashTick > 0) {
            showFlash = true
            delay(650)
            showFlash = false
        }
    }

    BackHandler(queueOpen) { scope.launch { queueP.animateTo(0f, springSpec) } }

    fun finish() {
        val v = tracker.calculateVelocity()
        when (axis) {
            1 -> scope.launch {
                val dx = offX.value
                // right-to-left = next song, left-to-right = previous song
                if (dx < -swipePx || v.x < -1400f) vm.player.next()
                else if (dx > swipePx || v.x > 1400f) vm.player.previousTrack()
                offX.animateTo(0f, springSpec)
            }
            2 -> scope.launch {
                if (queueP.value > 0.2f || v.y < -1000f) queueP.animateTo(1f, springSpec, initialVelocity = -v.y / heightPx)
                else queueP.animateTo(0f, springSpec)
            }
            3 -> scope.launch {
                if (expand.value < 0.8f || v.y > 1000f) expand.animateTo(0f, springSpec, initialVelocity = -v.y / travelPx)
                else expand.animateTo(1f, springSpec)
            }
        }
        axis = 0
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationY = (1f - expand.value) * travelPx }
            .background(Color.Black)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(c1.value, c2.value, c3.value)))
                // the mini player's colour fades out as the bar grows into the page
                val a = (1f - expand.value * 3f).coerceIn(0f, 1f)
                if (a > 0f) drawRect(miniColor.value.copy(alpha = a))
            }
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
                            2 -> scope.launch { queueP.snapTo((-total.y / heightPx).coerceIn(0f, 1f)) }
                            3 -> scope.launch { expand.snapTo((1f - total.y / travelPx).coerceIn(0f, 1f)) }
                        }
                    }
                )
            }
    ) {
        // 1. ambient background: the artwork, scaled up from 32px, darkened
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((expand.value - 0.2f) / 0.8f).coerceIn(0f, 1f) }) {
            BlurredArt(tiny, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
        }

        // 2. hero artwork: full-bleed at the top, melting into the ambient layer. Doubles as the shared element
        //    that flies between the mini player and the page.
        Box(
            Modifier.fillMaxWidth().height(widthDp)
                .pointerInput(seekSec) {
                    detectTapGestures(onDoubleTap = { o ->
                        if (o.x < size.width / 2f) { vm.player.seekBy(-seekSec * 1000L); flashDir = -1 }
                        else { vm.player.seekBy(seekSec * 1000L); flashDir = 1 }
                        flashTick++
                    })
                }
        ) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val t = expand.value
                        val w = size.width
                        val scaleT = lerp(miniArtPx / w, 1f, t)
                        val extra = 1f - (1f - pauseScale) * t
                        val s = scaleT * extra
                        val shrinkPad = (w * scaleT - w * s) / 2f
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = s
                        scaleY = s
                        translationX = lerp(miniArtX, 0f, t) + shrinkPad + offX.value * 0.35f * t
                        translationY = (lerp(miniArtY, 0f, t) - (1f - t) * travelPx) + shrinkPad
                        // 4dp corners on the mini thumbnail, square when open, rounded card when paused
                        val screenR = lerp(4.dp.toPx(), 0f, t) + ((1f - pauseScale) / 0.12f) * 18.dp.toPx() * t
                        shape = androidx.compose.foundation.shape.RoundedCornerShape((screenR / s).coerceAtLeast(0f))
                        clip = true
                    }
                    .fadeBottom(0.45f) { expand.value * ((pauseScale - 0.88f) / 0.12f) }
            ) {
                Artwork(song, Modifier.fillMaxSize(), px = 1000, shape = RectangleShape, exact = true)
            }
            AnimatedVisibility(
                visible = showFlash,
                modifier = Modifier.align(if (flashDir < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 32.dp),
                enter = fadeIn(tween(80)), exit = fadeOut(tween(250))
            ) {
                // just the symbol and the seconds - no pill
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (flashDir < 0) Icons.Rounded.FastRewind else Icons.Rounded.FastForward, null, tint = Color.White, modifier = Modifier.size(44.dp))
                    Text(
                        "${seekSec}s", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 10f))
                    )
                }
            }
        }

        // 3. text, scrubber and controls float over the same continuous background
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    alpha = ((expand.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
                    translationX = offX.value * 0.35f
                }
                .navigationBarsPadding().padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(widthDp - 40.dp))
            Text(song.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = artistColor, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Scrubber(vm, ps.durationMs)
            Spacer(Modifier.weight(0.8f))

            // Apple-style transport: double-triangle back / play-pause / forward
            Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton({ vm.player.previous() }, Modifier.size(76.dp)) {
                    Icon(Icons.Rounded.FastRewind, "Previous", tint = Color.White, modifier = Modifier.size(56.dp))
                }
                IconButton({ vm.player.togglePlay() }, Modifier.size(92.dp)) {
                    Icon(if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause",
                        tint = Color.White, modifier = Modifier.size(76.dp))
                }
                IconButton({ vm.player.next() }, Modifier.size(76.dp)) {
                    Icon(Icons.Rounded.FastForward, "Next", tint = Color.White, modifier = Modifier.size(56.dp))
                }
            }

            Spacer(Modifier.weight(0.8f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                ToggleIcon(Icons.Rounded.Shuffle, ps.shuffle) { vm.player.setShuffle(!ps.shuffle) }
                ToggleIcon(
                    if (ps.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    ps.repeat != Player.REPEAT_MODE_OFF
                ) { vm.player.cycleRepeat() }
            }
            Spacer(Modifier.height(12.dp))
        }

        // back arrow, top-left over the artwork
        Box(
            Modifier.statusBarsPadding().padding(start = 10.dp, top = 6.dp)
                .graphicsLayer { alpha = ((expand.value - 0.5f) / 0.5f).coerceIn(0f, 1f) }
                .size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.28f))
                .clickable { scope.launch { expand.animateTo(0f, springSpec) } },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, "Back", tint = Color.White, modifier = Modifier.size(30.dp))
        }

        // 4. the mini player's own row, fading out as the page opens (and back in as it collapses)
        Box(Modifier.fillMaxWidth().height(MINI_H).graphicsLayer { alpha = (1f - expand.value * 4f).coerceIn(0f, 1f) }) {
            MiniRow(vm, song, ps.isPlaying, showArt = false)
        }

        if (queueVisible) QueuePanel(vm, ps, colors, queueP, heightPx)
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
            .background(if (on) Color.White.copy(alpha = 0.26f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (on) Color.White else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
    }
}

/** Thin Apple-style scrubber. Only this composable recomposes as the position ticks. */
@Composable
private fun Scrubber(vm: AppViewModel, durationMs: Long) {
    val pos by vm.player.position.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragFrac by remember { mutableFloatStateOf(0f) }
    val dur = durationMs.coerceAtLeast(1L)
    val frac = if (dragging) dragFrac else (pos.toFloat() / dur).coerceIn(0f, 1f)
    val thick by animateFloatAsState(if (dragging) 12f else 6f, tween(120), label = "thick")
    val shownPos = if (dragging) (dragFrac * dur).toLong() else pos

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(30.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { o -> vm.player.seekTo(((o.x / size.width).coerceIn(0f, 1f) * dur).toLong()) }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragging = true; dragFrac = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { vm.player.seekTo((dragFrac * dur).toLong()); dragging = false },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val th = thick.dp.toPx()
                val top = (size.height - th) / 2f
                drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(0f, top), Size(size.width, th), CornerRadius(th / 2f))
                drawRoundRect(Color.White.copy(alpha = 0.95f), Offset(0f, top), Size(size.width * frac, th), CornerRadius(th / 2f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTime(shownPos), color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text("-" + fmtTime((dur - shownPos).coerceAtLeast(0L)), color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
        }
    }
}

/**
 * Up next: press and hold any song to lift it, then drag it anywhere in the queue. Tap to jump, X to remove.
 * Swipe down (when the list is at the top) or tap the arrow to close.
 */
@Composable
private fun QueuePanel(
    vm: AppViewModel, ps: PlayerState, colors: NowColors?,
    queueP: Animatable<Float, AnimationVector1D>, heightPx: Float,
) {
    val lib by vm.library.collectAsState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val tracker = remember { VelocityTracker() }
    val springSpec = spring<Float>(dampingRatio = 0.9f, stiffness = 500f)
    val listState = rememberLazyListState()
    val cur = ps.index
    val tint = colors?.mid ?: Color(0xFF1B1B22)

    // queue indices after the current song, in play order (a local copy, so dragging is instant)
    val upIdx = remember(ps.queue, ps.index) { if (cur < 0) emptyList() else (cur + 1 until ps.queue.size).toList() }
    val local = remember(upIdx) { mutableStateListOf<Int>().also { it.addAll(upIdx) } }
    var dragKey by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragStart by remember { mutableIntStateOf(0) }

    fun onDragMove(dy: Float) {
        dragOffset += dy
        val infos = listState.layoutInfo.visibleItemsInfo
        val me = infos.firstOrNull { it.key == dragKey } ?: return
        val center = me.offset + me.size / 2f + dragOffset
        val other = infos.firstOrNull {
            it.key is Int && it.key != dragKey && center >= it.offset && center <= it.offset + it.size
        } ?: return
        val a = local.indexOf(dragKey)
        val b = local.indexOf(other.key as Int)
        if (a >= 0 && b >= 0) {
            local.add(b, local.removeAt(a))
            dragOffset -= (other.offset - me.offset)
        }
    }

    fun onDrop() {
        if (dragKey >= 0) {
            val to = local.indexOf(dragKey)
            if (to >= 0 && to != dragStart) vm.player.move(cur + 1 + dragStart, cur + 1 + to)
        }
        dragKey = -1
        dragOffset = 0f
    }

    suspend fun settleQueue(vy: Float) {
        if (vy > 1000f || (queueP.value < 0.75f && vy > -1000f)) queueP.animateTo(0f, springSpec)
        else queueP.animateTo(1f, springSpec)
    }

    // Swipe down on the list (when it is scrolled to the top) pulls the whole panel down to close it.
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y < 0f && queueP.value < 1f) {
                    scope.launch { queueP.snapTo((queueP.value - available.y / heightPx).coerceIn(0f, 1f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.Drag && available.y > 0f) {
                    scope.launch { queueP.snapTo((queueP.value - available.y / heightPx).coerceIn(0f, 1f)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (queueP.value in 0.001f..0.999f) {
                    settleQueue(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    fun settleHeader() {
        val v = tracker.calculateVelocity().y
        scope.launch { settleQueue(v) }
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationY = (1f - queueP.value) * size.height }
            .background(Color(0xFF0B0B0F))
            .drawBehind { drawRect(Brush.verticalGradient(listOf(tint.copy(alpha = 0.75f), Color.Transparent))) }
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(60.dp)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { tracker.resetTracking(); total = 0f },
                            onDragEnd = { settleHeader() },
                            onDragCancel = { settleHeader() },
                            onVerticalDrag = { change, dy ->
                                change.consume()
                                total += dy
                                tracker.addPosition(change.uptimeMillis, Offset(0f, total))
                                scope.launch { queueP.snapTo((queueP.value - dy / heightPx).coerceIn(0f, 1f)) }
                            }
                        )
                    }
                    .padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Up next", Modifier.weight(1f), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                IconButton({ scope.launch { queueP.animateTo(0f, springSpec) } }) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Close", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(nested),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "h1") { QueueLabel("Playing now") }
                item(key = "cur") {
                    val s = ps.mediaId?.toLongOrNull()?.let { lib.byId[it] }
                    if (s != null) {
                        // the playing song has transport buttons instead of a handle
                        QueueRow(s, current = true, dragging = false, onClick = {}, onRemove = null) {
                            IconButton({ vm.player.previous() }, Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.FastRewind, "Previous", tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                            IconButton({ vm.player.togglePlay() }, Modifier.size(44.dp)) {
                                Icon(if (ps.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause",
                                    tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            IconButton({ vm.player.next() }, Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.FastForward, "Next", tint = Color.White, modifier = Modifier.size(26.dp))
                            }
                        }
                    }
                }
                item(key = "h2") { QueueLabel("Up next") }
                if (local.isEmpty()) {
                    item(key = "empty") {
                        Text("Nothing up next", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = Color.White.copy(alpha = 0.6f))
                    }
                }
                itemsIndexed(local, key = { _, k -> k }) { _, k ->
                    val s = ps.queue.getOrNull(k)?.toLongOrNull()?.let { lib.byId[it] }
                    if (s != null) {
                        val isDragged = dragKey == k
                        QueueRow(
                            s, current = false, dragging = isDragged,
                            modifier = (if (isDragged) Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                            else Modifier.animateItemPlacement())
                                .pointerInput(k) {
                                    // press and hold anywhere on the row to lift it
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragKey = k; dragOffset = 0f; dragStart = local.indexOf(k)
                                        },
                                        onDragEnd = { onDrop() },
                                        onDragCancel = { onDrop() },
                                        onDrag = { change, amount -> change.consume(); onDragMove(amount.y) }
                                    )
                                },
                            onClick = { vm.player.skipTo(k) },
                            onRemove = { vm.player.removeAt(k) },
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueLabel(text: String) {
    Text(text, Modifier.padding(horizontal = 24.dp, vertical = 10.dp), color = Color.White.copy(alpha = 0.6f),
        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun QueueRow(
    song: Song, current: Boolean, dragging: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth()
            .background(if (dragging) Color(0xFF2A2A31) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(song, Modifier.size(48.dp), px = 144)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium)
            Text(song.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) {
            IconButton(onRemove) { Icon(Icons.Rounded.Close, "Remove", tint = Color.White.copy(alpha = 0.55f)) }
        }
        trailing()
    }
}
