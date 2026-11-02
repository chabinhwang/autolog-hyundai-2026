package com.autolog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.autolog.app.data.local.DriverEntity

/** 이용자 선택. 공용차는 탈 때마다 고른다(기본값: 마지막 사용자). */
@Composable
fun DriverPickerDialog(
    drivers: List<DriverEntity>,
    currentId: Long?,
    onPick: (DriverEntity?) -> Unit,
    onDismiss: () -> Unit,
) {
    PleosPopup(
        title = "이번 운행 이용자",
        onDismiss = onDismiss,
        dismiss = "닫기" to onDismiss,
    ) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(drivers, key = { it.id }) { driver ->
                DriverRow(driver.name, driver.dept, selected = driver.id == currentId, onClick = { onPick(driver) })
            }
            item { DriverRow("이용자 미지정", null, selected = currentId == null, onClick = { onPick(null) }) }
        }
    }
}

@Composable
private fun DriverRow(name: String, dept: String?, selected: Boolean, onClick: () -> Unit) {
    val colors = AutoLogColors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) colors.tint(colors.positive) else colors.buttonBasic)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).background(if (dept != null) colors.positive else colors.personal, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(if (dept != null) name.take(1) else "–", style = PleosType.BodyMediumStrong, color = Color.White) }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = PleosType.BodyLargeStrong, color = colors.text)
            dept?.let { Text(it, style = PleosType.BodySmall, color = colors.textTertiary) }
        }
        if (selected) Text("✓", style = PleosType.TitleSmall, color = colors.positive)
    }
}

/** 개인정보 수집·이용 동의 (rules §11 동의 화면 내용). */
@Composable
fun ConsentDialog(driver: DriverEntity, onAccept: () -> Unit, onDecline: () -> Unit) {
    PleosPopup(
        title = "${driver.name}님, 운행 기록 동의",
        onDismiss = onDecline,
        large = true,
        confirm = "동의" to onAccept,
        dismiss = "동의 안 함" to onDecline,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Item("수집 항목", "사용일자, 주행 전·후 계기판 거리, 출발·도착 장소명(회사·거래처·'집'·내비 목적지명), 운행 용도·업무 세부·메모, 이용자(부서·성명)")
            Item("서버로 보내지 않는 것", "좌표, 집 위치")
            Item("관리자가 볼 수 있는 범위", "개인 운행은 날짜와 거리만 보이고 장소·시각·메모는 가려져요.")
            Item("보유 기간", "서버 운행 기록은 장부 보존 기간(5년) 동안, 차량에는 전송 후 90일까지 보관해요.")
            Item("거부할 수 있어요", "동의하지 않으면 이 직원 이름으로 운행을 기록할 수 없어요.")
        }
    }
}

@Composable
private fun Item(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AutoLogColors.surfaceHigh).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = PleosType.BodyMediumStrong, color = AutoLogColors.text)
        Text(body, style = PleosType.BodyMedium, color = AutoLogColors.textSecondary)
    }
}

/** 재페어링 전 미전송 경고 (D10). */
@Composable
fun RepairWarningDialog(pending: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    PleosPopup(
        title = "차량을 다시 등록할까요?",
        description = if (pending > 0) {
            "아직 전송되지 않은 기록이 ${pending}건 있어요. 먼저 전송을 시도하지만, 다른 차량으로 등록하면 이 기록은 서버에 올라가지 않아요."
        } else {
            "새 등록 코드를 받아 관리자 대시보드에서 다시 연결해요."
        },
        onDismiss = onDismiss,
        confirm = "다시 등록" to onConfirm,
        dismiss = "취소" to onDismiss,
    )
}
