package com.tunetag.player.tags

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val UA = "TuneTag/0.1 (local music player; tag lookup)"

    fun get(url: String, headers: Map<String, String> = emptyMap()): String? {
        val rb = Request.Builder().url(url).header("User-Agent", UA)
        headers.forEach { (k, v) -> rb.header(k, v) }
        client.newCall(rb.build()).execute().use { r ->
            return if (r.isSuccessful) r.body?.string() else null
        }
    }

    fun getBytes(url: String): ByteArray? {
        val rb = Request.Builder().url(url).header("User-Agent", UA)
        client.newCall(rb.build()).execute().use { r ->
            return if (r.isSuccessful) r.body?.bytes() else null
        }
    }

    fun postForm(url: String, form: Map<String, String>, headers: Map<String, String> = emptyMap()): String? {
        val fb = FormBody.Builder()
        form.forEach { (k, v) -> fb.add(k, v) }
        val rb = Request.Builder().url(url).header("User-Agent", UA).post(fb.build())
        headers.forEach { (k, v) -> rb.header(k, v) }
        client.newCall(rb.build()).execute().use { r ->
            return if (r.isSuccessful) r.body?.string() else null
        }
    }
}
