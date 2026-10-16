package com.tunetag.player.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.tunetag.player.tags.ArtCache
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Notification / lock-screen artwork. Loaded on a background thread so changing track never
 * waits on disk (the old version blocked the service's main thread).
 */
class ArtBitmapLoader(private val ctx: Context) : BitmapLoader {
    private val delegate = DataSourceBitmapLoader(ctx)
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor { r ->
        Thread(r, "tunetag-notif-art").apply { priority = Thread.MIN_PRIORITY + 1; isDaemon = true }
    })

    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (uri.scheme == "content" && uri.authority == "media") {
            return executor.submit(Callable<Bitmap> {
                ArtCache.loadBlocking(ctx, uri, 512, "n$uri") ?: throw IllegalStateException("no artwork")
            })
        }
        return delegate.loadBitmap(uri)
    }
}
