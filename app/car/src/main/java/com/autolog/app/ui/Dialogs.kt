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
import com.autolog.app.domain.Purpose
import com.autolog.app.purpose.ArrivalChoice
import com.autolog.app.trip.TripRecorder

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
        title = "${driver.name}님, 운행 기록에 동의해 주세요",
        onDismiss = onDecline,
        large = true,
        confirm = "동의" to onAccept,
        dismiss = "동의 안 함" to onDecline,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Item(
                "어떤 정보를 기록하나요",
                "운행 날짜, 출발·도착 때의 계기판 거리, 출발지·도착지 이름(회사·거래처·집, 그 밖에는 동 이름), 운행 용도와 메모, 운전한 사람(부서·이름)을 기록해요.",
            )
            Item("차량 밖으로 보내지 않아요", "정확한 위치(좌표)와 집 위치는 차량 안에만 두고 서버로 보내지 않아요.")
            Item("관리자에게는 이만큼 보여요", "개인 운행은 날짜와 거리만 보여요. 장소·시각·메모는 관리자도 볼 수 없어요.")
            Item("얼마나 보관하나요", "회사 서버에는 장부 보존 기간인 5년 동안, 차량에는 서버로 보낸 뒤 90일까지 보관해요.")
            Item("동의하지 않아도 돼요", "다만 동의하지 않으면 ${driver.name}님 이름으로는 운행을 기록할 수 없어요.")
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

/**
 * 도착 질문 팝업 (car-app.md §3). 음성으로 묻는 동안 같은 질문을 띄우고, 말하거나 버튼을 누르는 것 중 먼저 들어온 답으로 기록한다.
 * 용도를 정하면 전송 전까지 남은 시간과 '다시 선택'을 보여준다(CONFIRMING).
 * 정차(P) 확인 뒤에만 뜨므로 화면 조작이 가능하다. 바깥을 눌러도 닫지 않는다(음성 무응답이면 기록 서비스가 닫는다).
 */
@Composable
fun ArrivalDialog(status: TripRecorder.Status, onChoose: (ArrivalChoice) -> Unit, onReselect: () -> Unit) {
    val sendAt = status.sendAtMillis
    if (status.phase == TripRecorder.Phase.CONFIRMING && sendAt != null) {
        val seconds = rememberSecondsLeft(sendAt)
        PleosPopup(
            title = status.lastMessage ?: "기록했어요",
            description = "${seconds}초 뒤 서버로 전송해요.",
            onDismiss = {},
            large = true,
            confirm = "다시 선택" to onReselect,
        )
        return
    }
    PleosPopup(
        title = if (status.previousTrip) "지난 운행 확인" else "도착했어요",
        description = status.prompt,
        onDismiss = {},
        large = true,
        dismiss = "나중에 고를게요" to { onChoose(ArrivalChoice.LATER) },
    ) {
        Text("말로 대답하거나 아래에서 골라 주세요.", style = PleosType.BodySmall, color = AutoLogColors.textTertiary)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ArrivalButton("출퇴근", Purpose.COMMUTE, status.suggested, AutoLogColors.autonomous) { onChoose(ArrivalChoice.COMMUTE) }
            ArrivalButton("업무", Purpose.BUSINESS, status.suggested, AutoLogColors.positive) { onChoose(ArrivalChoice.BUSINESS) }
            ArrivalButton("개인", Purpose.PERSONAL, status.suggested, null) { onChoose(ArrivalChoice.PERSONAL) }
        }
    }
}

/** 추정 용도 버튼은 '네, ○○'로 채워서 강조한다(음성 "네"와 같은 뜻). */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ArrivalButton(
    label: String,
    purpose: Purpose,
    suggested: Purpose?,
    tint: Color?,
    onClick: () -> Unit,
) {
    val isSuggested = purpose == suggested
    PleosButton(
        label = if (isSuggested) "네, $label" else label,
        onClick = onClick,
        modifier = Modifier.weight(1f),
        size = ButtonSize.Large,
        style = when {
            isSuggested -> ButtonStyle.Filled
            tint != null -> ButtonStyle.Tinted(tint)
            else -> ButtonStyle.Basic
        },
    )
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
