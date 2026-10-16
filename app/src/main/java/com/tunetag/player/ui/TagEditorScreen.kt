package com.tunetag.player.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tunetag.player.data.Song
import com.tunetag.player.tags.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TagEditorScreen(vm: AppViewModel, song: Song, autoStart: Boolean, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var original by remember(song.id) { mutableStateOf<TagData?>(null) }
    var tags by remember(song.id) { mutableStateOf(TagData()) }
    var artBytes by remember(song.id) { mutableStateOf<ByteArray?>(null) }
    var removeArt by remember(song.id) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var busyMsg by remember { mutableStateOf<String?>(null) }
    var sheet by remember { mutableStateOf(false) }
    var autoDone by remember { mutableStateOf(false) }
    var pendingPick by remember { mutableStateOf<TrackCandidate?>(null) }

    LaunchedEffect(song.id) {
        val t = withContext(Dispatchers.IO) { TagIO.read(song.path) }
        original = t
        tags = t.copy(
            title = t.title.ifBlank { song.title },
            artist = t.artist.ifBlank { if (song.artist == "Unknown artist") "" else song.artist },
            album = t.album.ifBlank { if (song.album == "Unknown album") "" else song.album },
        )
    }
    LaunchedEffect(original) {
        if (autoStart && original != null && !autoDone) { autoDone = true; sheet = true }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val b = ArtworkFetcher.fromUri(ctx, uri)
            if (b != null) { artBytes = b; removeArt = false } else snackbar.showSnackbar("Could not read that image")
        }
    }

    fun save() {
        scope.launch {
            saving = true
            val r = vm.saveTags(song, tags, artBytes, removeArt)
            saving = false
            if (r.isSuccess) { snackbar.showSnackbar("Tags saved"); onBack() }
            else snackbar.showSnackbar("Could not write tags: ${r.exceptionOrNull()?.message ?: "unknown error"}")
        }
    }

    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopAppBar(
                title = { Text("Edit tags", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background),
            )
            if (saving || busyMsg != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                busyMsg?.let { Text(it, Modifier.padding(horizontal = 20.dp, vertical = 4.dp), fontSize = 12.sp, color = cs.onSurfaceVariant) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                // artwork + actions
                Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceVariant.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        val pending = artBytes
                        val artMod = Modifier.size(180.dp)
                        if (pending != null) {
                            val bmp = remember(pending) { BitmapFactory.decodeByteArray(pending, 0, pending.size) }
                            if (bmp != null) Image(bmp.asImageBitmap(), null, artMod.clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
                        } else if (removeArt) {
                            Artwork(null, artMod, shape = RoundedCornerShape(24.dp))
                        } else {
                            Artwork(song, artMod, px = 540, shape = RoundedCornerShape(24.dp), exact = true)
                        }
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ sheet = true }, Modifier.weight(1f), shape = CircleShape, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Auto-tag", maxLines = 1)
                            }
                            FilledTonalButton({ picker.launch("image/*") }, Modifier.weight(1f), shape = CircleShape, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Rounded.Image, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Image", maxLines = 1)
                            }
                            FilledTonalButton({ artBytes = null; removeArt = true }, Modifier.weight(1f), shape = CircleShape, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Clear", maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                FieldCard("DETAILS") {
                    TextF("Title", tags.title) { tags = tags.copy(title = it) }
                    TextF("Artist", tags.artist) { tags = tags.copy(artist = it) }
                    TextF("Album", tags.album) { tags = tags.copy(album = it) }
                    TextF("Album artist", tags.albumArtist) { tags = tags.copy(albumArtist = it) }
                }
                Spacer(Modifier.height(14.dp))
                FieldCard("MORE") {
                    TextF("Genre", tags.genre) { tags = tags.copy(genre = it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextF("Year", tags.year, Modifier.weight(1f), number = true) { tags = tags.copy(year = it) }
                        TextF("Track", tags.track, Modifier.weight(1f), number = true) { tags = tags.copy(track = it) }
                        TextF("Disc", tags.disc, Modifier.weight(1f), number = true) { tags = tags.copy(disc = it) }
                    }
                    TextF("Composer", tags.composer) { tags = tags.copy(composer = it) }
                }
                Spacer(Modifier.height(14.dp))
                Text(song.path, Modifier.padding(horizontal = 8.dp), fontSize = 11.sp, color = cs.onSurfaceVariant)
                Spacer(Modifier.height(110.dp))
            }
        }
        ExtendedFloatingActionButton(
            onClick = { if (!saving) save() },
            icon = { Icon(Icons.Rounded.Check, null) },
            text = { Text("Save changes", fontWeight = FontWeight.SemiBold) },
            containerColor = cs.primary, contentColor = cs.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).navigationBarsPadding().imePadding()
        )
    }

    if (sheet) {
        AutoTagSheet(
            vm,
            TrackQuery(tags.title, tags.artist, tags.album, song.durationMs),
            onPick = { sheet = false; pendingPick = it },
            onDismiss = { sheet = false }
        )
    }

    pendingPick?.let { c ->
        ApplySheet(
            song = song, old = tags, cand = c,
            onDismiss = { pendingPick = null },
            onApply = { fields ->
                pendingPick = null
                scope.launch {
                    busyMsg = "Applying…"
                    val t = tags.applying(c, fields, onlyEmpty = false)
                    if (TagField.ARTWORK in fields && c.artworkUrl != null) {
                        busyMsg = "Downloading artwork…"
                        ArtworkFetcher.fetchJpeg(c.artworkUrl)?.let { artBytes = it; removeArt = false }
                    }
                    tags = t
                    busyMsg = null
                    snackbar.showSnackbar("Applied — review, then tap Save")
                }
            }
        )
    }
}

@Composable
private fun FieldCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun TextF(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), number: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default
    )
}

@Composable
fun ScoreBadge(score: Int) {
    val color = when {
        score >= 85 -> Color(0xFF2EBD59)
        score >= 70 -> Color(0xFFF5A524)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(Modifier.clip(CircleShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text("$score%", color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Searches every enabled provider and lets the user choose a result. Reused by single and batch tagging. */
@Composable
fun AutoTagSheet(vm: AppViewModel, initial: TrackQuery, onPick: (TrackCandidate) -> Unit, onDismiss: () -> Unit) {
    val settings = vm.user.collectAsState().value.settings
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(initial.title) }
    var artist by remember { mutableStateOf(initial.artist) }
    var results by remember { mutableStateOf<List<TrackCandidate>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun doSearch() {
        scope.launch {
            busy = true
            results = try { MetadataService.search(initial.copy(title = title, artist = artist), settings) } catch (_: Exception) { emptyList() }
            busy = false
        }
    }
    LaunchedEffect(Unit) { doSearch() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxHeight(0.92f).padding(horizontal = 16.dp)) {
            Text("Find metadata", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Apple Music • Deezer • MusicBrainz", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f))
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f))
                FilledIconButton({ doSearch() }) { Icon(Icons.Rounded.Search, "Search") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
            val list = results
            if (list != null && list.isEmpty() && !busy) {
                Text("No results. Try editing the title or artist, or enable more sources in Settings.",
                    Modifier.padding(top = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                items(list ?: emptyList()) { c -> CandidateRow(c) { onPick(c) } }
            }
        }
    }
}

@Composable
fun CandidateRow(c: TrackCandidate, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.7f)).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RemoteImage(c.artworkUrl?.replace("1200x1200bb", "200x200bb")?.replace("front-1200", "front-250"), Modifier.size(64.dp), RoundedCornerShape(14.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${c.artist}${c.album?.let { " • $it" } ?: ""}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp, color = cs.onSurfaceVariant)
            Text(listOfNotNull(c.year?.toString(), c.genre, c.trackNo?.let { "Track $it" }).joinToString(" • "),
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            ScoreBadge(c.score)
            Spacer(Modifier.height(4.dp))
            Text(c.provider, fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
    }
}

/** Review sheet: shows old -> new for each field so you choose exactly what to take from the result. */
@Composable
fun ApplySheet(song: Song, old: TagData, cand: TrackCandidate, onDismiss: () -> Unit, onApply: (Set<TagField>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val newTags = remember(old, cand) { old.applying(cand, TagField.values().toSet(), false) }
    fun oldOf(f: TagField): String = when (f) {
        TagField.TITLE -> old.title; TagField.ARTIST -> old.artist; TagField.ALBUM -> old.album
        TagField.ALBUM_ARTIST -> old.albumArtist; TagField.GENRE -> old.genre; TagField.YEAR -> old.year
        TagField.TRACK -> old.track; TagField.DISC -> old.disc; TagField.ARTWORK -> ""
    }
    fun newOf(f: TagField): String = when (f) {
        TagField.TITLE -> newTags.title; TagField.ARTIST -> newTags.artist; TagField.ALBUM -> newTags.album
        TagField.ALBUM_ARTIST -> newTags.albumArtist; TagField.GENRE -> newTags.genre; TagField.YEAR -> newTags.year
        TagField.TRACK -> newTags.track; TagField.DISC -> newTags.disc
        TagField.ARTWORK -> if (cand.artworkUrl != null) "x" else ""
    }
    val fields = TagField.values().filter { it != TagField.ARTWORK && newOf(it).isNotBlank() }
    var chosen by remember {
        mutableStateOf(
            (fields.filter { newOf(it) != oldOf(it) } + (if (cand.artworkUrl != null) listOf(TagField.ARTWORK) else emptyList())).toSet()
        )
    }
    fun toggle(f: TagField) { chosen = if (f in chosen) chosen - f else chosen + f }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Review changes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("From ${cand.provider}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                ScoreBadge(cand.score)
            }
            Spacer(Modifier.height(14.dp))

            if (cand.artworkUrl != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceVariant.copy(alpha = 0.7f))
                        .clickable { toggle(TagField.ARTWORK) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(TagField.ARTWORK in chosen, { toggle(TagField.ARTWORK) })
                    Text("Artwork", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Artwork(song, Modifier.size(56.dp), px = 168, shape = RoundedCornerShape(12.dp), exact = true)
                    Icon(Icons.Rounded.ArrowForward, null, Modifier.padding(horizontal = 8.dp), tint = cs.onSurfaceVariant)
                    RemoteImage(cand.artworkUrl.replace("1200x1200bb", "200x200bb").replace("front-1200", "front-250"), Modifier.size(56.dp), RoundedCornerShape(12.dp))
                }
                Spacer(Modifier.height(10.dp))
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceVariant.copy(alpha = 0.7f)).padding(vertical = 6.dp)
            ) {
                fields.forEach { f ->
                    val o = oldOf(f)
                    val n = newOf(f)
                    Row(Modifier.fillMaxWidth().clickable { toggle(f) }.padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(f in chosen, { toggle(f) })
                        Column(Modifier.weight(1f)) {
                            Text(f.label, fontSize = 11.sp, color = cs.onSurfaceVariant)
                            Text(n, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (o.isNotBlank() && o != n) {
                                Text(o, fontSize = 12.sp, color = cs.onSurfaceVariant, textDecoration = TextDecoration.LineThrough,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            } else if (o == n) {
                                Text("unchanged", fontSize = 12.sp, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button({ onApply(chosen) }, Modifier.fillMaxWidth().height(52.dp), shape = CircleShape, enabled = chosen.isNotEmpty()) {
                Text("Apply ${chosen.size} change${if (chosen.size == 1) "" else "s"}", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.navigationBarsPadding().height(20.dp))
        }
    }
}
