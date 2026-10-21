package com.virtualrun.app.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

internal val IslandInk = Color(0xFF303839)
internal val IslandAccent = Color(0xFFF0CD88)
internal val IslandMuted = Color(0xFFC4CAC6)
internal val IslandText = Color(0xFFF5F2EB)
internal val IslandRaised = Color(0xFF414B4C)

/** A shared palette for the island and the dialogs opened from it. */
internal val IslandDialogColors = darkColorScheme(
    primary = IslandAccent,
    onPrimary = IslandInk,
    primaryContainer = Color(0xFF665A3E),
    onPrimaryContainer = IslandAccent,
    secondary = IslandAccent,
    onSecondary = IslandInk,
    secondaryContainer = Color(0xFF665A3E),
    onSecondaryContainer = IslandAccent,
    background = IslandInk,
    onBackground = IslandText,
    surface = IslandInk,
    onSurface = IslandText,
    surfaceVariant = IslandRaised,
    onSurfaceVariant = IslandMuted,
    outline = Color(0xFF8F9B94),
    outlineVariant = Color(0xFF556261),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF380D0A)
)
