package com.example.xiaonao

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 小脑 Little Brain 主题 ——「柔和脑感蓝」。
 *
 * 设计取向：低饱和蓝/青做主色，圆角、柔和阴影，友好不拘谨，契合「小脑」轻量定位。
 * 其余色（分类色点、成功/警告/危险）在各自组件里单独取用，不走主题，避免互相打架。
 */

// —— 主色：柔和脑感蓝 ——
private val BrainBlue = Color(0xFF4A6CF7)       // 主色（按钮、选中态、进度）
private val BrainBlueDark = Color(0xFF3450C8)   // 深一档
private val BrainTeal = Color(0xFF3AA6A0)       // 辅色（青）
private val BrainCyan = Color(0xFF6FD3D8)       // 亮青点缀
private val BrainBg = Color(0xFFF6F8FF)         // 背景：极浅蓝白
private val BrainSurface = Color(0xFFFFFFFF)    // 卡片面
private val BrainSurfaceTint = Color(0xFFEDF1FF)// 浅蓝容器
private val BrainOnSurface = Color(0xFF1B1B2F)  // 主文字
private val BrainOutline = Color(0xFFD5DCF0)    // 描边

// —— 深色主题 ——（跟随系统，保持同一色相）
private val DBg = Color(0xFF0F1220)
private val DSurface = Color(0xFF191D30)
private val DSurfaceTint = Color(0xFF232A45)
private val DOnSurface = Color(0xFFE6E9F5)
private val DOutline = Color(0xFF39405C)

private val LightColors = lightColorScheme(
    primary = BrainBlue,
    onPrimary = Color.White,
    primaryContainer = BrainSurfaceTint,
    onPrimaryContainer = BrainBlueDark,
    secondary = BrainTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF3F1),
    onSecondaryContainer = Color(0xFF12504C),
    tertiary = BrainCyan,
    background = BrainBg,
    onBackground = BrainOnSurface,
    surface = BrainSurface,
    onSurface = BrainOnSurface,
    surfaceVariant = BrainSurfaceTint,
    onSurfaceVariant = Color(0xFF5A6178),
    outline = BrainOutline,
    outlineVariant = Color(0xFFE6EAF7),
    error = Color(0xFFE5484D),
    surfaceTint = BrainBlue
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB2FF),
    onPrimary = Color(0xFF17204A),
    primaryContainer = DSurfaceTint,
    onPrimaryContainer = Color(0xFFD7E0FF),
    secondary = Color(0xFF7FD3CD),
    onSecondary = Color(0xFF0A3B38),
    secondaryContainer = Color(0xFF1D4442),
    onSecondaryContainer = Color(0xFFC7EFEB),
    tertiary = BrainCyan,
    background = DBg,
    onBackground = DOnSurface,
    surface = DSurface,
    onSurface = DOnSurface,
    surfaceVariant = DSurfaceTint,
    onSurfaceVariant = Color(0xFFB9C0D6),
    outline = DOutline,
    outlineVariant = Color(0xFF2A3150),
    error = Color(0xFFFF6B6F),
    surfaceTint = Color(0xFF9DB2FF)
)

@Composable
fun XiaoNaoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
