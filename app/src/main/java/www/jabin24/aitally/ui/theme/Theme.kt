package www.jabin24.aitally.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TallyPrimaryDark,
    onPrimary = TallyOnPrimaryDark,
    primaryContainer = TallyPrimaryContainerDark,
    onPrimaryContainer = TallyOnPrimaryContainerDark,
    secondary = TallySecondaryDark,
    tertiary = TallyTertiaryDark,
    background = TallyBackgroundDark,
    surface = TallySurfaceDark,
    onSurface = TallyOnSurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = TallyPrimary,
    onPrimary = TallyOnPrimary,
    primaryContainer = TallyPrimaryContainer,
    onPrimaryContainer = TallyOnPrimaryContainer,
    secondary = TallySecondary,
    onSecondary = TallyOnSecondary,
    secondaryContainer = TallySecondaryContainer,
    onSecondaryContainer = TallyOnSecondaryContainer,
    tertiary = TallyTertiary,
    onTertiary = TallyOnTertiary,
    tertiaryContainer = TallyTertiaryContainer,
    onTertiaryContainer = TallyOnTertiaryContainer,
    background = Color(0xFFFCFAF8),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1E1B1B),
    onSurface = Color(0xFF1E1B1B),
    surfaceVariant = Color(0xFFF4ECE8),
    onSurfaceVariant = Color(0xFF524341),
    outline = Color(0xFF857371)
)

@Composable
fun TallyKhataTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep TallyKhata distinctive red identity by default
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Alias for backward compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = TallyKhataTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
