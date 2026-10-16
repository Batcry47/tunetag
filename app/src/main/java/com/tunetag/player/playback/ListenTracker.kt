package com.tunetag.player.playback

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.tunetag.player.data.HistoryDb
import java.util.concurrent.Executors

/**
 * Measures real listening time per track (wall-clock while actually playing, so seeking doesn't inflate it)
 * and keeps one history row per listen. Runs inside the service so it keeps working with the screen off.
 */
class ListenTracker(private val player: Player, private val db: HistoryDb) : Player.Listener {
    private class Session(val item: MediaItem, val startedAt: Long) {
        var accMs = 0L
        var resumedAt = -1L
        var duration = 0L
        var rowId = -1L // only touched on the history thread
    }

    // single thread => inserts/updates for a session always run in order
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "tunetag-history").apply { priority = Thread.MIN_PRIORITY } }
    private var session: Session? = null

    private fun begin(item: MediaItem?) {
        session = if (item == null) null else Session(item, System.currentTimeMillis()).also {
            if (player.isPlaying) it.resumedAt = SystemClock.elapsedRealtime()
        }
    }

    fun start() {
        begin(player.currentMediaItem)
        player.addListener(this)
    }

    fun stop() {
        player.removeListener(this)
        accumulate()
        flush()
        io.shutdown()
    }

    fun flushNow() {
        accumulate()
        if (player.isPlaying) session?.resumedAt = SystemClock.elapsedRealtime()
        flush()
    }

    private fun accumulate() {
        val s = session ?: return
        if (s.resumedAt >= 0) {
            s.accMs += SystemClock.elapsedRealtime() - s.resumedAt
            s.resumedAt = -1
        }
    }

    private fun flush() {
        val s = session ?: return
        if (s.accMs < 3000) return
        val id = s.item.mediaId.toLongOrNull() ?: return
        val md = s.item.mediaMetadata
        val title = md.title?.toString() ?: ""
        val artist = md.artist?.toString() ?: ""
        val album = md.albumTitle?.toString() ?: ""
        val genre = md.genre?.toString() ?: ""
        val total = s.accMs
        val dur = s.duration
        io.execute {
            try {
                if (s.rowId > 0) db.updateListened(s.rowId, total)
                else s.rowId = db.insert(id, title, artist, album, genre, s.startedAt, total, dur)
            } catch (_: Exception) {
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val s = session ?: return
        if (isPlaying) {
            if (s.resumedAt < 0) s.resumedAt = SystemClock.elapsedRealtime()
        } else {
            accumulate()
            flush()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        accumulate()
        flush()
        begin(mediaItem)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val s = session ?: return
        if (playbackState == Player.STATE_READY && player.duration > 0) s.duration = player.duration
    }
}
