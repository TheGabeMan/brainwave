package dev.gabrie.brainwave.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun BrainwaveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You: on Android 12+ the palette follows the user's wallpaper.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> BrainwaveDarkScheme
        else -> BrainwaveLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BrainwaveTypography,
        content = content,
    )
}
