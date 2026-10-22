package com.obpiub.myapplication.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.obpiub.myapplication.domain.model.AccentColor
import com.obpiub.myapplication.domain.model.AppSettings
import com.obpiub.myapplication.domain.model.ThemeMode

private data class AccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
)

@Composable
fun MyApplicationTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val accent = when (settings.accent) {
        AccentColor.Purple -> if (dark)
            AccentPalette(Color(0xFFD0BCFF), Color(0xFF381E72), Color(0xFF4F378B), Color(0xFFEADDFF))
        else AccentPalette(Color(0xFF6750A4), Color.White, Color(0xFFEADDFF), Color(0xFF21005D))
        AccentColor.Blue -> if (dark)
            AccentPalette(Color(0xFFA8C7FA), Color(0xFF062E6F), Color(0xFF0842A0), Color(0xFFD3E3FD))
        else AccentPalette(Color(0xFF0B57D0), Color.White, Color(0xFFD3E3FD), Color(0xFF041E49))
        AccentColor.Green -> if (dark)
            AccentPalette(Color(0xFF7DDAA5), Color(0xFF00391F), Color(0xFF00522E), Color(0xFF99F7BF))
        else AccentPalette(Color(0xFF006C3E), Color.White, Color(0xFF99F7BF), Color(0xFF002110))
    }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = accent.primary, onPrimary = accent.onPrimary,
        primaryContainer = accent.container, onPrimaryContainer = accent.onContainer,
        secondary = accent.primary, onSecondary = accent.onPrimary,
        secondaryContainer = accent.container, onSecondaryContainer = accent.onContainer,
        tertiary = accent.primary, onTertiary = accent.onPrimary,
        tertiaryContainer = accent.container, onTertiaryContainer = accent.onContainer,
        surfaceTint = accent.primary,
    )
    MaterialTheme(colorScheme = colors, content = content)
}
