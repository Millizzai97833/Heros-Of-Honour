package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorContainerGold = androidx.compose.ui.graphics.Color(0xFF3A2A0A)

private val TacticalDarkColorScheme = darkColorScheme(
    primary = AmberGold,
    onPrimary = TacticalBlack,
    primaryContainer = ColorContainerGold,
    onPrimaryContainer = AmberGoldBright,
    secondary = TacticalCyan,
    onSecondary = TacticalBlack,
    secondaryContainer = GunmetalElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = ShieldEmerald,
    onTertiary = TacticalBlack,
    background = TacticalBlack,
    onBackground = TextPrimary,
    surface = GunmetalDark,
    onSurface = TextPrimary,
    surfaceVariant = GunmetalSurface,
    onSurfaceVariant = TextSecondary,
    outline = ArmorBorder,
    error = CrimsonAlert,
    onError = TextPrimary
)

val TacticalShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun HerosOfHonourTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TacticalDarkColorScheme,
        typography = HerosTypography,
        shapes = TacticalShapes,
        content = content
    )
}
