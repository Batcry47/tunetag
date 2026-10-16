package com.tunetag.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunetag.player.TuneTagApp
import com.tunetag.player.data.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

private fun fmtMinutes(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60} hr ${m % 60} min" else "$m min"
}

/** A "Wrapped"-style summary built from the private listening history kept by the player service. */
@Composable
fun StatsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val lib by vm.library.collectAsState()
    val cs = MaterialTheme.colorScheme
    var period by remember { mutableIntStateOf(1) } // 0 this month, 1 this year, 2 all time
    var stats by remember { mutableStateOf<Stats?>(null) }

    LaunchedEffect(period) {
        stats = null
        stats = withContext(Dispatchers.IO) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val from = when (period) {
                0 -> { cal.set(Calendar.DAY_OF_MONTH, 1); cal.timeInMillis }
                1 -> { cal.set(Calendar.DAY_OF_YEAR, 1); cal.timeInMillis }
                else -> 0L
            }
            try { TuneTagApp.instance.history.stats(from, System.currentTimeMillis() + 1) } catch (_: Exception) { Stats() }
        }
    }

    LargePage("Your Listening", onBack = onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("This month", "This year", "All time").forEachIndexed { i, label ->
                    FilterChip(period == i, { period = i }, label = { Text(label) })
                }
            }
            val st = stats
            if (st == null) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (st.totalMs < 5000) {
                EmptyState("Nothing here yet. Listen to some music and your stats will appear.")
            } else {
                // headline card
                Box(
                    Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFA2D48), Color(0xFF8E2DE2))))
                        .padding(24.dp)
                ) {
                    Column {
                        Text("You listened for", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                        Text(fmtMinutes(st.totalMs), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("${st.plays} plays • ${st.songs} different songs", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
                    }
                }

                val topArtist = st.topArtists.firstOrNull()
                if (topArtist != null) {
                    val art = lib.songs.firstOrNull { it.artist == topArtist.name }
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(art, Modifier.size(84.dp), px = 252, shape = CircleShape)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Top artist", fontSize = 13.sp, color = cs.primary, fontWeight = FontWeight.SemiBold)
                            Text(topArtist.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(fmtMinutes(topArtist.ms), fontSize = 14.sp, color = cs.onSurfaceVariant)
                        }
                    }
                }

                SectionTitle("Top songs")
                st.topSongs.forEachIndexed { i, t ->
                    val song = lib.byId[t.songId]
                    StatRow(i + 1, { Artwork(song, Modifier.size(48.dp), px = 144) }, t.title, t.artist,
                        "${t.plays} plays • ${fmtMinutes(t.ms)}")
                }

                SectionTitle("Top artists")
                st.topArtists.forEachIndexed { i, a ->
                    val art = lib.songs.firstOrNull { it.artist == a.name }
                    StatRow(i + 1, { Artwork(art, Modifier.size(48.dp), px = 144, shape = CircleShape) }, a.name, "", fmtMinutes(a.ms))
                }

                SectionTitle("Top albums")
                st.topAlbums.forEachIndexed { i, a ->
                    val art = lib.songs.firstOrNull { it.album == a.name && it.artist == a.sub }
                    StatRow(i + 1, { Artwork(art, Modifier.size(48.dp), px = 144) }, a.name, a.sub, fmtMinutes(a.ms))
                }

                if (st.topGenres.isNotEmpty()) {
                    SectionTitle("Top genres")
                    val max = st.topGenres.maxOf { it.ms }.coerceAtLeast(1L)
                    st.topGenres.forEach { g ->
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                            Row {
                                Text(g.name, Modifier.weight(1f), fontSize = 16.sp)
                                Text(fmtMinutes(g.ms), fontSize = 14.sp, color = cs.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(cs.surfaceVariant)) {
                                Box(Modifier.fillMaxWidth(g.ms.toFloat() / max).fillMaxHeight().clip(CircleShape).background(cs.primary))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(LocalBottomPad.current + 24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun StatRow(rank: Int, leading: @Composable () -> Unit, title: String, subtitle: String, detail: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", Modifier.width(26.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = cs.primary)
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp)
            Text(
                if (subtitle.isBlank()) detail else "$subtitle • $detail",
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = cs.onSurfaceVariant
            )
        }
    }
}
