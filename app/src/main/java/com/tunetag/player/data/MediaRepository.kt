package com.tunetag.player.data

import android.content.Context
import android.provider.MediaStore

object MediaRepository {

    private fun clean(s: String?, fallback: String): String {
        val t = s?.trim().orEmpty()
        return if (t.isEmpty() || t == "<unknown>") fallback else t
    }

    /** Fast single query. Genres are loaded separately so the library appears instantly. */
    fun loadSongs(ctx: Context): List<Song> {
        val a = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DATE_MODIFIED, MediaStore.Audio.Media.SIZE,
        )
        val out = ArrayList<Song>()
        ctx.contentResolver.query(a, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(8) ?: continue
                val rawTrack = c.getInt(6)
                out.add(
                    Song(
                        id = c.getLong(0),
                        title = clean(c.getString(1), path.substringAfterLast('/').substringBeforeLast('.')),
                        artist = clean(c.getString(2), "Unknown artist"),
                        album = clean(c.getString(3), "Unknown album"),
                        albumId = c.getLong(4),
                        durationMs = c.getLong(5),
                        trackNo = rawTrack % 1000,
                        discNo = if (rawTrack >= 1000) rawTrack / 1000 else 0,
                        year = c.getInt(7),
                        genre = "",
                        path = path,
                        dateAdded = c.getLong(9),
                        dateModified = c.getLong(10),
                        size = c.getLong(11),
                    )
                )
            }
        }
        return out
    }

    // MediaStore.Audio.Media.GENRE only exists on API 30+, so on Android 10 we walk the genre tables.
    fun loadGenres(ctx: Context): Map<Long, String> {
        val map = HashMap<Long, String>()
        try {
            ctx.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null, null, null
            )?.use { g ->
                while (g.moveToNext()) {
                    val gid = g.getLong(0)
                    val name = g.getString(1)?.trim().orEmpty()
                    if (name.isBlank()) continue
                    val mUri = MediaStore.Audio.Genres.Members.getContentUri("external", gid)
                    ctx.contentResolver.query(
                        mUri, arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null
                    )?.use { m ->
                        while (m.moveToNext()) map[m.getLong(0)] = name
                    }
                }
            }
        } catch (_: Exception) {
        }
        return map
    }
}
