package com.tunetag.player.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNo: Int,
    val discNo: Int,
    val year: Int,
    val genre: String,
    val path: String,
    val dateAdded: Long,
    val dateModified: Long,
    val size: Long,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val folder: String get() = path.substringBeforeLast('/', "")
}

data class Album(val id: Long, val name: String, val artist: String, val year: Int, val songs: List<Song>)
data class Artist(val name: String, val songs: List<Song>)
data class Genre(val name: String, val songs: List<Song>)
data class Folder(val path: String, val songs: List<Song>) {
    val name: String get() = path.substringAfterLast('/')
}
data class Playlist(val id: String, val name: String, val paths: List<String>)

enum class SortKey(val label: String) {
    TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), DATE_ADDED("Date added"),
    DURATION("Duration"), YEAR("Year")
}

data class Library(val songs: List<Song> = emptyList()) {
    val byId: Map<Long, Song> by lazy { songs.associateBy { it.id } }
    val byPath: Map<String, Song> by lazy { songs.associateBy { it.path } }
    val searchIndex: List<String> by lazy { songs.map { "${it.title} ${it.artist} ${it.album}".lowercase() } }

    val albums: List<Album> by lazy {
        songs.groupBy { it.albumId }.map { (id, list) ->
            val artist = list.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: ""
            Album(
                id, list.first().album, artist, list.maxOf { it.year },
                list.sortedWith(compareBy<Song>({ it.discNo }, { it.trackNo }, { it.title }))
            )
        }.sortedBy { it.name.lowercase() }
    }
    val artists: List<Artist> by lazy {
        songs.groupBy { it.artist }.map { Artist(it.key, it.value) }.sortedBy { it.name.lowercase() }
    }
    val genres: List<Genre> by lazy {
        songs.filter { it.genre.isNotBlank() }.groupBy { it.genre }
            .map { Genre(it.key, it.value) }.sortedBy { it.name.lowercase() }
    }
    val folders: List<Folder> by lazy {
        songs.groupBy { it.folder }.map { Folder(it.key, it.value) }.sortedBy { it.path.lowercase() }
    }
}

data class AppSettings(
    val useItunes: Boolean = true,
    val useDeezer: Boolean = true,
    val useMusicBrainz: Boolean = true,
    val country: String = "US",
    val minScore: Int = 70,
    val sort: SortKey = SortKey.TITLE,
    val sortDesc: Boolean = false,
    val themeMode: Int = 0, // 0 system, 1 light, 2 dark
    val seekSeconds: Int = 5,
    val albumGrid: Boolean = true,
    val playerStyle: Int = 0, // 0 modern, 1 iPod Classic
    val ipodSound: Boolean = true,
    val ipodHaptics: Boolean = true,
)

data class UserState(
    val playlists: List<Playlist> = emptyList(),
    val settings: AppSettings = AppSettings(),
)
