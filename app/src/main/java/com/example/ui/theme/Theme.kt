package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandPrimaryGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD4EFE3),
    onPrimaryContainer = BrandPrimaryGreen,
    secondary = BrandBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE9FA),
    onSecondaryContainer = BrandBlue,
    tertiary = BrandSecondaryGreen,
    onTertiary = Color(0xFF06291D),
    tertiaryContainer = Color(0xFFDDF5EA),
    onTertiaryContainer = Color(0xFF0B3D29),
    error = BrandExpenseRed,
    onError = Color.White,
    errorContainer = Color(0xFFFDE4E2),
    onErrorContainer = Color(0xFF7A1E1E),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

private val DarkColors = darkColorScheme(
    primary = BrandSecondaryGreen,
    onPrimary = Color(0xFF07291D),
    primaryContainer = BrandPrimaryGreen,
    onPrimaryContainer = Color(0xFFD2F3E4),
    secondary = Color(0xFF7DB2F7),
    onSecondary = Color(0xFF03234F),
    secondaryContainer = Color(0xFF0A3978),
    onSecondaryContainer = Color(0xFFD7E8FF),
    tertiary = BrandAccentRed,
    onTertiary = Color(0xFF3D0E09),
    tertiaryContainer = Color(0xFF5D261F),
    onTertiaryContainer = Color(0xFFFFDFDB),
    error = Color(0xFFF28B82),
    onError = Color(0xFF470E0E),
    errorContainer = Color(0xFF6A1D1D),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)

val HisabNikashShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HisabNikashTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HisabNikashTypography,
        shapes = HisabNikashShapes,
        content = content
    )
}
