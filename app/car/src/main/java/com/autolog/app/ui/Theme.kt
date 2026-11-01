package com.autolog.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autolog.app.domain.Purpose

/** 대시보드와 같은 현대 브랜드 색을 차량 화면(어두운 배경)에 맞춘 값. */
object AutoLogColors {
    val Background = Color(0xFF0B1017)
    val Surface = Color(0xFF141B25)
    val SurfaceRaised = Color(0xFF1C2532)
    val Border = Color(0xFF2A3546)
    val Text = Color(0xFFEEF2F6)
    val Muted = Color(0xFF9AA6B2)
    val Active = Color(0xFF00AAD2)
    val Blue = Color(0xFF4F8FDB)
    val Unconfirmed = Color(0xFFE8A33D)
    val Personal = Color(0xFFC9C1B8)
    val Ok = Color(0xFF3CC08A)
    val Error = Color(0xFFFF6B5E)

    fun purpose(purpose: Purpose): Color = when (purpose) {
        Purpose.COMMUTE -> Active
        Purpose.BUSINESS -> Blue
        Purpose.PERSONAL -> Personal
        Purpose.UNCONFIRMED -> Unconfirmed
    }
}

/** 차량 화면 최소 터치 영역. */
val TouchTarget = 76.dp

@Composable
fun AutoLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AutoLogColors.Active,
            onPrimary = Color(0xFF00121A),
            secondary = AutoLogColors.Blue,
            background = AutoLogColors.Background,
            onBackground = AutoLogColors.Text,
            surface = AutoLogColors.Surface,
            onSurface = AutoLogColors.Text,
            surfaceVariant = AutoLogColors.SurfaceRaised,
            onSurfaceVariant = AutoLogColors.Muted,
            outline = AutoLogColors.Border,
            error = AutoLogColors.Error,
        ),
        typography = Typography(
            displayLarge = TextStyle(fontSize = 120.sp, fontWeight = FontWeight.Bold, letterSpacing = 16.sp),
            headlineLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
            headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium),
            bodyLarge = TextStyle(fontSize = 24.sp),
            bodyMedium = TextStyle(fontSize = 20.sp),
            labelLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}
