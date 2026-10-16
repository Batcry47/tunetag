package com.tunetag.player.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tunetag.player.data.AppSettings

/**
 * Apple Music look: pure white / pure black backgrounds, iOS-style grays and the signature red accent.
 * Fixed on purpose: the app never re-themes itself per track (that would recompose the whole UI).
 */
@Composable
fun TuneTagTheme(s: AppSettings, content: @Composable () -> Unit) {
    val dark = when (s.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFFFA2D48), onPrimary = Color.White,
            primaryContainer = Color(0xFF5C0F1B), onPrimaryContainer = Color(0xFFFFD9DD),
            secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color.White,
            background = Color.Black, onBackground = Color.White,
            surface = Color.Black, onSurface = Color.White,
            surfaceVariant = Color(0xFF1C1C1E), onSurfaceVariant = Color(0xFF98989F),
            outline = Color(0xFF38383A),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFFFA2D48), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDADD), onPrimaryContainer = Color(0xFF410009),
            secondaryContainer = Color(0xFFE5E5EA), onSecondaryContainer = Color.Black,
            background = Color.White, onBackground = Color.Black,
            surface = Color.White, onSurface = Color.Black,
            surfaceVariant = Color(0xFFF2F2F7), onSurfaceVariant = Color(0xFF8A8A8E),
            outline = Color(0xFFC6C6C8),
        )
    }
    val shapes = Shapes(
        small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
    )
    MaterialTheme(colorScheme = scheme, shapes = shapes, typography = Typography(), content = content)
}
