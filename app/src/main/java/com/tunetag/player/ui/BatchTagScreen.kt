package com.tunetag.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunetag.player.data.Song
import com.tunetag.player.tags.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BatchItem(val song: Song) {
    var old by mutableStateOf(TagData())
    var cand by mutableStateOf<TrackCandidate?>(null)
    var checked by mutableStateOf(false)
    var state by mutableIntStateOf(0) // 0 waiting, 1 searching, 2 matched, 3 no match, 4 written, 5 failed
    var message by mutableStateOf("")
}

@Composable
fun BatchTagScreen(vm: AppViewModel, songs: List<Song>, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val settings = vm.user.collectAsState().value.settings
    val batch = remember { songs.map { BatchItem(it) } }
    var running by remember { mutableStateOf(true) }
    var applying by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }
    var summary by remember { mutableStateOf<String?>(null) }
    var onlyEmpty by remember { mutableStateOf(false) }
    var fields by remember {
        mutableStateOf(setOf(TagField.ALBUM, TagField.ALBUM_ARTIST, TagField.GENRE, TagField.YEAR, TagField.TRACK, TagField.DISC, TagField.ARTWORK))
    }
    var chooseFor by remember { mutableStateOf<BatchItem?>(null) }

    LaunchedEffect(Unit) {
        for (item in batch) {
            item.state = 1
            val old = withContext(Dispatchers.IO) { TagIO.read(item.song.path) }
            item.old = old
            val q = TrackQuery(
                old.title.ifBlank { item.song.title },
                old.artist.ifBlank { if (item.song.artist == "Unknown artist") "" else item.song.artist },
                old.album.ifBlank { if (item.song.album == "Unknown album") "" else item.song.album },
                item.song.durationMs
            )
            val merged = try { MetadataService.merged(MetadataService.search(q, settings)) } catch (_: Exception) { null }
            item.cand = merged
            item.checked = merged != null && merged.score >= settings.minScore
            item.state = if (merged == null) 3 else 2
            done++
        }
        running = false
    }

    fun applyAll() {
        scope.launch {
            applying = true
            done = 0
            val todo = batch.filter { it.checked && it.cand != null }
            val written = ArrayList<String>()
            var ok = 0
            var failed = 0
            for (item in todo) {
                val c = item.cand!!
                val r = withContext(Dispatchers.IO) {
                    runCatching {
                        val t = item.old.applying(c, fields, onlyEmpty)
                        var art: ByteArray? = null
                        if (TagField.ARTWORK in fields && c.artworkUrl != null &&
                            !(onlyEmpty && TagIO.hasArtwork(item.song.path))
                        ) art = ArtworkFetcher.fetchJpeg(c.artworkUrl)
                        TagIO.write(item.song.path, t, art, false).getOrThrow()
                    }
                }
                if (r.isSuccess) { item.state = 4; ok++; written.add(item.song.path) }
                else { item.state = 5; item.message = r.exceptionOrNull()?.message ?: "write failed"; failed++ }
                done++
            }
            vm.rescan(written)
            applying = false
            summary = "Updated $ok file${if (ok == 1) "" else "s"}" + if (failed > 0) ", $failed failed" else ""
        }
    }

    Column(Modifier.fillMaxSize().background(cs.background)) {
        TopAppBar(
            title = {
                Column {
                    Text("Auto-tag", fontWeight = FontWeight.Bold)
                    Text("${songs.size} songs", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
        )

        // status card
        Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    summary ?: if (running) "Searching… $done of ${batch.size}" else if (applying) "Writing tags… $done of ${batch.count { it.checked }}"
                    else "${batch.count { it.cand != null }} matches found — review and apply",
                    fontWeight = FontWeight.SemiBold
                )
                if (running || applying) {
                    Spacer(Modifier.height(10.dp))
                    val total = if (running) batch.size else batch.count { it.checked }.coerceAtLeast(1)
                    LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TagField.values().toList()) { f ->
                FilterChip(
                    selected = f in fields,
                    onClick = { fields = if (f in fields) fields - f else fields + f },
                    label = { Text(f.label) }
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(onlyEmpty, { onlyEmpty = it })
            Spacer(Modifier.width(10.dp))
            Text("Only fill empty fields", fontSize = 14.sp)
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(batch) { item ->
                val c = item.cand
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(22.dp))
                        .background(cs.surfaceVariant.copy(alpha = 0.7f))
                        .clickable { chooseFor = item }.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(item.checked, { item.checked = it }, enabled = c != null)
                    Artwork(item.song, Modifier.size(46.dp), px = 138, shape = RoundedCornerShape(12.dp))
                    if (c != null) {
                        Icon(Icons.Rounded.ArrowForward, null, Modifier.padding(horizontal = 6.dp).size(16.dp), tint = cs.onSurfaceVariant)
                        RemoteImage(c.artworkUrl?.replace("1200x1200bb", "200x200bb")?.replace("front-1200", "front-250"),
                            Modifier.size(46.dp), RoundedCornerShape(12.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = cs.onSurfaceVariant)
                        Text(
                            when {
                                item.state == 5 -> "Failed: ${item.message}"
                                item.state == 4 -> "Written ✓"
                                item.state == 1 -> "Searching…"
                                item.state == 0 -> "Waiting"
                                c == null -> "No match — tap to search"
                                else -> c.title
                            },
                            maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            color = if (item.state == 5) cs.error else cs.onSurface
                        )
                        if (c != null && item.state == 2) {
                            Text("${c.artist}${c.album?.let { " • $it" } ?: ""}${c.year?.let { " ($it)" } ?: ""}",
                                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                    }
                    if (c != null) ScoreBadge(c.score)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton({ batch.forEach { it.checked = it.cand != null } }, Modifier.weight(1f).height(52.dp), shape = CircleShape) {
                Text("Select all")
            }
            Button(
                onClick = { applyAll() },
                enabled = !running && !applying && batch.any { it.checked },
                modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape
            ) { Text("Apply (${batch.count { it.checked }})", fontWeight = FontWeight.SemiBold) }
        }
    }

    chooseFor?.let { item ->
        AutoTagSheet(
            vm,
            TrackQuery(item.old.title.ifBlank { item.song.title }, item.old.artist.ifBlank { item.song.artist }, item.old.album, item.song.durationMs),
            onPick = { item.cand = it; item.checked = true; item.state = 2; chooseFor = null },
            onDismiss = { chooseFor = null }
        )
    }
}
