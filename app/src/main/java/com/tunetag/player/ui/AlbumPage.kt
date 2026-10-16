package com.tunetag.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.tunetag.player.data.Album

/**
 * Apple Music style album page: the cover fills the top edge-to-edge (behind the status bar), melts into a blurred
 * copy of itself with the album details floating over it, and ends right above the Play / Shuffle row.
 */
@Composable
fun AlbumPage(vm: AppViewModel, album: Album, actions: SongActions, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val currentId by vm.currentId.collectAsState()
    val first = album.songs.first()
    val tiny by rememberTinyArt(first)
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val dark = cs.background.luminance() < 0.5f
    val mins = album.songs.sumOf { it.durationMs } / 60000

    Box(Modifier.fillMaxSize().background(cs.background)) {
        SongList(
            album.songs, actions, currentId, Modifier.fillMaxSize(), showTrackNo = true,
            header = {
                Column {
                    Box(Modifier.fillMaxWidth().height(screenW)) {
                        // ambient layer: the cover scaled up from 32px and darkened
                        BlurredArt(tiny, Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                        // sharp cover, full width, fading out at the bottom into the ambient layer
                        Artwork(first, Modifier.fillMaxSize().fadeBottom(0.5f), px = 1000, shape = RectangleShape, exact = true)
                        // in dark mode the whole header dissolves into the page background
                        if (dark) {
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(0.78f to Color.Transparent, 1f to cs.background)
                                )
                            )
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                            Text(album.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(album.artist, color = Color.White.copy(alpha = 0.9f), fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                (if (album.year > 0) "${album.year} • " else "") + "${album.songs.size} song${if (album.songs.size == 1) "" else "s"} • $mins min",
                                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
                            )
                        }
                    }
                    PlayShuffleRow(album.songs.size, { actions.playInOrder(album.songs) }, { actions.shuffle(album.songs) })
                }
            }
        )

        // floating buttons over the cover
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.weight(1f))
            RoundIconButton(Icons.Rounded.AutoFixHigh, "Auto-tag album") { actions.autoTag(album.songs) }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Rounded.Settings, "Settings") { actions.push(Route.Settings) }
        }
    }
}

@Composable
private fun RoundIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.3f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, desc, tint = Color.White, modifier = Modifier.size(22.dp)) }
}
