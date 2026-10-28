package com.pagecast.app.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F3E5C),
    secondary = Color(0xFF8A6A2F),
    tertiary = Color(0xFFF2B84B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5EA),
    secondary = Color(0xFFE5C17F),
    tertiary = Color(0xFFF2B84B),
)

/** 리더 화면의 문장/단어 하이라이트 색. 테마와 무관하게 대비가 확보되도록 따로 둔다. */
object ReaderColors {
    val sentenceLight = Color(0xFFFFF1C7)
    val sentenceDark = Color(0xFF4A3F1F)
}

@Composable
fun PagecastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = PagecastTypography, content = content)
}
