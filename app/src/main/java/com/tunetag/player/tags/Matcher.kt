package com.tunetag.player.tags

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object Matcher {
    private val parens = Regex("""\([^)]*\)|\[[^\]]*\]""")
    private val marks = Regex("\\p{Mn}+")
    private val feat = Regex("""\b(feat|ft|featuring)\b.*""")
    private val junk = Regex("[^\\p{L}\\p{N} ]")
    private val spaces = Regex("\\s+")

    fun norm(s: String): String {
        var t = s.lowercase().replace(parens, " ")
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replace(marks, "")
        t = t.replace("&", " and ").replace(feat, " ")
        return t.replace(junk, " ").replace(spaces, " ").trim()
    }

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    fun sim(a: String, b: String): Double {
        val x = norm(a)
        val y = norm(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        if (minOf(x.length, y.length) >= 4 && (x.contains(y) || y.contains(x))) return 0.88
        return 1.0 - lev(x, y).toDouble() / max(x.length, y.length)
    }

    /** 0..100 match score between what we know about the file and a provider result. */
    fun score(q: TrackQuery, c: TrackCandidate): Int {
        val t = sim(q.title, c.title)
        val a = if (q.artist.isBlank()) 0.5 else maxOf(sim(q.artist, c.artist), sim(q.artist, c.albumArtist ?: ""))
        val al = if (q.album.isBlank() || c.album == null) 0.5 else sim(q.album, c.album)
        val d = if (q.durationMs <= 0 || c.durationMs == null) 0.5 else {
            val diff = abs(q.durationMs - c.durationMs) / 1000.0
            when {
                diff <= 3 -> 1.0
                diff <= 8 -> 0.6
                diff <= 20 -> 0.2
                else -> 0.0
            }
        }
        return (100 * (0.5 * t + 0.25 * a + 0.10 * al + 0.15 * d)).roundToInt().coerceIn(0, 100)
    }
}
