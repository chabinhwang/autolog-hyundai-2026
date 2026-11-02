package com.autolog.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autolog.app.domain.Purpose

/**
 * Pleos Connect 디자인 가이드(Foundations: Colors·Typography, Components: Basic button·System popup) 기준 색·글자.
 * 차량 시스템 UI의 라이트/다크 설정을 따른다. 값은 가이드의 토큰 이름을 주석으로 남긴다.
 */
@Immutable
data class AutoLogPalette(
    val isDark: Boolean,
    val background: Color,      // background_secondary
    val surface: Color,         // surface_basic (카드)
    val surfaceHigh: Color,     // surface_high (카드 안 묶음)
    val surfaceAccent: Color,   // surface_accent
    val popup: Color,           // background_popup
    val divider: Color,         // divider_primary
    val dividerStrong: Color,   // divider_secondary
    val text: Color,            // text_primary
    val textSecondary: Color,   // text_secondary (84%)
    val textTertiary: Color,    // text_tertiary (64%)
    val textDisabled: Color,    // text_quaternary (32%)
    val buttonBasic: Color,     // button_basic_enabled
    val buttonBasicPressed: Color,
    val buttonFilled: Color,    // button_filled_enabled
    val buttonFilledPressed: Color,
    val onFilled: Color,
    val active: Color,          // informative_active
    val positive: Color,        // informative_positive
    val negative: Color,        // informative_negative
    val autonomous: Color,      // informative_autonomous_driving
    val caution: Color,
    val personal: Color,
) {
    fun purpose(purpose: Purpose): Color = when (purpose) {
        Purpose.COMMUTE -> autonomous
        Purpose.BUSINESS -> positive
        Purpose.PERSONAL -> personal
        Purpose.UNCONFIRMED -> caution
    }

    /** 강조색 위에 얇게 깐 배경(상태 칩·선택 카드). */
    fun tint(color: Color): Color = color.copy(alpha = if (isDark) 0.22f else 0.10f)

    companion object {
        private val Ink = Color(0xFF131417)

        val Light = AutoLogPalette(
            isDark = false,
            background = Color(0xFFF7F8FA),
            surface = Color(0xFFFFFFFF),
            surfaceHigh = Color(0xFFEDEEF2),
            surfaceAccent = Color(0xFFE0E1E6),
            popup = Color(0xFFFFFFFF),
            divider = Ink.copy(alpha = 0.05f),
            dividerStrong = Ink.copy(alpha = 0.10f),
            text = Ink,
            textSecondary = Ink.copy(alpha = 0.84f),
            textTertiary = Ink.copy(alpha = 0.64f),
            textDisabled = Ink.copy(alpha = 0.32f),
            buttonBasic = Ink.copy(alpha = 0.05f),
            buttonBasicPressed = Ink.copy(alpha = 0.10f),
            buttonFilled = Color(0xFF44464E),
            buttonFilledPressed = Color(0xFF313236),
            onFilled = Color.White,
            active = Color(0xFF02C265),
            positive = Color(0xFF0064FF),
            negative = Color(0xFFFE3D16),
            autonomous = Color(0xFF5A46FA),
            caution = Color(0xFFE58A00),
            personal = Color(0xFFA4A8AE),
        )

        val Dark = AutoLogPalette(
            isDark = true,
            background = Color(0xFF131417),
            surface = Color(0xFF1D1E21),
            surfaceHigh = Color(0xFF313236),
            surfaceAccent = Color(0xFF44464E),
            popup = Color(0xFF313236),
            divider = Color.White.copy(alpha = 0.06f),
            dividerStrong = Color.White.copy(alpha = 0.12f),
            text = Color.White,
            textSecondary = Color.White.copy(alpha = 0.84f),
            textTertiary = Color.White.copy(alpha = 0.64f),
            textDisabled = Color.White.copy(alpha = 0.32f),
            buttonBasic = Color.White.copy(alpha = 0.08f),
            buttonBasicPressed = Color.White.copy(alpha = 0.14f),
            buttonFilled = Color(0xFFE0E1E6),
            buttonFilledPressed = Color(0xFFC7C9CE),
            onFilled = Ink,
            active = Color(0xFF00E074),
            positive = Color(0xFF0082FF),
            negative = Color(0xFFFF4C28),
            autonomous = Color(0xFF6A57FF),
            caution = Color(0xFFFFA42E),
            personal = Color(0xFF868A92),
        )
    }
}

private val LocalPalette = staticCompositionLocalOf { AutoLogPalette.Light }

/** 현재 테마의 색. 컴포저블 안에서만 읽는다. */
val AutoLogColors: AutoLogPalette
    @Composable @ReadOnlyComposable get() = LocalPalette.current

/**
 * Pleos 글자 위계(px, 이 디스플레이는 160dpi라 1px = 1dp). 시스템 기본 글꼴이 Asta Sans다.
 * 기기 글꼴 설정이 600→Bold, 700 이상→ExtraBold 파일이라 가이드의 Bold는 SemiBold(600)로 요청한다.
 */
object PleosType {
    private val Bold = FontWeight.SemiBold
    // 한국어를 글자 단위가 아니라 어절 단위로 줄바꿈한다("기록 시/작" 방지). Android 13 미만은 무시된다.
    private val KoreanLineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)
    private val ExtraBold = FontWeight.ExtraBold
    private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        TextStyle(fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, lineBreak = KoreanLineBreak)

    val HeadlineLarge = style(60, 80, ExtraBold)
    val HeadlineSmall = style(48, 64, ExtraBold)
    val TitleLarge = style(40, 52, ExtraBold)
    val TitleMedium = style(36, 44, Bold)
    val TitleSmall = style(32, 40, Bold)
    val BodyLarge = style(30, 38)
    val BodyLargeStrong = style(30, 38, Bold)
    val BodyMedium = style(28, 36)
    val BodyMediumStrong = style(28, 36, Bold)
    val BodySmall = style(26, 34)
    val BodySmallStrong = style(26, 34, Bold)
    val Label = style(24, 28)
    val LabelSmall = style(20, 24)
    val Display = style(112, 134, Bold)
}

/** 차량 화면 최소 터치 영역(Pleos Small 버튼 높이). */
val TouchTarget = 64.dp

@Composable
fun AutoLogTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) AutoLogPalette.Dark else AutoLogPalette.Light
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme.copy(
                primary = palette.positive,
                onPrimary = Color.White,
                background = palette.background,
                onBackground = palette.text,
                surface = palette.background,
                onSurface = palette.text,
                surfaceVariant = palette.surfaceHigh,
                onSurfaceVariant = palette.textTertiary,
                surfaceContainerHigh = palette.popup,
                outline = palette.dividerStrong,
                error = palette.negative,
            ),
            typography = Typography(
                displayLarge = PleosType.Display,
                headlineLarge = PleosType.TitleLarge,
                headlineMedium = PleosType.TitleMedium,
                titleLarge = PleosType.TitleSmall,
                titleMedium = PleosType.BodyLargeStrong,
                bodyLarge = PleosType.BodyLarge,
                bodyMedium = PleosType.BodyMedium,
                bodySmall = PleosType.BodySmall,
                labelLarge = PleosType.BodyMediumStrong,
            ),
            content = content,
        )
    }
}
