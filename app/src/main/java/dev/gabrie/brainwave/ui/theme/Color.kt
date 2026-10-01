package dev.gabrie.brainwave.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Fallback palette for devices without dynamic color (below Android 12).
// A Material 3 tonal palette generated from a violet source colour.
private val Violet40 = Color(0xFF6750A4)
private val Violet80 = Color(0xFFD0BCFF)
private val Violet90 = Color(0xFFEADDFF)
private val Violet10 = Color(0xFF21005D)
private val Violet20 = Color(0xFF381E72)
private val Violet30 = Color(0xFF4F378B)

private val Teal40 = Color(0xFF00696E)
private val Teal80 = Color(0xFF4FD8DF)
private val Teal90 = Color(0xFF97F0F5)
private val Teal10 = Color(0xFF002022)
private val Teal30 = Color(0xFF004F52)

private val Rose40 = Color(0xFF7D5260)
private val Rose80 = Color(0xFFEFB8C8)
private val Rose90 = Color(0xFFFFD8E4)
private val Rose10 = Color(0xFF31111D)
private val Rose30 = Color(0xFF633B48)

private val Neutral10 = Color(0xFF1D1B20)
private val Neutral90 = Color(0xFFE6E0E9)
private val Neutral95 = Color(0xFFF5EFF7)
private val Neutral99 = Color(0xFFFFFBFE)
private val NeutralVariant30 = Color(0xFF49454F)
private val NeutralVariant50 = Color(0xFF79747E)
private val NeutralVariant80 = Color(0xFFCAC4D0)
private val NeutralVariant90 = Color(0xFFE7E0EC)

private val Error40 = Color(0xFFB3261E)
private val Error80 = Color(0xFFF2B8B5)
private val Error90 = Color(0xFFF9DEDC)
private val Error10 = Color(0xFF410E0B)

/** Success accent for the swipe-to-complete affordance. */
val CompleteGreen = Color(0xFF2E6B4F)
val CompleteGreenContainer = Color(0xFFB8F0CF)

val BrainwaveLightScheme = lightColorScheme(
    primary = Violet40,
    onPrimary = Color.White,
    primaryContainer = Violet90,
    onPrimaryContainer = Violet10,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal90,
    onSecondaryContainer = Teal10,
    tertiary = Rose40,
    onTertiary = Color.White,
    tertiaryContainer = Rose90,
    onTertiaryContainer = Rose10,
    error = Error40,
    onError = Color.White,
    errorContainer = Error90,
    onErrorContainer = Error10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
)

val BrainwaveDarkScheme = darkColorScheme(
    primary = Violet80,
    onPrimary = Violet20,
    primaryContainer = Violet30,
    onPrimaryContainer = Violet90,
    secondary = Teal80,
    onSecondary = Teal10,
    secondaryContainer = Teal30,
    onSecondaryContainer = Teal90,
    tertiary = Rose80,
    onTertiary = Rose10,
    tertiaryContainer = Rose30,
    onTertiaryContainer = Rose90,
    error = Error80,
    onError = Error10,
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Error90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant30,
)

val LightSurfaceTint = Neutral95
