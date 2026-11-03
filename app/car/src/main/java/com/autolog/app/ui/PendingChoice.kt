package com.autolog.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.autolog.app.domain.Purpose
import kotlinx.coroutines.delay

/** 고른 용도와 전송 예정 시각(epoch ms). 그때까지 '다시 선택'으로 되돌릴 수 있다. */
data class PendingChoice(val purpose: Purpose, val sendAtMillis: Long) {
    companion object {
        const val HOLD_MILLIS = 5_000L
    }
}

/** [untilMillis]까지 남은 초(올림, 0 이상). 1초보다 잘게 다시 그린다. */
@Composable
fun rememberSecondsLeft(untilMillis: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(untilMillis) {
        while (now < untilMillis) {
            delay(200)
            now = System.currentTimeMillis()
        }
    }
    return ((untilMillis - now + 999) / 1000).coerceAtLeast(0)
}
