package com.autolog.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이번 운행 이용자") },
        text = {
            LazyColumn(Modifier.heightIn(max = 560.dp)) {
                items(drivers, key = { it.id }) { driver ->
                    Row(
                        label = "${driver.name}  ·  ${driver.dept}",
                        selected = driver.id == currentId,
                        onClick = { onPick(driver) },
                    )
                }
                item { Row(label = "이용자 미지정", selected = currentId == null, onClick = { onPick(null) }) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = TouchTarget)) { Text("닫기") } },
    )
}

@Composable
private fun Row(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = if (selected) "✓  $label" else label,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) AutoLogColors.Active else AutoLogColors.Text,
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTarget).clickable(onClick = onClick).padding(vertical = 22.dp),
    )
    HorizontalDivider(color = AutoLogColors.Border)
}

/** 개인정보 수집·이용 동의 (rules §11 동의 화면 내용). */
@Composable
fun ConsentDialog(driver: DriverEntity, onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("${driver.name}님, 운행 기록 동의") },
        text = {
            Column {
                Item("수집 항목", "사용일자, 주행 전·후 계기판 거리, 출발·도착 장소명(회사·거래처·'집'·내비 목적지명), 운행 용도·업무 세부·메모, 이용자(부서·성명)")
                Item("서버로 보내지 않는 것", "좌표, 집 위치")
                Item("관리자가 볼 수 있는 범위", "개인 운행은 날짜와 거리만 보이고 장소·시각·메모는 가려져요.")
                Item("보유 기간", "서버 운행 기록은 장부 보존 기간(5년) 동안, 차량에는 전송 후 90일까지 보관해요.")
                Item("거부할 수 있어요", "동의하지 않으면 이 직원 이름으로 운행을 기록할 수 없어요.")
            }
        },
        confirmButton = { Button(onClick = onAccept, modifier = Modifier.heightIn(min = TouchTarget)) { Text("동의") } },
        dismissButton = { TextButton(onClick = onDecline, modifier = Modifier.heightIn(min = TouchTarget)) { Text("동의 안 함") } },
    )
}

@Composable
private fun Item(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = AutoLogColors.Active, modifier = Modifier.padding(top = 14.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium)
}

/** 재페어링 전 미전송 경고 (D10). */
@Composable
fun RepairWarningDialog(pending: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("차량을 다시 등록할까요?") },
        text = {
            Text(
                if (pending > 0) {
                    "아직 전송되지 않은 기록이 ${pending}건 있어요. 먼저 전송을 시도하지만, 다른 차량으로 등록하면 이 기록은 서버에 올라가지 않아요."
                } else {
                    "새 등록 코드를 받아 관리자 대시보드에서 다시 연결해요."
                },
            )
        },
        confirmButton = { Button(onClick = onConfirm, modifier = Modifier.heightIn(min = TouchTarget)) { Text("다시 등록") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = TouchTarget)) { Text("취소") } },
    )
}
