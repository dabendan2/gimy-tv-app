package com.gimytv.horror.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CharcoalDark = Color(0xFF121212)
val CharcoalCard = Color(0xFF2C2C2C)
val GimyGreen = Color(0xFF4CAF50)
val TextWhite = Color(0xFFFFFFFF)
val TextGray = Color(0xFFAAAAAA)
val CardBorderFocus = Color(0xFF81C784)

private val TvDarkColorScheme = darkColorScheme(
    primary = GimyGreen,
    onPrimary = Color.Black,
    surface = CharcoalCard,
    onSurface = TextWhite,
    background = CharcoalDark,
    onBackground = TextWhite
)

@Composable
fun GimyTvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        content = content
    )
}
