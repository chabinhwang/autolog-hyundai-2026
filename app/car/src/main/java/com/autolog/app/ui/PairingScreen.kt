package com.autolog.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.autolog.app.pairing.PairingController
import com.autolog.app.purpose.PurposeEstimator
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** 페어링 화면. 차량에서 키보드 입력 없이 코드만 보여주고, 관리자가 대시보드에 입력한다(D6). */
@Composable
fun PairingScreen(state: PairingController.State, onStart: () -> Unit, onCancel: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AutoLog", style = MaterialTheme.typography.headlineLarge, color = AutoLogColors.Active)
        Spacer(Modifier.height(12.dp))
        Text("차량 등록", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(40.dp))
        when (state) {
            PairingController.State.Idle -> {
                Guide("관리자 대시보드에서 이 차량을 등록하려면 등록 코드를 받아 주세요.")
                Spacer(Modifier.height(32.dp))
                Button(onClick = onStart, modifier = Modifier.height(TouchTarget).widthIn(min = 320.dp)) { Text("등록 코드 받기") }
            }
            PairingController.State.Requesting -> CircularProgressIndicator()
            is PairingController.State.ShowingCode -> {
                Text(state.code.chunked(3).joinToString(" "), style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(24.dp))
                Guide("대시보드 › 차량 관리 › 이 차량의 '페어링'에서 위 6자리 코드를 입력하세요.")
                Spacer(Modifier.height(8.dp))
                Text("${expiry(state.expiresAt)}까지 유효 · 만료되면 새 코드를 자동으로 받아요.", color = AutoLogColors.Muted)
            }
            is PairingController.State.Paired -> {
                Text("등록 완료", style = MaterialTheme.typography.headlineLarge, color = AutoLogColors.Ok)
                Spacer(Modifier.height(16.dp))
                Guide(listOfNotNull(state.regNo, state.modelName).joinToString(" · "))
            }
            is PairingController.State.Error -> {
                Text(state.message, color = AutoLogColors.Error, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                Button(onClick = onStart, modifier = Modifier.height(TouchTarget).widthIn(min = 320.dp)) { Text("다시 시도") }
            }
        }
        if (onCancel != null) {
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.TextButton(onClick = onCancel, modifier = Modifier.height(TouchTarget)) { Text("취소") }
        }
    }
}

@Composable
private fun Guide(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = AutoLogColors.Muted, textAlign = TextAlign.Center)
}

private fun expiry(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(PurposeEstimator.SEOUL).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(iso)
