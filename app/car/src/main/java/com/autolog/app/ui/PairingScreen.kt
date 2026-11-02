package com.autolog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autolog.app.pairing.PairingController
import com.autolog.app.purpose.PurposeEstimator
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** 페어링 화면. 차량에서 키보드 입력 없이 코드만 보여주고, 관리자가 대시보드에 입력한다(D6). */
@Composable
fun PairingScreen(state: PairingController.State, onStart: () -> Unit, onCancel: (() -> Unit)?) {
    Box(Modifier.fillMaxSize().background(AutoLogColors.background).padding(40.dp), contentAlignment = Alignment.Center) {
        PleosCard(Modifier.widthIn(max = 1200.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(64.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 720.dp)) {
                Text("AutoLog", style = PleosType.BodyLargeStrong, color = AutoLogColors.positive)
                Spacer(Modifier.height(8.dp))
                Text("차량 등록", style = PleosType.TitleLarge, color = AutoLogColors.text)
                Spacer(Modifier.height(48.dp))
                when (state) {
                    PairingController.State.Idle -> {
                        Guide("관리자 대시보드에서 이 차량을 연결하려면 등록 코드를 받아 주세요.")
                        Spacer(Modifier.height(48.dp))
                        PleosButton("등록 코드 받기", onStart, Modifier.widthIn(min = 440.dp), ButtonSize.Large, ButtonStyle.Filled)
                    }
                    PairingController.State.Requesting -> {
                        CircularProgressIndicator(color = AutoLogColors.positive, strokeWidth = 6.dp, modifier = Modifier.size(80.dp))
                        Spacer(Modifier.height(24.dp))
                        Guide("등록 코드를 받는 중이에요.")
                    }
                    is PairingController.State.ShowingCode -> {
                        CodeBoxes(state.code)
                        Spacer(Modifier.height(40.dp))
                        Guide("대시보드 › 차량 관리 › 이 차량의 '페어링'에서 위 6자리 코드를 입력하세요.")
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "${expiry(state.expiresAt)}까지 유효 · 만료되면 새 코드를 자동으로 받아요.",
                            style = PleosType.BodySmall,
                            color = AutoLogColors.textTertiary,
                        )
                    }
                    is PairingController.State.Paired -> {
                        Box(
                            Modifier.size(96.dp).background(AutoLogColors.tint(AutoLogColors.active), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Text("✓", style = PleosType.HeadlineSmall, color = AutoLogColors.active) }
                        Spacer(Modifier.height(24.dp))
                        Text("등록 완료", style = PleosType.TitleLarge, color = AutoLogColors.text)
                        Spacer(Modifier.height(12.dp))
                        Guide(listOfNotNull(state.regNo, state.modelName).joinToString(" · "))
                    }
                    is PairingController.State.Error -> {
                        Text(state.message, style = PleosType.BodyLarge, color = AutoLogColors.negative, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(48.dp))
                        PleosButton("다시 시도", onStart, Modifier.widthIn(min = 440.dp), ButtonSize.Large, ButtonStyle.Filled)
                    }
                }
                if (onCancel != null) {
                    Spacer(Modifier.height(24.dp))
                    PleosButton("취소", onCancel, Modifier.widthIn(min = 440.dp), ButtonSize.Large, ButtonStyle.Basic)
                }
            }
        }
    }
}

/** 6자리 코드를 3+3 칸으로(Pleos Number field Large 비율). 멀리서도 한 자씩 읽힌다. */
@Composable
private fun CodeBoxes(code: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        code.forEachIndexed { index, digit ->
            if (index == 3) Spacer(Modifier.width(40.dp)) else if (index > 0) Spacer(Modifier.width(16.dp))
            Box(
                Modifier.size(width = 120.dp, height = 156.dp).clip(RoundedCornerShape(24.dp)).background(AutoLogColors.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(digit.toString(), style = PleosType.Display, color = AutoLogColors.text)
            }
        }
    }
}

@Composable
private fun Guide(text: String) {
    Text(text, style = PleosType.BodyLarge, color = AutoLogColors.textSecondary, textAlign = TextAlign.Center)
}

private fun expiry(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(PurposeEstimator.SEOUL).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(iso)
