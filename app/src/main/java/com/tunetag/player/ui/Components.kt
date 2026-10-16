package com.tunetag.player.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunetag.player.data.Playlist
import com.tunetag.player.data.Song
import com.tunetag.player.tags.ArtCache
import com.tunetag.player.tags.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** Extra space at the bottom of lists so the floating mini player / tab bar never cover content. */
val LocalBottomPad = compositionLocalOf { 0.dp }

/** Callbacks shared by every song list. Built once, so rows don't recompose needlessly. */
class SongActions(
    val play: (List<Song>, Int) -> Unit,
    val playInOrder: (List<Song>) -> Unit,
    val shuffle: (List<Song>) -> Unit,
    val more: (Song) -> Unit,
    val enqueue: (List<Song>) -> Unit,
    val addToPlaylist: (List<Song>) -> Unit,
    val autoTag: (List<Song>) -> Unit,
    val push: (Route) -> Unit,
)

@Composable
fun Artwork(
    song: Song?,
    modifier: Modifier = Modifier,
    px: Int = 200,
    shape: Shape = RoundedCornerShape(8.dp),
    exact: Boolean = false,
) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(ArtCache.peek(song, px, exact), song?.id, song?.dateModified, px, exact) {
        value = if (song == null) null else ArtCache.load(ctx, song, px, exact)
    }
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

private val remoteCache = LruCache<String, Bitmap>(80)

/** Loads a small image from the web (used for provider artwork previews while tagging). */
@Composable
fun RemoteImage(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val bmp by produceState<Bitmap?>(if (url == null) null else remoteCache.get(url), url) {
        value = if (url == null) null else withContext(Dispatchers.IO) {
            remoteCache.get(url) ?: try {
                Http.getBytes(url)?.let { ArtCache.decodeScaled(it, 300) }?.also { remoteCache.put(url, it) }
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

/** iOS-style page with a large title that collapses as you scroll. */
@Composable
fun LargePage(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Column(Modifier.fillMaxSize().background(cs.background).nestedScroll(behavior.nestedScrollConnection)) {
        LargeTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            },
            actions = actions,
            scrollBehavior = behavior,
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = cs.background, scrolledContainerColor = cs.background
            ),
        )
        content()
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun PlayShuffleRow(count: Int, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onBackground
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        listOf(Triple("Play", Icons.Rounded.PlayArrow, onPlay), Triple("Shuffle", Icons.Rounded.Shuffle, onShuffle)).forEach { (label, icon, click) ->
            Row(
                Modifier.weight(1f).height(52.dp).clickable(enabled = count > 0, onClick = click),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SongList(
    songs: List<Song>,
    actions: SongActions,
    currentId: String?,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    header: (@Composable () -> Unit)? = null,
    showTrackNo: Boolean = false,
    onRemoveSelected: ((List<Song>) -> Unit)? = null,
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val selecting = selected.isNotEmpty()
    BackHandler(selecting) { selected = emptySet() }

    Column(modifier) {
        AnimatedVisibility(selecting) {
            Surface(
                shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ selected = emptySet() }) { Icon(Icons.Rounded.Close, "Clear") }
                    Text("${selected.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton({ selected = songs.map { it.id }.toSet() }) { Icon(Icons.Rounded.SelectAll, "Select all") }
                    IconButton({ actions.play(songs.filter { it.id in selected }, 0); selected = emptySet() }) {
                        Icon(Icons.Rounded.PlayArrow, "Play")
                    }
                    IconButton({ actions.enqueue(songs.filter { it.id in selected }); selected = emptySet() }) {
                        Icon(Icons.Rounded.QueueMusic, "Add to queue")
                    }
                    IconButton({ actions.addToPlaylist(songs.filter { it.id in selected }); selected = emptySet() }) {
                        Icon(Icons.Rounded.PlaylistAdd, "Add to playlist")
                    }
                    IconButton({ actions.autoTag(songs.filter { it.id in selected }); selected = emptySet() }) {
                        Icon(Icons.Rounded.AutoFixHigh, "Auto-tag")
                    }
                    if (onRemoveSelected != null) {
                        IconButton({ onRemoveSelected(songs.filter { it.id in selected }); selected = emptySet() }) {
                            Icon(Icons.Rounded.Delete, "Remove")
                        }
                    }
                }
            }
        }
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(bottom = LocalBottomPad.current + 8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            if (header != null) item { header() }
            if (songs.isEmpty()) item { EmptyState("Nothing here yet") }
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    selected = song.id in selected,
                    isCurrent = song.id.toString() == currentId,
                    trackNo = if (showTrackNo) song.trackNo else 0,
                    onClick = {
                        if (selecting) selected = if (song.id in selected) selected - song.id else selected + song.id
                        else actions.play(songs, index)
                    },
                    onLong = { selected = if (song.id in selected) selected - song.id else selected + song.id },
                    onMore = { actions.more(song) },
                )
            }
        }
    }
}

@Composable
private fun SongRow(
    song: Song,
    selected: Boolean,
    isCurrent: Boolean,
    trackNo: Int,
    onClick: () -> Unit,
    onLong: () -> Unit,
    onMore: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().background(if (selected) cs.primary.copy(alpha = 0.12f) else Color.Transparent)) {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong)
                .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                if (trackNo > 0) {
                    Text(
                        "$trackNo", fontSize = 16.sp,
                        color = if (isCurrent) cs.primary else cs.onSurfaceVariant
                    )
                } else {
                    Artwork(song, Modifier.fillMaxSize(), px = 150)
                }
                if (selected) {
                    Box(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(cs.primary.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Check, null, tint = cs.onPrimary) }
                } else if (isCurrent && trackNo == 0) {
                    Box(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.GraphicEq, null, tint = Color.White) }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isCurrent) cs.primary else cs.onSurface
                )
                Text(
                    "${song.artist} • ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontSize = 14.sp, color = cs.onSurfaceVariant
                )
            }
            IconButton(onMore) { Icon(Icons.Rounded.MoreHoriz, "More", tint = cs.onSurfaceVariant) }
        }
        Box(Modifier.padding(start = 80.dp).fillMaxWidth().height(0.5.dp).background(cs.outline.copy(alpha = 0.5f)))
    }
}

@Composable
fun SimpleRow(title: String, subtitle: String, leading: @Composable () -> Unit, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp)
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = cs.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = cs.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(cs.outline.copy(alpha = 0.5f)))
    }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(18.dp))
        Text(text, fontSize = 16.sp)
    }
}

@Composable
fun SongMenuSheet(
    song: Song,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onPlaylist: () -> Unit,
    onEdit: () -> Unit,
    onAutoTag: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(song, Modifier.size(56.dp), px = 168)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("${song.artist} • ${song.album}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)))
        MenuItem(Icons.Rounded.SkipNext, "Play next") { onDismiss(); onPlayNext() }
        MenuItem(Icons.Rounded.QueueMusic, "Add to queue") { onDismiss(); onQueue() }
        MenuItem(Icons.Rounded.PlaylistAdd, "Add to playlist") { onDismiss(); onPlaylist() }
        MenuItem(Icons.Rounded.AutoFixHigh, "Auto-tag") { onDismiss(); onAutoTag() }
        MenuItem(Icons.Rounded.Edit, "Edit tags") { onDismiss(); onEdit() }
        MenuItem(Icons.Rounded.Album, "Go to album") { onDismiss(); onAlbum() }
        MenuItem(Icons.Rounded.Person, "Go to artist") { onDismiss(); onArtist() }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onPick: (Playlist) -> Unit,
    onNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(if (creating) "New playlist" else "Add to playlist") },
        text = {
            if (creating) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            } else {
                Column {
                    TextButton({ creating = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("New playlist") }
                    playlists.forEach { p ->
                        TextButton({ onPick(p) }, Modifier.fillMaxWidth()) {
                            Text(p.name, Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creating) TextButton({ if (name.isNotBlank()) onNew(name) }) { Text("Create") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
