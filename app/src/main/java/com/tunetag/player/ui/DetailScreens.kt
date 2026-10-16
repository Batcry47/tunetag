package com.tunetag.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunetag.player.data.Album
import com.tunetag.player.data.Library
import com.tunetag.player.data.Playlist
import com.tunetag.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private class PageData(val title: String, val songs: List<Song>, val album: Album? = null)

private fun resolve(lib: Library, playlists: List<Playlist>, route: Route.Songs): PageData = when (route.kind) {
    "album" -> {
        val a = lib.albums.firstOrNull { it.id.toString() == route.key }
        PageData(a?.name ?: "Album", a?.songs ?: emptyList(), a)
    }
    "artist" -> PageData(
        route.key,
        lib.songs.filter { it.artist == route.key }.sortedWith(compareBy<Song>({ it.album }, { it.discNo }, { it.trackNo }))
    )
    "genre" -> PageData(route.key, lib.songs.filter { it.genre == route.key })
    "folder" -> PageData(route.key.substringAfterLast('/').ifBlank { "/" }, lib.songs.filter { it.folder == route.key })
    "playlist" -> {
        val p = playlists.firstOrNull { it.id == route.key }
        PageData(p?.name ?: "Playlist", p?.paths?.mapNotNull { lib.byPath[it] } ?: emptyList())
    }
    else -> PageData("Recently added", lib.songs.sortedByDescending { it.dateAdded }.take(100))
}

@Composable
fun SongsScreen(vm: AppViewModel, route: Route.Songs, actions: SongActions, onBack: () -> Unit) {
    val lib by vm.library.collectAsState()
    val user by vm.user.collectAsState()
    val currentId by vm.currentId.collectAsState()
    var menu by remember { mutableStateOf(false) }
    val data = remember(lib, user.playlists, route) { resolve(lib, user.playlists, route) }
    val songs = data.songs
    val isPlaylist = route.kind == "playlist"

    // albums get the immersive Apple Music style page
    val albumData = data.album
    if (albumData != null && albumData.songs.isNotEmpty()) {
        AlbumPage(vm, albumData, actions, onBack)
        return
    }

    val removeFn: ((List<Song>) -> Unit)? =
        if (isPlaylist) fun(l: List<Song>) { vm.removeFromPlaylist(route.key, l) } else null

    LargePage(data.title, onBack = onBack, actions = {
        if (songs.isNotEmpty()) {
            IconButton({ actions.autoTag(songs) }) { Icon(Icons.Rounded.AutoFixHigh, "Auto-tag all") }
        }
        if (isPlaylist) {
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "Menu") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Delete playlist") }, onClick = {
                        menu = false; vm.deletePlaylist(route.key); onBack()
                    })
                }
            }
        }
        IconButton({ actions.push(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") }
    }) {
        SongList(
            songs, actions, currentId, Modifier.weight(1f),
            showTrackNo = route.kind == "album",
            onRemoveSelected = removeFn,
            header = {
                Column {
                    val album = data.album
                    if (album != null) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Artwork(album.songs.first(), Modifier.size(240.dp).shadow(18.dp, RoundedCornerShape(12.dp)), px = 720, shape = RoundedCornerShape(12.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(album.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
                            Text(album.artist, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                            val mins = album.songs.sumOf { it.durationMs } / 60000
                            Text(
                                (if (album.year > 0) "${album.year} • " else "") + "${album.songs.size} songs • $mins min",
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    PlayShuffleRow(songs.size, { actions.playInOrder(songs) }, { actions.shuffle(songs) })
                    if (route.kind == "artist") ArtistAlbums(songs, actions)
                }
            }
        )
    }
}

@Composable
private fun ArtistAlbums(songs: List<Song>, actions: SongActions) {
    val albums = remember(songs) { songs.groupBy { it.albumId }.values.map { it.first() } }
    if (albums.size < 2) return
    Text("Albums", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(albums, key = { it.albumId }) { s ->
            Column(Modifier.width(140.dp).clip(RoundedCornerShape(18.dp))
                .clickable { actions.push(Route.Songs("album", s.albumId.toString())) }) {
                Artwork(s, Modifier.size(140.dp), px = 420, shape = RoundedCornerShape(18.dp))
                Spacer(Modifier.height(6.dp))
                Text(s.album, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Songs", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
}

// ------------------------------------------------------------------ Search
@Composable
fun SearchScreen(vm: AppViewModel, actions: SongActions, onBack: () -> Unit) {
    val lib by vm.library.collectAsState()
    val currentId by vm.currentId.collectAsState()
    var query by remember { mutableStateOf("") }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val focus = remember { FocusRequester() }
    val q = query.trim().lowercase()

    LaunchedEffect(q, lib) {
        if (q.isEmpty()) {
            songs = emptyList()
        } else {
            delay(120) // debounce so typing stays instant
            songs = withContext(Dispatchers.Default) {
                val idx = lib.searchIndex
                lib.songs.filterIndexed { i, _ -> idx[i].contains(q) }.take(200)
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val artists = remember(q, lib) { if (q.isEmpty()) emptyList() else lib.artists.filter { it.name.lowercase().contains(q) }.take(3) }
    val albums = remember(q, lib) {
        if (q.isEmpty()) emptyList() else lib.albums.filter { it.name.lowercase().contains(q) || it.artist.lowercase().contains(q) }.take(3)
    }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            TextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Songs, albums, artists") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true, shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = cs.surfaceVariant, unfocusedContainerColor = cs.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
        }
        if (q.isEmpty()) {
            EmptyState("Search your music")
        } else {
            SongList(
                songs, actions, currentId, Modifier.weight(1f),
                header = {
                    Column {
                        artists.forEach { a ->
                            SimpleRow(a.name, "Artist", { Artwork(a.songs.first(), Modifier.size(48.dp), px = 144, shape = CircleShape) }) {
                                actions.push(Route.Songs("artist", a.name))
                            }
                        }
                        albums.forEach { a ->
                            SimpleRow(a.name, "Album • ${a.artist}", { Artwork(a.songs.first(), Modifier.size(48.dp), px = 144) }) {
                                actions.push(Route.Songs("album", a.id.toString()))
                            }
                        }
                        if (songs.isNotEmpty()) {
                            Text("Songs", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    }
}
