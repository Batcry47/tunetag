package com.tunetag.player.tags

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

data class TagData(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val track: String = "",
    val disc: String = "",
    val composer: String = "",
)

enum class TagField(val label: String) {
    TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), ALBUM_ARTIST("Album artist"),
    GENRE("Genre"), YEAR("Year"), TRACK("Track #"), DISC("Disc #"), ARTWORK("Artwork")
}

/** Returns a copy of these tags with the chosen fields taken from the candidate. */
fun TagData.applying(c: TrackCandidate, fields: Set<TagField>, onlyEmpty: Boolean): TagData {
    fun pick(f: TagField, old: String, new: String?): String =
        if (f in fields && !new.isNullOrBlank() && (!onlyEmpty || old.isBlank())) new else old
    return copy(
        title = pick(TagField.TITLE, title, c.title),
        artist = pick(TagField.ARTIST, artist, c.artist),
        album = pick(TagField.ALBUM, album, c.album),
        albumArtist = pick(TagField.ALBUM_ARTIST, albumArtist, c.albumArtist ?: c.artist),
        genre = pick(TagField.GENRE, genre, c.genre),
        year = pick(TagField.YEAR, year, c.year?.toString()),
        track = pick(TagField.TRACK, track, c.trackNo?.toString()),
        disc = pick(TagField.DISC, disc, c.discNo?.toString()),
    )
}

object TagIO {
    private fun get(tag: Tag, k: FieldKey): String = try {
        tag.getFirst(k) ?: ""
    } catch (_: Exception) {
        ""
    }

    fun read(path: String): TagData {
        return try {
            val f = AudioFileIO.read(File(path))
            val t = f.tag ?: return TagData()
            TagData(
                title = get(t, FieldKey.TITLE), artist = get(t, FieldKey.ARTIST),
                album = get(t, FieldKey.ALBUM), albumArtist = get(t, FieldKey.ALBUM_ARTIST),
                genre = get(t, FieldKey.GENRE), year = get(t, FieldKey.YEAR),
                track = get(t, FieldKey.TRACK), disc = get(t, FieldKey.DISC_NO),
                composer = get(t, FieldKey.COMPOSER),
            )
        } catch (_: Throwable) {
            TagData()
        }
    }

    fun hasArtwork(path: String): Boolean = try {
        AudioFileIO.read(File(path)).tag?.artworkList?.isNotEmpty() == true
    } catch (_: Throwable) {
        false
    }

    fun write(path: String, d: TagData, artwork: ByteArray?, removeArtwork: Boolean): Result<Unit> = runCatching {
        val f = AudioFileIO.read(File(path))
        val tag = f.tagOrCreateAndSetDefault

        fun set(k: FieldKey, v: String) {
            if (v.isBlank()) {
                try { tag.deleteField(k) } catch (_: Exception) {}
            } else {
                tag.setField(k, v.trim())
            }
        }
        set(FieldKey.TITLE, d.title)
        set(FieldKey.ARTIST, d.artist)
        set(FieldKey.ALBUM, d.album)
        set(FieldKey.ALBUM_ARTIST, d.albumArtist)
        set(FieldKey.GENRE, d.genre)
        set(FieldKey.YEAR, d.year)
        set(FieldKey.TRACK, d.track)
        set(FieldKey.DISC_NO, d.disc)
        set(FieldKey.COMPOSER, d.composer)

        if (removeArtwork || artwork != null) {
            try { tag.deleteArtworkField() } catch (_: Exception) {}
        }
        if (artwork != null) {
            val art = ArtworkFactory.getNew()
            art.binaryData = artwork
            art.mimeType = "image/jpeg"
            art.pictureType = 3
            tag.setField(art)
        }
        f.commit()
    }
}
