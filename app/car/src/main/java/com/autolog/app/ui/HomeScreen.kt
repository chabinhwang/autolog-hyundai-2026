package com.autolog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.autolog.app.data.local.DriverEntity
import com.autolog.app.data.local.TripEntity
import com.autolog.app.data.settings.PairedVehicleInfo
import com.autolog.app.domain.Purpose
import com.autolog.app.domain.SyncState
import com.autolog.app.export.CsvExporter
import com.autolog.app.purpose.PurposeEstimator
import com.autolog.app.trip.TripRecorder
import java.time.format.DateTimeFormatter

private val DAY = DateTimeFormatter.ofPattern("M월 d일 (E)", java.util.Locale.KOREAN)
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val CardShape = RoundedCornerShape(20.dp)

data class HomeState(
    val vehicle: PairedVehicleInfo?,
    val driver: DriverEntity?,
    val status: TripRecorder.Status,
    val pendingCount: Int,
    val failedCount: Int,
    val unconfirmed: List<TripEntity>,
    val recent: List<TripEntity>,
    val homeRegistered: Boolean,
)

data class HomeActions(
    val pickDriver: () -> Unit,
    val choosePurpose: (TripEntity, Purpose) -> Unit,
    val registerHome: () -> Unit,
    val exportCsv: () -> Unit,
    val repair: () -> Unit,
)

@Composable
fun HomeScreen(state: HomeState, actions: HomeActions) {
    Row(Modifier.fillMaxSize().padding(32.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Header(state, actions)
            StatusCard(state.status)
            UnconfirmedCard(state.unconfirmed, actions.choosePurpose, Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = actions.registerHome, modifier = Modifier.weight(1f).height(TouchTarget)) {
                    Text(if (state.homeRegistered) "집 위치 다시 등록" else "지금 위치를 집으로")
                }
                OutlinedButton(onClick = actions.exportCsv, modifier = Modifier.weight(1f).height(TouchTarget)) { Text("이번 달 CSV") }
                OutlinedButton(onClick = actions.repair, modifier = Modifier.weight(1f).height(TouchTarget)) { Text("차량 다시 등록") }
            }
        }
        RecentCard(state.recent, state.pendingCount, state.failedCount, Modifier.weight(1.15f).fillMaxHeight())
    }
}

@Composable
private fun Header(state: HomeState, actions: HomeActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("AutoLog", style = MaterialTheme.typography.headlineMedium, color = AutoLogColors.Active)
            Text(
                listOfNotNull(state.vehicle?.regNo, state.vehicle?.modelName).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
                color = AutoLogColors.Muted,
            )
        }
        Button(
            onClick = actions.pickDriver,
            modifier = Modifier.height(TouchTarget),
            colors = ButtonDefaults.buttonColors(containerColor = AutoLogColors.SurfaceRaised, contentColor = AutoLogColors.Text),
        ) {
            Text(state.driver?.let { "이용자  ${it.name} · ${it.dept}" } ?: "이용자 선택")
        }
    }
}

@Composable
private fun StatusCard(status: TripRecorder.Status) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).background(if (status.driving) AutoLogColors.Ok else AutoLogColors.Muted, CircleShape))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(if (status.driving) "운행 기록 중" else "운행을 기다리는 중", style = MaterialTheme.typography.titleLarge)
                Text(
                    status.lastMessage ?: "기어를 D로 바꾸면 자동으로 기록을 시작해요. 도착하면 한 번만 물어볼게요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AutoLogColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun UnconfirmedCard(trips: List<TripEntity>, choose: (TripEntity, Purpose) -> Unit, modifier: Modifier) {
    Card(modifier) {
        Text(
            "확인이 필요한 운행 ${trips.size}건",
            style = MaterialTheme.typography.titleLarge,
            // 0건이면 경고색을 쓰지 않는다(할 일이 없는데 주의를 끌지 않게).
            color = if (trips.isEmpty()) MaterialTheme.colorScheme.onSurface else AutoLogColors.Unconfirmed,
        )
        Spacer(Modifier.height(12.dp))
        if (trips.isEmpty()) {
            Text("모든 운행의 용도가 정해졌어요.", color = AutoLogColors.Muted, style = MaterialTheme.typography.bodyLarge)
            return@Card
        }
        LazyColumn(state = rememberNewestFirstListState(trips.first().clientUuid), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(trips, key = { it.clientUuid }) { trip ->
                Column(Modifier.fillMaxWidth().background(AutoLogColors.SurfaceRaised, RoundedCornerShape(16.dp)).padding(18.dp)) {
                    Text(summary(trip), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PurposeButton("출퇴근", AutoLogColors.Active, Modifier.weight(1f)) { choose(trip, Purpose.COMMUTE) }
                        PurposeButton("업무", AutoLogColors.Blue, Modifier.weight(1f)) { choose(trip, Purpose.BUSINESS) }
                        PurposeButton("개인", AutoLogColors.Personal, Modifier.weight(1f)) { choose(trip, Purpose.PERSONAL) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurposeButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(TouchTarget),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF0B1017)),
    ) { Text(label) }
}

@Composable
private fun RecentCard(trips: List<TripEntity>, pendingCount: Int, failedCount: Int, modifier: Modifier) {
    Card(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("최근 운행", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            val (label, color) = when {
                failedCount > 0 -> "전송 실패 ${failedCount}건" + (if (pendingCount > 0) " · 대기 ${pendingCount}건" else "") to AutoLogColors.Error
                pendingCount > 0 -> "전송 대기 ${pendingCount}건" to AutoLogColors.Unconfirmed
                else -> "모두 전송됨" to AutoLogColors.Ok
            }
            Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        if (trips.isEmpty()) {
            Text("아직 기록된 운행이 없어요.", color = AutoLogColors.Muted, style = MaterialTheme.typography.bodyLarge)
            return@Card
        }
        LazyColumn(state = rememberNewestFirstListState(trips.first().clientUuid)) {
            items(trips, key = { it.clientUuid }) { trip -> TripRow(trip) }
        }
    }
}

/**
 * 최신순 목록용. key를 쓰는 LazyColumn은 맨 위에 새 항목이 들어오면 보던 항목에 위치를 고정해
 * 새 운행이 화면 위로 숨는다(에뮬레이터에서 확인). 맨 위를 보고 있었다면 새 항목으로 따라 올라간다.
 */
@Composable
private fun rememberNewestFirstListState(newestKey: String): LazyListState {
    val state = rememberLazyListState()
    LaunchedEffect(newestKey) {
        if (state.firstVisibleItemIndex <= 1) state.scrollToItem(0)
    }
    return state
}

@Composable
private fun TripRow(trip: TripEntity) {
    Row(Modifier.fillMaxWidth().heightIn(min = TouchTarget).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(52.dp).background(AutoLogColors.purpose(trip.purpose), RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(summary(trip), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${CsvExporter.purposeLabel(trip.purpose, trip.businessCategory)} · ${syncLabel(trip)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (trip.syncState == SyncState.FAILED) AutoLogColors.Error else AutoLogColors.Muted,
            )
        }
        Text(km(trip), style = MaterialTheme.typography.titleLarge)
    }
}

private fun summary(trip: TripEntity): String {
    val start = trip.startedAt.atZone(PurposeEstimator.SEOUL)
    val from = CsvExporter.endpointLabel(trip.originType, trip.originLabel)
    val to = CsvExporter.endpointLabel(trip.destType, trip.destLabel)
    return "${DAY.format(start)} ${TIME.format(start)}  $from → $to  ${km(trip)}"
}

private fun km(trip: TripEntity) = String.format(java.util.Locale.ROOT, "%.1fkm", trip.distanceKm)

private fun syncLabel(trip: TripEntity) = when (trip.syncState) {
    SyncState.SYNCED -> "전송됨"
    SyncState.PENDING -> if (trip.answerPending) "확인 중" else "전송 대기"
    SyncState.FAILED -> "전송 실패(${trip.syncErrorCode}) · 관리자 문의"
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth()
            .background(AutoLogColors.Surface, CardShape)
            .border(1.dp, AutoLogColors.Border, CardShape)
            .padding(24.dp),
        content = content,
    )
}
