package com.rork.hollowmarch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Hollowmarch ink-and-ochre palette. Charcoal canvas, parchment ink, three sparing accents. */
object Ink {
    val Canvas: Color = Color(0xFF12100D)
    val Surface: Color = Color(0xFF201B15)
    val Overlay: Color = Color(0xFF1A1611)
    val Hairline: Color = Color(0xFF3A3128)
    val Parchment: Color = Color(0xFFE6DAC0)
    val Faded: Color = Color(0xFF9A8D77)
    val Dim: Color = Color(0xFF5E5546)
    val Brass: Color = Color(0xFFC8952F)
    val Verdigris: Color = Color(0xFF4E7A6B)
    val Blood: Color = Color(0xFFA33B28)
}

private val HollowmarchColors = darkColorScheme(
    primary = Ink.Brass,
    onPrimary = Color(0xFF14110C),
    secondary = Ink.Verdigris,
    onSecondary = Ink.Parchment,
    tertiary = Ink.Blood,
    onTertiary = Ink.Parchment,
    background = Ink.Canvas,
    onBackground = Ink.Parchment,
    surface = Ink.Surface,
    onSurface = Ink.Parchment,
    surfaceVariant = Ink.Overlay,
    onSurfaceVariant = Ink.Faded,
    outline = Ink.Hairline,
    outlineVariant = Ink.Hairline,
    error = Ink.Blood,
    onError = Ink.Parchment
)

/** Monospaced digits carry every date, count and coordinate in the chronicle voice. */
val MonoStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    color = Ink.Parchment
)

val EngravedTitle: TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Bold,
    fontSize = 34.sp,
    letterSpacing = 8.sp,
    color = Ink.Parchment
)

val SectionLabel: TextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    letterSpacing = 2.4.sp,
    color = Ink.Faded
)

private val HollowmarchTypography = Typography(
    displayLarge = EngravedTitle,
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 2.sp,
        color = Ink.Parchment
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = 0.6.sp,
        color = Ink.Parchment
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Ink.Parchment
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = Ink.Faded
    ),
    labelSmall = SectionLabel
)

private val HollowmarchShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(5.dp),
    extraLarge = RoundedCornerShape(6.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HollowmarchColors,
        typography = HollowmarchTypography,
        shapes = HollowmarchShapes,
        content = content
    )
}
