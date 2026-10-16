package com.tunetag.player.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class TopSong(val songId: Long, val title: String, val artist: String, val plays: Int, val ms: Long)
data class TopName(val name: String, val sub: String, val plays: Int, val ms: Long)
data class Stats(
    val totalMs: Long = 0,
    val plays: Int = 0,
    val songs: Int = 0,
    val topSongs: List<TopSong> = emptyList(),
    val topArtists: List<TopName> = emptyList(),
    val topAlbums: List<TopName> = emptyList(),
    val topGenres: List<TopName> = emptyList(),
)

/** One row per listening session of a track. Written by the playback service so it works with the screen off. */
class HistoryDb(ctx: Context) : SQLiteOpenHelper(ctx, "history.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE plays(_id INTEGER PRIMARY KEY AUTOINCREMENT, song_id INTEGER, title TEXT, artist TEXT, " +
                "album TEXT, genre TEXT, ts INTEGER, listened INTEGER, duration INTEGER)"
        )
        db.execSQL("CREATE INDEX plays_ts ON plays(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun insert(songId: Long, title: String, artist: String, album: String, genre: String, ts: Long, listened: Long, duration: Long): Long {
        val v = ContentValues().apply {
            put("song_id", songId); put("title", title); put("artist", artist); put("album", album)
            put("genre", genre); put("ts", ts); put("listened", listened); put("duration", duration)
        }
        return writableDatabase.insert("plays", null, v)
    }

    fun updateListened(id: Long, listened: Long) {
        writableDatabase.execSQL("UPDATE plays SET listened=? WHERE _id=?", arrayOf<Any>(listened, id))
    }

    private val played = "CASE WHEN listened >= 30000 OR (duration > 0 AND listened * 2 >= duration) THEN 1 ELSE 0 END"

    fun stats(from: Long, to: Long): Stats {
        val db = readableDatabase
        val args = arrayOf(from.toString(), to.toString())
        var totalMs = 0L
        var plays = 0
        var songs = 0
        db.rawQuery(
            "SELECT COALESCE(SUM(listened),0), COALESCE(SUM($played),0), COUNT(DISTINCT song_id) FROM plays WHERE ts >= ? AND ts < ?", args
        ).use { c -> if (c.moveToFirst()) { totalMs = c.getLong(0); plays = c.getInt(1); songs = c.getInt(2) } }

        val topSongs = ArrayList<TopSong>()
        db.rawQuery(
            "SELECT song_id, title, artist, SUM($played) AS p, SUM(listened) AS ms FROM plays WHERE ts >= ? AND ts < ? " +
                "GROUP BY song_id HAVING ms > 0 ORDER BY p DESC, ms DESC LIMIT 10", args
        ).use { c -> while (c.moveToNext()) topSongs.add(TopSong(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3), c.getLong(4))) }

        fun grouped(sql: String): List<TopName> {
            val out = ArrayList<TopName>()
            db.rawQuery(sql, args).use { c ->
                while (c.moveToNext()) out.add(TopName(c.getString(0) ?: "", c.getString(1) ?: "", c.getInt(2), c.getLong(3)))
            }
            return out
        }
        val artists = grouped(
            "SELECT artist, '' , SUM($played), SUM(listened) AS ms FROM plays WHERE ts >= ? AND ts < ? GROUP BY artist ORDER BY ms DESC LIMIT 5"
        )
        val albums = grouped(
            "SELECT album, artist, SUM($played), SUM(listened) AS ms FROM plays WHERE ts >= ? AND ts < ? GROUP BY album, artist ORDER BY ms DESC LIMIT 6"
        )
        val genres = grouped(
            "SELECT genre, '' , SUM($played), SUM(listened) AS ms FROM plays WHERE ts >= ? AND ts < ? AND genre != '' GROUP BY genre ORDER BY ms DESC LIMIT 5"
        )
        return Stats(totalMs, plays, songs, topSongs, artists, albums, genres)
    }
}
