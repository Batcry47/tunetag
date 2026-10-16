package com.tunetag.player.tags

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.tunetag.player.data.AppSettings
import org.json.JSONObject

data class TrackQuery(val title: String, val artist: String, val album: String = "", val durationMs: Long = 0)

data class TrackCandidate(
    val provider: String,
    val title: String,
    val artist: String,
    val albumArtist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val trackNo: Int? = null,
    val discNo: Int? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
    val artworkUrl: String? = null,
    val score: Int = 0,
)

interface MetadataProvider {
    val label: String
    suspend fun search(q: TrackQuery): List<TrackCandidate>
}

internal fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).takeIf { it.isNotBlank() }
internal fun JSONObject.pos(k: String): Int? = if (isNull(k)) null else optInt(k).takeIf { it > 0 }
internal fun JSONObject.objs(k: String): List<JSONObject> {
    val a = optJSONArray(k) ?: return emptyList()
    return List(a.length()) { a.getJSONObject(it) }
}

// ---------------------------------------------------------------- Apple Music (iTunes Search API)
class ItunesProvider(private val country: String) : MetadataProvider {
    override val label = "Apple Music"
    override suspend fun search(q: TrackQuery): List<TrackCandidate> = withContext(Dispatchers.IO) {
        val term = enc("${q.artist} ${q.title}".trim())
        val body = Http.get("https://itunes.apple.com/search?term=$term&media=music&entity=song&limit=10&country=$country")
            ?: return@withContext emptyList()
        JSONObject(body).objs("results").mapNotNull { o ->
            val title = o.str("trackName") ?: return@mapNotNull null
            val artist = o.str("artistName") ?: ""
            TrackCandidate(
                provider = label, title = title, artist = artist,
                albumArtist = o.str("collectionArtistName") ?: artist,
                album = o.str("collectionName"),
                genre = o.str("primaryGenreName"),
                year = o.str("releaseDate")?.take(4)?.toIntOrNull(),
                trackNo = o.pos("trackNumber"), discNo = o.pos("discNumber"),
                durationMs = o.optLong("trackTimeMillis").takeIf { it > 0 },
                artworkUrl = o.str("artworkUrl100")?.replace("100x100bb", "1200x1200bb"),
            )
        }
    }
}

// ---------------------------------------------------------------- Deezer
class DeezerProvider : MetadataProvider {
    override val label = "Deezer"
    override suspend fun search(q: TrackQuery): List<TrackCandidate> = withContext(Dispatchers.IO) {
        val query = if (q.artist.isNotBlank())
            "artist:\"${q.artist.replace("\"", " ")}\" track:\"${q.title.replace("\"", " ")}\""
        else q.title
        val body = Http.get("https://api.deezer.com/search?q=${enc(query)}&limit=8") ?: return@withContext emptyList()
        JSONObject(body).objs("data").mapIndexedNotNull { i, o ->
            val title = o.str("title") ?: return@mapIndexedNotNull null
            val artist = o.optJSONObject("artist")?.str("name") ?: ""
            val album = o.optJSONObject("album")
            var genre: String? = null
            var year: Int? = null
            var trackNo: Int? = null
            var disc: Int? = null
            var isrc: String? = null
            if (i < 3) { // enrich the top few with track/album details (track no., ISRC, genre)
                val tid = o.optLong("id")
                if (tid > 0) {
                    Http.get("https://api.deezer.com/track/$tid")?.let { tb ->
                        runCatching {
                            val t = JSONObject(tb)
                            trackNo = t.pos("track_position"); disc = t.pos("disk_number")
                            isrc = t.str("isrc"); year = t.str("release_date")?.take(4)?.toIntOrNull()
                        }
                    }
                }
                val aid = album?.optLong("id") ?: 0L
                if (aid > 0) {
                    Http.get("https://api.deezer.com/album/$aid")?.let { ab ->
                        runCatching {
                            genre = JSONObject(ab).optJSONObject("genres")?.objs("data")?.firstOrNull()?.str("name")
                        }
                    }
                }
            }
            TrackCandidate(
                provider = label, title = title, artist = artist,
                album = album?.str("title"), genre = genre, year = year,
                trackNo = trackNo, discNo = disc, isrc = isrc,
                durationMs = o.optLong("duration").takeIf { it > 0 }?.times(1000),
                artworkUrl = album?.str("cover_xl"),
            )
        }
    }
}

// ---------------------------------------------------------------- MusicBrainz + Cover Art Archive
class MusicBrainzProvider : MetadataProvider {
    override val label = "MusicBrainz"
    private val lock = Mutex()
    private var last = 0L

    private fun clean(s: String) = s.replace(Regex("[\"\\\\]"), " ")

    override suspend fun search(q: TrackQuery): List<TrackCandidate> {
        val body = lock.withLock {
            // MusicBrainz allows ~1 request per second
            val wait = 1100 - (System.currentTimeMillis() - last)
            if (wait > 0) delay(wait)
            val query = buildString {
                append("recording:\"${clean(q.title)}\"")
                if (q.artist.isNotBlank()) append(" AND artist:\"${clean(q.artist)}\"")
            }
            val r = withContext(Dispatchers.IO) {
                Http.get("https://musicbrainz.org/ws/2/recording/?query=${enc(query)}&fmt=json&limit=6")
            }
            last = System.currentTimeMillis()
            r
        } ?: return emptyList()
        return withContext(Dispatchers.Default) { parse(body) }
    }

    private fun parse(body: String): List<TrackCandidate> =
        JSONObject(body).objs("recordings").mapNotNull { r ->
            val title = r.str("title") ?: return@mapNotNull null
            val artist = r.objs("artist-credit").joinToString("") { ac ->
                (ac.str("name") ?: ac.optJSONObject("artist")?.str("name") ?: "") + ac.optString("joinphrase", "")
            }
            val rel = r.objs("releases").firstOrNull()
            val media = rel?.objs("media")?.firstOrNull()
            val tag = r.objs("tags").maxByOrNull { it.optInt("count") }?.str("name")
            TrackCandidate(
                provider = label, title = title, artist = artist,
                album = rel?.str("title"),
                genre = tag?.replaceFirstChar { it.uppercase() },
                year = rel?.str("date")?.take(4)?.toIntOrNull(),
                trackNo = media?.objs("track")?.firstOrNull()?.str("number")?.toIntOrNull(),
                discNo = media?.pos("position"),
                durationMs = r.optLong("length").takeIf { it > 0 },
                isrc = r.optJSONArray("isrcs")?.optString(0)?.takeIf { it.isNotBlank() },
                artworkUrl = rel?.str("id")?.let { "https://coverartarchive.org/release/$it/front-1200" },
            )
        }
}

// ---------------------------------------------------------------- Aggregator
object MetadataService {
    private val mb = MusicBrainzProvider()
    private val artPriority = listOf("Apple Music", "Deezer", "MusicBrainz")

    fun providers(s: AppSettings): List<MetadataProvider> = buildList {
        if (s.useItunes) add(ItunesProvider(s.country.ifBlank { "US" }))
        if (s.useDeezer) add(DeezerProvider())
        if (s.useMusicBrainz) add(mb)
    }

    /** Queries every enabled provider in parallel and returns candidates sorted by match score. */
    suspend fun search(q: TrackQuery, s: AppSettings): List<TrackCandidate> = coroutineScope {
        providers(s).map { p ->
            async { runCatching { p.search(q) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten()
            .map { it.copy(score = Matcher.score(q, it)) }
            .sortedByDescending { it.score }
    }

    /** Takes the best match and fills any blank fields from other close matches (artwork by provider priority). */
    fun merged(cands: List<TrackCandidate>): TrackCandidate? {
        val best = cands.firstOrNull() ?: return null
        val close = cands.filter { it.score >= best.score - 15 }
        fun <T> pick(sel: (TrackCandidate) -> T?): T? = sel(best) ?: close.firstNotNullOfOrNull(sel)
        val art = close.filter { it.artworkUrl != null }
            .minByOrNull { c -> artPriority.indexOf(c.provider).let { if (it < 0) 99 else it } }?.artworkUrl
        return best.copy(
            albumArtist = pick { it.albumArtist }, album = pick { it.album }, genre = pick { it.genre },
            year = pick { it.year }, trackNo = pick { it.trackNo }, discNo = pick { it.discNo },
            isrc = pick { it.isrc }, artworkUrl = art,
        )
    }
}
