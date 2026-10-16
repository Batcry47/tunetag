package com.tunetag.player.ui

import android.app.Application
import android.content.Context
import android.media.MediaScannerConnection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import com.tunetag.player.TuneTagApp
import com.tunetag.player.data.*
import com.tunetag.player.playback.PlayerController
import com.tunetag.player.playback.PlayerState
import com.tunetag.player.tags.ArtCache
import com.tunetag.player.tags.TagData
import com.tunetag.player.tags.TagIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Colours pulled from the current artwork, used by the player page and mini player only. */
data class NowColors(val top: Color, val mid: Color, val bottom: Color, val accent: Color)

fun sortSongs(songs: List<Song>, key: SortKey, desc: Boolean): List<Song> {
    val cmp: Comparator<Song> = when (key) {
        SortKey.TITLE -> Comparator { a, b -> a.title.compareTo(b.title, ignoreCase = true) }
        SortKey.ARTIST -> Comparator { a, b ->
            a.artist.compareTo(b.artist, true).let { if (it != 0) it else a.album.compareTo(b.album, true) }
        }
        SortKey.ALBUM -> Comparator { a, b ->
            a.album.compareTo(b.album, true).let {
                if (it != 0) it else compareValuesBy(a, b, { s -> s.discNo }, { s -> s.trackNo })
            }
        }
        SortKey.DATE_ADDED -> compareBy<Song> { it.dateAdded }
        SortKey.DURATION -> compareBy<Song> { it.durationMs }
        SortKey.YEAR -> compareBy<Song> { it.year }
    }
    val sorted = songs.sortedWith(cmp)
    return if (desc) sorted.reversed() else sorted
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    val store: UserStore = (app as TuneTagApp).store
    val user: StateFlow<UserState> = store.state

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()
    val loading = MutableStateFlow(false)
    private var restored = false
    private var genres: Map<Long, String> = emptyMap()

    val player = PlayerController(ctx, viewModelScope)
    val playerState: StateFlow<PlayerState> = player.state

    /** Only changes when the track changes, so song lists don't recompose on play/pause. */
    val currentId: StateFlow<String?> = playerState.map { it.mediaId }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val sortKey = user.map { it.settings.sort to it.settings.sortDesc }.distinctUntilChanged()

    val sortedSongs: StateFlow<List<Song>> = combine(library, sortKey) { lib, k ->
        sortSongs(lib.songs, k.first, k.second)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentSong: StateFlow<Song?> = combine(playerState, library) { ps, lib ->
        ps.mediaId?.toLongOrNull()?.let { lib.byId[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nowColors = MutableStateFlow<NowColors?>(null)

    init {
        player.connect()
        viewModelScope.launch {
            // Only extract colours while the app is on screen (saves battery with the screen off).
            combine(currentSong, player.foreground) { s, fg -> s to fg }.collectLatest { (s, fg) ->
                if (s == null) nowColors.value = null
                else if (fg) nowColors.value = withContext(Dispatchers.IO) { computeColors(s) }
            }
        }
    }

    private fun computeColors(s: Song): NowColors? {
        val bmp = ArtCache.loadBlocking(ctx, s.uri, 96, "c${s.id}#${s.dateModified}") ?: return null
        val p = Palette.from(bmp).maximumColorCount(12).generate()
        val base = p.vibrantSwatch?.rgb ?: p.dominantSwatch?.rgb ?: p.mutedSwatch?.rgb ?: return null
        val second = p.darkVibrantSwatch?.rgb ?: p.darkMutedSwatch?.rgb ?: p.mutedSwatch?.rgb ?: base
        val b = Color(base)
        val s2 = Color(second)
        return NowColors(
            top = lerp(b, Color.Black, 0.45f),
            mid = lerp(s2, Color.Black, 0.55f),
            bottom = lerp(s2, Color.Black, 0.82f),
            accent = lerp(b, Color.White, 0.2f),
        )
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }

    // ------------------------------------------------------------ library
    fun refresh() {
        viewModelScope.launch {
            loading.value = true
            val songs = withContext(Dispatchers.IO) { MediaRepository.loadSongs(ctx) }
            val known = genres
            _library.value = Library(if (known.isEmpty()) songs else songs.map { s -> s.copy(genre = known[s.id] ?: "") })
            loading.value = false
            if (!restored) {
                restored = true
                restoreQueue()
            }
            // genres are slower to read, so they fill in afterwards
            val g = withContext(Dispatchers.IO) { MediaRepository.loadGenres(ctx) }
            genres = g
            if (g.isNotEmpty()) {
                _library.value = Library(withContext(Dispatchers.Default) {
                    _library.value.songs.map { s -> s.copy(genre = g[s.id] ?: "") }
                })
            }
        }
    }

    private fun restoreQueue() {
        val (ids, idx, pos) = store.loadQueue()
        if (ids.isEmpty()) return
        val lib = _library.value
        val list = ids.mapNotNull { id -> id.toLongOrNull()?.let { lib.byId[it] } }
        if (list.isNotEmpty()) player.restore(list, idx.coerceIn(0, list.size - 1), pos)
    }

    // ------------------------------------------------------------ user data
    fun createPlaylist(name: String, songs: List<Song> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        store.update { u -> u.copy(playlists = u.playlists + Playlist(id, name.trim(), songs.map { it.path })) }
        return id
    }

    fun addToPlaylist(id: String, songs: List<Song>) = store.update { u ->
        u.copy(playlists = u.playlists.map { pl ->
            if (pl.id == id) pl.copy(paths = pl.paths + songs.map { it.path }.filter { p -> p !in pl.paths }) else pl
        })
    }

    fun removeFromPlaylist(id: String, songs: List<Song>) = store.update { u ->
        val gone = songs.map { it.path }.toSet()
        u.copy(playlists = u.playlists.map { pl -> if (pl.id == id) pl.copy(paths = pl.paths.filter { it !in gone }) else pl })
    }

    fun deletePlaylist(id: String) = store.update { u -> u.copy(playlists = u.playlists.filter { it.id != id }) }

    fun updateSettings(block: (AppSettings) -> AppSettings) = store.update { u -> u.copy(settings = block(u.settings)) }

    // ------------------------------------------------------------ tagging
    suspend fun saveTags(song: Song, data: TagData, artwork: ByteArray?, removeArt: Boolean): Result<Unit> {
        val r = withContext(Dispatchers.IO) { TagIO.write(song.path, data, artwork, removeArt) }
        if (r.isSuccess) rescan(listOf(song.path))
        return r
    }

    /** Asks MediaStore to re-read the files, then reloads the library. */
    suspend fun rescan(paths: List<String>) {
        if (paths.isEmpty()) return
        withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Unit> { cont ->
                val left = AtomicInteger(paths.size)
                MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null) { _, _ ->
                    if (left.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
                }
            }
        }
        ArtCache.clear()
        val g = genres
        val songs = withContext(Dispatchers.IO) { MediaRepository.loadSongs(ctx) }
        _library.value = Library(if (g.isEmpty()) songs else songs.map { s -> s.copy(genre = g[s.id] ?: "") })
    }
}
