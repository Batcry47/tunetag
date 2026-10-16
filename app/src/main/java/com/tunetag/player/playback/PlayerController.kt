package com.tunetag.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tunetag.player.TuneTagApp
import com.tunetag.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class PlayerState(
    val mediaId: String? = null,
    /** True when the user *wants* playback (stays true while buffering after a seek, so the UI doesn't flicker). */
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val queue: List<String> = emptyList(),
    val index: Int = -1,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF,
)

/**
 * Talks to PlaybackService through a MediaController and exposes the player as flows.
 * Shuffle is done by really re-ordering the queue (not ExoPlayer's hidden shuffle order), so the queue
 * you see - and can drag to reorder - is exactly what will play.
 */
class PlayerController(
    private val ctx: Context,
    private val scope: CoroutineScope,
) {
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private val store get() = TuneTagApp.instance.store

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private val _pos = MutableStateFlow(0L)
    val position: StateFlow<Long> = _pos.asStateFlow()
    private val _fg = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _fg.asStateFlow()
    private var tickJob: Job? = null

    private var shuffleOn = false
    private var sourceOrder: List<Long> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync(events.contains(Player.EVENT_TIMELINE_CHANGED), true)
            updateTicker()
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) savePosition()
        }
    }

    fun connect() {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                sync(true, false)
                pending.forEach { it(c) }
                pending.clear()
                updateTicker()
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun release() {
        tickJob?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else pending.add(block)
    }

    private fun sync(rebuildQueue: Boolean, persist: Boolean) {
        val c = controller ?: return
        val ids = if (rebuildQueue) List(c.mediaItemCount) { c.getMediaItemAt(it).mediaId } else null
        val st = c.playbackState
        _state.update {
            it.copy(
                mediaId = c.currentMediaItem?.mediaId,
                isPlaying = c.playWhenReady && (st == Player.STATE_READY || st == Player.STATE_BUFFERING),
                durationMs = c.duration.coerceAtLeast(0),
                queue = ids ?: it.queue,
                index = c.currentMediaItemIndex,
                shuffle = shuffleOn,
                repeat = c.repeatMode,
            )
        }
        _pos.value = c.currentPosition
        if (persist && ids != null && ids.isNotEmpty()) store.saveQueue(ids)
    }

    /** The position ticker only runs while the app is on screen AND audio is playing. */
    private fun updateTicker() {
        val c = controller
        val want = _fg.value && c != null && c.isPlaying
        if (want) {
            if (tickJob?.isActive != true) {
                tickJob = scope.launch {
                    while (isActive) {
                        val cc = controller ?: break
                        _pos.value = cc.currentPosition
                        val d = cc.duration
                        if (d > 0 && _state.value.durationMs != d) _state.update { it.copy(durationMs = d) }
                        delay(300)
                    }
                }
            }
        } else {
            tickJob?.cancel()
            tickJob = null
            if (c != null) _pos.value = c.currentPosition
        }
    }

    fun setForeground(fg: Boolean) {
        _fg.value = fg
        if (fg) sync(false, false)
        updateTicker()
        if (!fg) savePosition()
    }

    private fun savePosition() {
        controller?.let { if (it.mediaItemCount > 0) store.savePosition(it.currentMediaItemIndex, it.currentPosition) }
    }

    private fun toItem(s: Song): MediaItem = MediaItem.Builder()
        .setMediaId(s.id.toString())
        .setUri(s.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album).setGenre(s.genre)
                .setArtworkUri(s.uri) // resolved to album art by ArtBitmapLoader
                .build()
        ).build()

    // ------------------------------------------------------------ commands
    /**
     * Starts the chosen song immediately, then fills in the rest of the queue behind it, so even a 5,000 song
     * library starts instantly. shuffle = null keeps the current shuffle setting.
     */
    fun play(songs: List<Song>, start: Int = 0, shuffle: Boolean? = null) {
        if (songs.isEmpty()) return
        val s = start.coerceIn(0, songs.size - 1)
        val shuf = shuffle ?: shuffleOn
        scope.launch(Dispatchers.Default) {
            val before: List<Song>
            val rest: List<Song>
            if (shuf) {
                before = emptyList()
                rest = (songs.subList(0, s) + songs.subList(s + 1, songs.size)).shuffled()
            } else {
                before = songs.subList(0, s)
                rest = songs.subList(s + 1, songs.size)
            }
            val firstItem = toItem(songs[s])
            val beforeItems = before.map(::toItem)
            val restItems = rest.map(::toItem)
            val order = songs.map { it.id }
            withContext(Dispatchers.Main) {
                shuffleOn = shuf
                sourceOrder = order
                _state.update { it.copy(shuffle = shuf) }
                withController { c ->
                    c.shuffleModeEnabled = false
                    c.setMediaItem(firstItem)
                    c.prepare()
                    c.play()
                    if (beforeItems.isNotEmpty()) c.addMediaItems(0, beforeItems)
                    if (restItems.isNotEmpty()) c.addMediaItems(restItems)
                }
            }
        }
    }

    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        play(songs, Random.nextInt(songs.size), true)
    }

    /** Puts the saved queue back without starting playback (nothing is loaded until you press play). */
    fun restore(songs: List<Song>, index: Int, positionMs: Long) {
        if (songs.isEmpty()) return
        val s = index.coerceIn(0, songs.size - 1)
        scope.launch(Dispatchers.Default) {
            val first = toItem(songs[s])
            val before = if (s > 0) songs.subList(0, s).map(::toItem) else emptyList()
            val after = if (s < songs.size - 1) songs.subList(s + 1, songs.size).map(::toItem) else emptyList()
            withContext(Dispatchers.Main) {
                withController { c ->
                    if (c.mediaItemCount == 0) {
                        c.setMediaItem(first, positionMs)
                        if (before.isNotEmpty()) c.addMediaItems(0, before)
                        if (after.isNotEmpty()) c.addMediaItems(after)
                    }
                }
            }
        }
    }

    fun playNext(song: Song) = withController { c ->
        if (c.mediaItemCount == 0) {
            c.setMediaItem(toItem(song)); c.prepare(); c.play()
        } else c.addMediaItem(c.currentMediaItemIndex + 1, toItem(song))
    }

    fun enqueue(songs: List<Song>) = withController { c ->
        val empty = c.mediaItemCount == 0
        c.addMediaItems(songs.map(::toItem))
        if (empty) { c.prepare(); c.play() }
    }

    fun togglePlay() = withController { c ->
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE) c.pause()
        else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }

    /** Button behaviour: restarts the song if you're a few seconds in, otherwise goes to the previous song. */
    fun previous() = withController { it.seekToPrevious() }

    /** Swipe behaviour: always the previous song, however far into this one you are. */
    fun previousTrack() = withController { c ->
        if (c.hasPreviousMediaItem()) c.seekToPreviousMediaItem() else c.seekTo(0)
    }

    fun seekTo(ms: Long) = withController { it.seekTo(ms) }

    fun seekBy(deltaMs: Long) = withController { c ->
        val d = c.duration
        val t = (c.currentPosition + deltaMs).coerceAtLeast(0L)
        c.seekTo(if (d > 0) t.coerceAtMost(d) else t)
    }

    fun skipTo(index: Int) = withController { it.seekToDefaultPosition(index); it.play() }
    fun removeAt(index: Int) = withController { it.removeMediaItem(index) }
    fun move(from: Int, to: Int) = withController { it.moveMediaItem(from, to) }

    /** Shuffle re-orders the songs after the current one; turning it off restores the original order. */
    fun setShuffle(on: Boolean) = withController { c ->
        shuffleOn = on
        _state.update { it.copy(shuffle = on) }
        val n = c.mediaItemCount
        if (n <= 1) return@withController
        val cur = c.currentMediaItemIndex
        val items = List(n) { c.getMediaItemAt(it) }
        if (on) {
            if (cur >= n - 1) return@withController
            val tail = items.subList(cur + 1, n).shuffled()
            c.removeMediaItems(cur + 1, n)
            c.addMediaItems(tail)
        } else {
            val rank = HashMap<String, Int>()
            sourceOrder.forEachIndexed { i, id -> rank.putIfAbsent(id.toString(), i) }
            val desired = items.sortedBy { rank[it.mediaId] ?: Int.MAX_VALUE }
            val curId = items[cur].mediaId
            val pos = desired.indexOfFirst { it.mediaId == curId }.coerceAtLeast(0)
            val before = desired.subList(0, pos)
            val after = desired.subList(pos + 1, n)
            if (cur + 1 < n) c.removeMediaItems(cur + 1, n)
            if (cur > 0) c.removeMediaItems(0, cur)
            if (before.isNotEmpty()) c.addMediaItems(0, before)
            if (after.isNotEmpty()) c.addMediaItems(after)
        }
    }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Stops playback and empties the queue (swipe the mini player down). */
    fun stopAndClear() {
        withController { it.stop(); it.clearMediaItems() }
        store.saveQueue(emptyList())
        store.savePosition(0, 0L)
    }
}
