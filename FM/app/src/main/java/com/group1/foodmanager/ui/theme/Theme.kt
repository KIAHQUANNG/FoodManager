package com.group1.foodmanager.ui.theme

import android.app.Activity
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



    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */


private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFD700),      // 金色主色 (Gold)
    secondary = Color(0xFFFFC107),    // 琥珀金 (更亮一点的金色)
    background = Color(0xFF121212),   // 典型深黑背景
    surface = Color(0xFF1E1E1E),      // 深灰表面
    onPrimary = Color.Black,          // 金色按钮上的文字 → 黑色对比
    onSecondary = Color.Black,        // 琥珀金按钮上的文字 → 黑色对比
    onBackground = Color(0xFFE0E0E0), // 深黑背景上的文字 → 浅灰
    onSurface = Color(0xFFE0E0E0)     // 深灰表面上的文字 → 浅灰
)


private val LightColorScheme = lightColorScheme(
    primary = GoldPrimary,
    secondary = GoldSecondary,
    background = Color(0xFFFFFDE7), // 米白偏金色背景
    surface = Color(0xFFFFF9C4),    // 浅金色表面
    onPrimary = OnGoldPrimary,
    onSecondary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

@Composable
fun FoodManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 黑金风就不要用动态色了
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LightColorScheme else  DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}