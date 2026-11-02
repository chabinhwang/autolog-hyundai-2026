package com.autolog.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Pleos Basic button 크기(높이·모서리·좌우 여백·글자). */
enum class ButtonSize(val height: Dp, val radius: Dp, val padding: Dp, val minWidth: Dp) {
    Large(100.dp, 32.dp, 40.dp, 140.dp),
    Medium(80.dp, 24.dp, 32.dp, 120.dp),
    Small(64.dp, 16.dp, 24.dp, 100.dp),
}

/**
 * Basic: 옅은 회색 바탕(가이드 Primary) · Filled: 진한 바탕(가이드 Secondary, 주요 동작)
 * Tinted: 용도 색을 옅게 깐 바탕(용도 선택처럼 의미 색이 필요한 곳).
 */
sealed interface ButtonStyle {
    data object Basic : ButtonStyle
    data object Filled : ButtonStyle
    data class Tinted(val color: Color) : ButtonStyle
}

/** Pleos Basic button. 눌리면 95%로 줄고 바탕이 진해진다(Foundations › Interaction). */
@Composable
fun PleosButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: ButtonSize = ButtonSize.Medium,
    style: ButtonStyle = ButtonStyle.Basic,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = AutoLogColors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "press")
    val (bg, fg) = when (style) {
        ButtonStyle.Basic -> (if (pressed) colors.buttonBasicPressed else colors.buttonBasic) to colors.text
        ButtonStyle.Filled -> (if (pressed) colors.buttonFilledPressed else colors.buttonFilled) to colors.onFilled
        is ButtonStyle.Tinted -> style.color.copy(alpha = if (pressed) 0.28f else if (colors.isDark) 0.20f else 0.12f) to
            if (colors.isDark) colors.text else style.color
    }
    val shape = RoundedCornerShape(size.radius)
    val textStyle = when (size) {
        ButtonSize.Large -> PleosType.TitleSmall
        ButtonSize.Medium -> PleosType.BodyLargeStrong
        ButtonSize.Small -> PleosType.BodySmallStrong
    }
    Row(
        modifier = modifier
            .scale(scale)
            .heightIn(min = size.height)
            .widthIn(min = size.minWidth)
            .clip(shape)
            .then(
                if (enabled) Modifier.background(bg, shape) else Modifier.border(2.dp, colors.dividerStrong, shape),
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = size.padding),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke(this)
        Text(
            label,
            style = textStyle,
            color = if (enabled) fg else colors.textDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** 화면 카드(surface_basic). */
@Composable
fun PleosCard(
    modifier: Modifier = Modifier,
    color: Color = AutoLogColors.surface,
    contentPadding: PaddingValues = PaddingValues(36.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(color, shape)
            .border(2.dp, AutoLogColors.divider, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** 작은 상태 표시(점 + 글자). */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(AutoLogColors.tint(color))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(text, style = PleosType.Label, color = if (AutoLogColors.isDark) AutoLogColors.text else color, maxLines = 1)
    }
}

/**
 * Pleos System popup(Medium 808 / Large 1200). 제목·설명은 가운데, 버튼은 가로로 같은 너비.
 * [body]가 있으면 설명 아래에 들어간다(목록·동의 항목 등).
 */
@Composable
fun PleosPopup(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    large: Boolean = false,
    confirm: Pair<String, () -> Unit>? = null,
    dismiss: Pair<String, () -> Unit>? = null,
    body: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val shape = RoundedCornerShape(48.dp)
        Column(
            modifier = modifier
                .widthIn(max = if (large) 1200.dp else 808.dp)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(shape)
                .background(AutoLogColors.popup, shape)
                .border(2.dp, AutoLogColors.divider, shape)
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(title, style = PleosType.TitleLarge, color = AutoLogColors.text, textAlign = TextAlign.Center)
            description?.let {
                Text(it, style = PleosType.BodyLarge, color = AutoLogColors.textSecondary, textAlign = TextAlign.Center)
            }
            body?.invoke(this)
            if (confirm != null || dismiss != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    confirm?.let { (label, action) ->
                        PleosButton(label, action, Modifier.weight(1f), ButtonSize.Large, ButtonStyle.Filled)
                    }
                    dismiss?.let { (label, action) ->
                        PleosButton(label, action, Modifier.weight(1f), ButtonSize.Large, ButtonStyle.Basic)
                    }
                }
            }
        }
    }
}

/** 제목 + 보조 글자 한 줄 묶음. */
@Composable
fun TitleBlock(title: String, subtitle: String?, modifier: Modifier = Modifier, titleStyle: TextStyle = PleosType.TitleSmall) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = titleStyle, color = AutoLogColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        subtitle?.let { Text(it, style = PleosType.BodySmall, color = AutoLogColors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** 위아래 얇은 구분선. */
@Composable
fun PleosDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).background(AutoLogColors.divider))
}
