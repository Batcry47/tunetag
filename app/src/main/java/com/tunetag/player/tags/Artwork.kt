package com.tunetag.player.tags

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import com.tunetag.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.max

/** A 32x32 copy of the artwork: scaled up smoothly it gives a free "blurred ambient" backdrop (no blur API needed). */
class TinyArt(val bitmap: Bitmap, val avg: Int)

object ArtCache {
    private val tinyCache = LruCache<String, TinyArt>(48)

    fun peekTiny(song: Song?): TinyArt? = if (song == null) null else tinyCache.get("t${song.id}#${song.dateModified}")

    suspend fun tiny(ctx: Context, song: Song): TinyArt? = withContext(dispatcher) {
        val k = "t${song.id}#${song.dateModified}"
        tinyCache.get(k)?.let { return@withContext it }
        val src = fetch(ctx, song.uri, 96) ?: return@withContext null
        val soft: Bitmap? = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
        if (soft == null) return@withContext null
        val small = Bitmap.createScaledBitmap(soft, 32, 32, true)
        val px = IntArray(32 * 32)
        small.getPixels(px, 0, 32, 0, 0, 32, 32)
        var r = 0L
        var g = 0L
        var b = 0L
        for (p in px) {
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
        }
        val n = px.size
        val t = TinyArt(small, android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()))
        tinyCache.put(k, t)
        t
    }

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val missing = HashSet<String>()

    // A small low-priority pool keeps fast scrolling smooth and saves battery.
    private val dispatcher = Executors.newFixedThreadPool(3) { r ->
        Thread(r, "tunetag-art").apply { priority = Thread.MIN_PRIORITY + 1; isDaemon = true }
    }.asCoroutineDispatcher()

    fun clear() {
        cache.evictAll()
        tinyCache.evictAll()
        synchronized(missing) { missing.clear() }
    }

    // Songs of the same album share one cached bitmap (unless the album is unknown or `exact` is requested).
    private fun key(song: Song, px: Int, exact: Boolean): String =
        if (!exact && song.album != "Unknown album") "a${song.albumId}#$px" else "s${song.id}#${song.dateModified}#$px"

    fun peek(song: Song?, px: Int, exact: Boolean): Bitmap? = if (song == null) null else cache.get(key(song, px, exact))

    suspend fun load(ctx: Context, song: Song, px: Int, exact: Boolean = false): Bitmap? = withContext(dispatcher) {
        val k = key(song, px, exact)
        cache.get(k)?.let { return@withContext it }
        val miss = "m${song.id}#$px"
        if (synchronized(missing) { miss in missing }) return@withContext null
        val b = fetch(ctx, song.uri, px)
        if (b != null) cache.put(k, b) else synchronized(missing) { missing.add(miss) }
        b
    }

    /** Blocking variant for background threads (notification art, colour extraction). */
    fun loadBlocking(ctx: Context, uri: Uri, px: Int, key: String): Bitmap? {
        val k = "$key#$px"
        cache.get(k)?.let { return it }
        val b = fetch(ctx, uri, px)
        if (b != null) cache.put(k, b)
        return b
    }

    private fun fetch(ctx: Context, uri: Uri, px: Int): Bitmap? {
        return try {
            ctx.contentResolver.loadThumbnail(uri, Size(px, px), null)
        } catch (_: Exception) {
            fromEmbedded(ctx, uri, px)
        }
    }

    private fun fromEmbedded(ctx: Context, uri: Uri, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            val bytes = r.embeddedPicture
            if (bytes == null) null else decodeScaled(bytes, px)
        } catch (_: Exception) {
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    fun decodeScaled(bytes: ByteArray, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= px && o.outHeight / (sample * 2) >= px) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o2)
    }
}

object ArtworkFetcher {
    /** Downloads an image and re-encodes it as a JPEG of at most 1200px (what we embed into files). */
    suspend fun fetchJpeg(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val urls = listOf(url, url.replace("front-1200", "front-500"), url.replace("front-1200", "front")).distinct()
        for (u in urls) {
            val bytes = (try { Http.getBytes(u) } catch (_: Exception) { null }) ?: continue
            toJpeg(bytes)?.let { return@withContext it }
        }
        null
    }

    fun toJpeg(bytes: ByteArray, maxDim: Int = 1200): ByteArray? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val big = max(bmp.width, bmp.height)
        val scaled = if (big > maxDim) {
            val f = maxDim.toFloat() / big
            Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
        } else bmp
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
        return out.toByteArray()
    }

    suspend fun fromUri(ctx: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.let { toJpeg(it) }
        } catch (_: Exception) {
            null
        }
    }
}
