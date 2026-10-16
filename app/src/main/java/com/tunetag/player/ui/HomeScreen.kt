package com.tunetag.player.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunetag.player.data.Album
import com.tunetag.player.data.SortKey
import kotlinx.coroutines.launch

val SECTIONS = listOf("Albums", "Recently Added", "Songs", "Artists", "Genres", "Folders", "Playlists")

class HomeStates(
    val albumsList: LazyListState,
    val albumsGrid: LazyGridState,
    val recent: LazyListState,
    val songs: LazyListState,
    val artists: LazyListState,
    val genres: LazyListState,
    val folders: LazyListState,
    val playlists: LazyListState,
    val pager: PagerState,
)

private val TAB_ICONS = listOf(
    Icons.Rounded.Album, Icons.Rounded.Schedule, Icons.Rounded.MusicNote, Icons.Rounded.Person,
    Icons.Rounded.Category, Icons.Rounded.Folder, Icons.Rounded.QueueMusic
)

@Composable
fun HomeScreen(vm: AppViewModel, actions: SongActions, st: HomeStates) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val settings = vm.user.collectAsState().value.settings
    val pager = st.pager
    var sortDialog by remember { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding()) {
        // icon-only tab bar (no page names): swipe or tap, the indicator slides with the pager
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 4.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxSize()) {
                    TAB_ICONS.forEachIndexed { i, icon ->
                        val on = pager.currentPage == i
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clickable { scope.launch { pager.animateScrollToPage(i) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, SECTIONS[i], tint = if (on) cs.primary else cs.onSurfaceVariant, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    val tabW = size.width / TAB_ICONS.size
                    val pos = pager.currentPage + pager.currentPageOffsetFraction
                    val cx = (pos + 0.5f) * tabW
                    drawRoundRect(
                        cs.primary, Offset(cx - 11.dp.toPx(), size.height - 3.dp.toPx()),
                        Size(22.dp.toPx(), 2.5.dp.toPx()), CornerRadius(2.dp.toPx())
                    )
                }
            }
            IconButton({ actions.push(Route.Search) }, Modifier.size(40.dp)) { Icon(Icons.Rounded.Search, "Search", tint = cs.onSurface) }
            IconButton({ actions.push(Route.Settings) }, Modifier.size(40.dp)) { Icon(Icons.Rounded.Settings, "Settings", tint = cs.onSurface) }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { p ->
            when (p) {
                0 -> AlbumsPage(vm, actions, st.albumsList, st.albumsGrid, settings.albumGrid) {
                    vm.updateSettings { it.copy(albumGrid = !it.albumGrid) }
                }
                1 -> RecentPage(vm, actions, st.recent)
                2 -> SongsPage(vm, actions, st.songs) { sortDialog = true }
                3 -> ArtistsPage(vm, actions, st.artists)
                4 -> GenresPage(vm, actions, st.genres)
                5 -> FoldersPage(vm, actions, st.folders)
                else -> PlaylistsPage(vm, actions, st.playlists) { newPlaylist = true }
            }
        }
    }

    if (sortDialog) {
        AlertDialog(
            onDismissRequest = { sortDialog = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("Sort songs") },
            text = {
                Column {
                    SortKey.values().forEach { k ->
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.updateSettings { it.copy(sort = k) } }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(settings.sort == k, { vm.updateSettings { it.copy(sort = k) } })
                            Text(k.label)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Switch(settings.sortDesc, { v -> vm.updateSettings { it.copy(sortDesc = v) } })
                        Spacer(Modifier.width(10.dp)); Text("Descending")
                    }
                }
            },
            confirmButton = { TextButton({ sortDialog = false }) { Text("Done") } }
        )
    }
    if (newPlaylist) {
        AlertDialog(
            onDismissRequest = { newPlaylist = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("New playlist") },
            text = { OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(14.dp)) },
            confirmButton = {
                TextButton({
                    if (newName.isNotBlank()) vm.createPlaylist(newName)
                    newName = ""; newPlaylist = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton({ newPlaylist = false }) { Text("Cancel") } }
        )
    }
}

/** Small right-aligned action (view toggle / sort / new playlist) that scrolls with the page. */
@Composable
private fun ActionRow(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
        IconButton(onClick) { Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// ------------------------------------------------------------------ pages
@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Artwork(album.songs.first(), Modifier.fillMaxWidth().aspectRatio(1f), px = 420, shape = RoundedCornerShape(10.dp))
        Spacer(Modifier.height(8.dp))
        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
        Text(album.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun AlbumsPage(vm: AppViewModel, actions: SongActions, listState: LazyListState, gridState: LazyGridState, grid: Boolean, onToggle: () -> Unit) {
    val lib by vm.library.collectAsState()
    val bottom = LocalBottomPad.current + 8.dp
    if (lib.albums.isEmpty()) {
        EmptyState("No albums yet")
    } else if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), state = gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = bottom),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { ActionRow(Icons.Rounded.ViewList, "List view", onToggle) }
            items(lib.albums, key = { it.id }) { a -> AlbumCard(a) { actions.push(Route.Songs("album", a.id.toString())) } }
        }
    } else {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = bottom), modifier = Modifier.fillMaxSize()) {
            item { ActionRow(Icons.Rounded.GridView, "Grid view", onToggle) }
            items(lib.albums, key = { it.id }) { a ->
                SimpleRow(
                    a.name, "${a.artist} • ${a.songs.size} songs" + if (a.year > 0) " • ${a.year}" else "",
                    { Artwork(a.songs.first(), Modifier.size(60.dp), px = 180, shape = RoundedCornerShape(8.dp)) }
                ) { actions.push(Route.Songs("album", a.id.toString())) }
            }
        }
    }
}

@Composable
private fun RecentPage(vm: AppViewModel, actions: SongActions, state: LazyListState) {
    val lib by vm.library.collectAsState()
    val currentId by vm.currentId.collectAsState()
    val songs = remember(lib) { lib.songs.sortedByDescending { it.dateAdded }.take(200) }
    SongList(
        songs, actions, currentId, Modifier.fillMaxSize(), state,
        header = { PlayShuffleRow(songs.size, { actions.playInOrder(songs) }, { actions.shuffle(songs) }) }
    )
}

@Composable
private fun SongsPage(vm: AppViewModel, actions: SongActions, state: LazyListState, onSort: () -> Unit) {
    val songs by vm.sortedSongs.collectAsState()
    val currentId by vm.currentId.collectAsState()
    val loading by vm.loading.collectAsState()
    if (songs.isEmpty() && loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        SongList(
            songs, actions, currentId, Modifier.fillMaxSize(), state,
            header = {
                Column {
                    ActionRow(Icons.Rounded.Sort, "Sort", onSort)
                    PlayShuffleRow(songs.size, { actions.playInOrder(songs) }, { actions.shuffle(songs) })
                }
            }
        )
    }
}

@Composable
private fun ArtistsPage(vm: AppViewModel, actions: SongActions, state: LazyListState) {
    val lib by vm.library.collectAsState()
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = LocalBottomPad.current + 8.dp), modifier = Modifier.fillMaxSize()) {
        items(lib.artists, key = { it.name }) { a ->
            SimpleRow(
                a.name, "${a.songs.map { it.albumId }.distinct().size} albums • ${a.songs.size} songs",
                { Artwork(a.songs.first(), Modifier.size(60.dp), px = 180, shape = CircleShape) }
            ) { actions.push(Route.Songs("artist", a.name)) }
        }
    }
}

@Composable
private fun GenresPage(vm: AppViewModel, actions: SongActions, state: LazyListState) {
    val lib by vm.library.collectAsState()
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = LocalBottomPad.current + 8.dp), modifier = Modifier.fillMaxSize()) {
        if (lib.genres.isEmpty()) item { EmptyState("No genres found") }
        items(lib.genres, key = { it.name }) { g ->
            SimpleRow(g.name, "${g.songs.size} songs",
                { Artwork(g.songs.first(), Modifier.size(52.dp), px = 156, shape = RoundedCornerShape(8.dp)) }
            ) { actions.push(Route.Songs("genre", g.name)) }
        }
    }
}

@Composable
private fun FoldersPage(vm: AppViewModel, actions: SongActions, state: LazyListState) {
    val lib by vm.library.collectAsState()
    val cs = MaterialTheme.colorScheme
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = LocalBottomPad.current + 8.dp), modifier = Modifier.fillMaxSize()) {
        items(lib.folders, key = { it.path }) { f ->
            SimpleRow(f.name.ifBlank { "/" }, "${f.songs.size} songs • ${f.path}", {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(cs.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Folder, null, tint = cs.primary)
                }
            }) { actions.push(Route.Songs("folder", f.path)) }
        }
    }
}

@Composable
private fun PlaylistsPage(vm: AppViewModel, actions: SongActions, state: LazyListState, onNew: () -> Unit) {
    val user by vm.user.collectAsState()
    val lib by vm.library.collectAsState()
    val cs = MaterialTheme.colorScheme
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = LocalBottomPad.current + 8.dp), modifier = Modifier.fillMaxSize()) {
        item { ActionRow(Icons.Rounded.Add, "New playlist", onNew) }
        if (user.playlists.isEmpty()) item { EmptyState("No playlists yet. Tap + to make one.") }
        items(user.playlists, key = { it.id }) { p ->
            val first = p.paths.firstNotNullOfOrNull { lib.byPath[it] }
            SimpleRow(p.name, "${p.paths.size} songs", {
                if (first != null) Artwork(first, Modifier.size(60.dp), px = 180, shape = RoundedCornerShape(8.dp))
                else Box(
                    Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(cs.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.QueueMusic, null, tint = cs.primary) }
            }) { actions.push(Route.Songs("playlist", p.id)) }
        }
    }
}
