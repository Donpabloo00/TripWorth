package com.ridego.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RideBlack = Color(0xFF0B0B0D)
val RideSurface = Color(0xFF16171A)
val RideSurfaceHigh = Color(0xFF202226)
val RideYellow = Color(0xFFFFC400)
val RideWhite = Color(0xFFF5F6F7)
val RideGray = Color(0xFF9BA0A6)
val RideGreen = Color(0xFF25C368)
val RideRed = Color(0xFFE23D3D)
val RideOrange = Color(0xFFFF9427)

private val RideColorScheme = darkColorScheme(
    primary = RideYellow,
    onPrimary = RideBlack,
    secondary = RideYellow,
    background = RideBlack,
    onBackground = RideWhite,
    surface = RideSurface,
    onSurface = RideWhite,
    surfaceVariant = RideSurfaceHigh,
    onSurfaceVariant = RideGray,
    error = RideRed
)

private val RideTypography = Typography(
    displayLarge = TextStyle(fontSize = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
)

@Composable
fun RideGoTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // RideGo is dark-only by design — drivers use it at night, over other apps.
    MaterialTheme(
        colorScheme = RideColorScheme,
        typography = RideTypography,
        content = content
    )
}
