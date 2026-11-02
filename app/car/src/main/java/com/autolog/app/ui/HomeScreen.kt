package com.autolog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
private val TIME = DateTimeFormatter.ofPattern("HH:mm")

/** 두 단 배치를 쓰는 최소 앱 창 너비. Pleos 세로(1:1 분할) 창은 이보다 좁아 한 단으로 쌓는다. */
private val TwoPaneMinWidth = 1200.dp

data class HomeState(
    val vehicle: PairedVehicleInfo?,
    val driver: DriverEntity?,
    val status: TripRecorder.Status,
    val pendingCount: Int,
    val failedCount: Int,
    val unconfirmed: List<TripEntity>,
    val recent: List<TripEntity>,
    val homeRegistered: Boolean,
    val month: YearMonth,
    val summary: MonthSummary,
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
    BoxWithConstraints(Modifier.fillMaxSize().background(AutoLogColors.background)) {
        if (maxWidth >= TwoPaneMinWidth) TwoPane(state, actions) else OnePane(state, actions)
    }
}

/** 가로(Landscape) 창: 왼쪽은 지금 할 일, 오른쪽은 기록. */
@Composable
private fun TwoPane(state: HomeState, actions: HomeActions) {
    Row(Modifier.fillMaxSize().padding(40.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Header(state, actions)
            if (state.unconfirmed.isEmpty()) {
                // 할 일이 없으면 상태 카드를 키워 자동 기록 방식을 함께 보여준다.
                StatusCard(state.status, Modifier.weight(1f), showGuide = true)
                AllClearCard()
            } else {
                StatusCard(state.status)
                UnconfirmedCard(state.unconfirmed, actions.choosePurpose, Modifier.weight(1f))
            }
            ActionRow(state.homeRegistered, actions)
        }
        Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            SummaryCard(state.month, state.summary)
            PleosCard(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(start = 36.dp, top = 32.dp, end = 36.dp)) {
                RecentHeader(state.pendingCount, state.failedCount)
                Spacer(Modifier.height(8.dp))
                if (state.recent.isEmpty()) {
                    EmptyRecent()
                } else {
                    LazyColumn(state = rememberNewestFirstListState(state.recent.first().clientUuid)) {
                        recentItems(state.recent)
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

/** 세로(Portrait) 창: 같은 내용을 한 단으로. */
@Composable
private fun OnePane(state: HomeState, actions: HomeActions) {
    LazyColumn(
        state = rememberNewestFirstListState(state.unconfirmed.firstOrNull()?.clientUuid ?: state.recent.firstOrNull()?.clientUuid),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { Header(state, actions) }
        item { StatusCard(state.status) }
        if (state.unconfirmed.isEmpty()) {
            item { AllClearCard() }
        } else {
            item { UnconfirmedTitle(state.unconfirmed.size) }
            items(state.unconfirmed, key = { "u-" + it.clientUuid }) { trip -> UnconfirmedItem(trip, actions.choosePurpose) }
        }
        item { SummaryCard(state.month, state.summary) }
        item { RecentHeader(state.pendingCount, state.failedCount) }
        if (state.recent.isEmpty()) item { EmptyRecent() } else recentItems(state.recent.take(30))
        item { ActionRow(state.homeRegistered, actions) }
    }
}

@Composable
private fun Header(state: HomeState, actions: HomeActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        TitleBlock(
            title = "AutoLog",
            subtitle = listOfNotNull(state.vehicle?.regNo, state.vehicle?.modelName).joinToString(" · ").ifEmpty { null },
            titleStyle = PleosType.TitleMedium,
            modifier = Modifier.weight(1f),
        )
        val driver = state.driver
        if (driver == null) {
            PleosButton("이용자 선택", actions.pickDriver, size = ButtonSize.Medium, style = ButtonStyle.Tinted(AutoLogColors.caution))
        } else {
            PleosButton(
                label = "${driver.name} · ${driver.dept}",
                onClick = actions.pickDriver,
                size = ButtonSize.Medium,
                leading = { Avatar(driver.name) },
            )
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(
        Modifier.size(48.dp).background(AutoLogColors.positive, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1), style = PleosType.BodySmallStrong, color = androidx.compose.ui.graphics.Color.White)
    }
}

/** 지금 상태. 주행 중에는 화면을 보지 않으므로 정차 중 한눈에 읽히는 크기로만 보여준다. */
@Composable
private fun StatusCard(status: TripRecorder.Status, modifier: Modifier = Modifier, showGuide: Boolean = false) {
    val accent = if (status.driving) AutoLogColors.active else AutoLogColors.personal
    PleosCard(
        modifier.fillMaxWidth(),
        color = if (status.driving) AutoLogColors.tint(AutoLogColors.active) else AutoLogColors.surface,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(accent, CircleShape))
            Spacer(Modifier.width(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (status.driving) "운행 기록 중" else "운행 대기 중", style = PleosType.TitleLarge, color = AutoLogColors.text)
                Text(
                    status.lastMessage ?: if (showGuide) "다음 운행을 기록할 준비가 됐어요." else "기어를 D로 바꾸면 자동으로 기록해요.",
                    style = PleosType.BodyMedium,
                    color = AutoLogColors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showGuide) {
            Spacer(Modifier.height(32.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GuideStep(1, "기어 D", "자동으로 기록 시작", Modifier.weight(1f))
                GuideStep(2, "도착", "용도를 음성으로 질문", Modifier.weight(1f))
                GuideStep(3, "대답", "한마디면 기록 완료", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GuideStep(number: Int, title: String, body: String, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(AutoLogColors.surfaceHigh).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp).background(AutoLogColors.positive, CircleShape), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = PleosType.TitleSmall, color = androidx.compose.ui.graphics.Color.White)
        }
        Text(title, style = PleosType.TitleSmall, color = AutoLogColors.text, maxLines = 1)
        Text(body, style = PleosType.BodySmall, color = AutoLogColors.textTertiary, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AllClearCard() {
    PleosCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).background(AutoLogColors.tint(AutoLogColors.active), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("✓", style = PleosType.TitleSmall, color = AutoLogColors.active) }
            Spacer(Modifier.width(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("확인할 운행이 없어요", style = PleosType.BodyLargeStrong, color = AutoLogColors.text)
                Text("모든 운행의 용도가 정해졌어요.", style = PleosType.BodySmall, color = AutoLogColors.textTertiary)
            }
        }
    }
}

@Composable
private fun UnconfirmedTitle(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("확인이 필요한 운행", style = PleosType.TitleSmall, color = AutoLogColors.text)
        StatusChip("${count}건", AutoLogColors.caution)
    }
}

@Composable
private fun UnconfirmedCard(trips: List<TripEntity>, choose: (TripEntity, Purpose) -> Unit, modifier: Modifier) {
    PleosCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 32.dp, top = 32.dp, end = 32.dp)) {
        UnconfirmedTitle(trips.size)
        Spacer(Modifier.height(20.dp))
        LazyColumn(
            state = rememberNewestFirstListState(trips.first().clientUuid),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(trips, key = { it.clientUuid }) { trip -> UnconfirmedItem(trip, choose) }
        }
    }
}

/** 미확인 한 건: 언제·어디서 어디로·거리, 그리고 용도 세 버튼. */
@Composable
private fun UnconfirmedItem(trip: TripEntity, choose: (TripEntity, Purpose) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(AutoLogColors.surfaceHigh).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(whenLabel(trip), style = PleosType.BodySmall, color = AutoLogColors.textTertiary)
                Text(route(trip), style = PleosType.BodyLargeStrong, color = AutoLogColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(16.dp))
            Text(km(trip), style = PleosType.TitleSmall, color = AutoLogColors.text)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PleosButton("출퇴근", { choose(trip, Purpose.COMMUTE) }, Modifier.weight(1f), style = ButtonStyle.Tinted(AutoLogColors.autonomous))
            PleosButton("업무", { choose(trip, Purpose.BUSINESS) }, Modifier.weight(1f), style = ButtonStyle.Tinted(AutoLogColors.positive))
            PleosButton("개인", { choose(trip, Purpose.PERSONAL) }, Modifier.weight(1f), style = ButtonStyle.Basic)
        }
    }
}

@Composable
private fun ActionRow(homeRegistered: Boolean, actions: HomeActions) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PleosButton(if (homeRegistered) "집 위치 변경" else "여기를 집으로", actions.registerHome, Modifier.weight(1f))
        PleosButton("이번 달 CSV", actions.exportCsv, Modifier.weight(1f))
        PleosButton("차량 다시 등록", actions.repair, Modifier.weight(1f))
    }
}

/** 이번 달 운행 수·거리·업무사용비율. */
@Composable
private fun SummaryCard(month: YearMonth, summary: MonthSummary) {
    PleosCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${month.monthValue}월 운행", style = PleosType.TitleSmall, color = AutoLogColors.text, modifier = Modifier.weight(1f))
            if (summary.unconfirmedCount > 0) StatusChip("미확인 ${summary.unconfirmedCount}건", AutoLogColors.caution)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("운행", "${summary.tripCount}건", Modifier.weight(1f))
            Stat("주행 거리", String.format(Locale.ROOT, "%.1fkm", summary.totalKm), Modifier.weight(1f))
            Stat("업무사용비율", summary.businessRatio?.let { "$it%" } ?: "–", Modifier.weight(1f), highlight = (summary.businessRatio ?: 0) > 0)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(24.dp)).background(AutoLogColors.surfaceHigh).padding(horizontal = 28.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = PleosType.BodySmall, color = AutoLogColors.textTertiary, maxLines = 1)
        Text(
            value,
            style = PleosType.TitleLarge,
            color = if (highlight) AutoLogColors.positive else AutoLogColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentHeader(pendingCount: Int, failedCount: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("최근 운행", style = PleosType.TitleSmall, color = AutoLogColors.text, modifier = Modifier.weight(1f))
        when {
            failedCount > 0 -> StatusChip(
                "전송 실패 ${failedCount}건" + if (pendingCount > 0) " · 대기 ${pendingCount}건" else "",
                AutoLogColors.negative,
            )
            pendingCount > 0 -> StatusChip("전송 대기 ${pendingCount}건", AutoLogColors.caution)
            else -> StatusChip("모두 전송됨", AutoLogColors.active)
        }
    }
}

@Composable
private fun EmptyRecent() {
    Text(
        "아직 기록된 운행이 없어요. 기어를 D로 바꾸면 자동으로 기록을 시작해요.",
        style = PleosType.BodyMedium,
        color = AutoLogColors.textTertiary,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

/** 날짜별로 묶은 최근 운행. */
private fun LazyListScope.recentItems(trips: List<TripEntity>) {
    var previousDay: LocalDate? = null
    trips.forEach { trip ->
        val day = trip.startedAt.atZone(PurposeEstimator.SEOUL).toLocalDate()
        if (day != previousDay) {
            val label = DAY.format(day)
            item(key = "d-${trip.clientUuid}") { DayHeader(label) }
            previousDay = day
        }
        item(key = trip.clientUuid) { TripRow(trip) }
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        label,
        style = PleosType.BodySmallStrong,
        color = AutoLogColors.textTertiary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun TripRow(trip: TripEntity) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            TIME.format(trip.startedAt.atZone(PurposeEstimator.SEOUL)),
            style = PleosType.BodyMedium,
            color = AutoLogColors.textTertiary,
            modifier = Modifier.width(96.dp),
        )
        Box(Modifier.width(8.dp).height(60.dp).background(AutoLogColors.purpose(trip.purpose), RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(route(trip), style = PleosType.BodyLargeStrong, color = AutoLogColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${CsvExporter.purposeLabel(trip.purpose, trip.businessCategory)} · ${syncLabel(trip)}",
                style = PleosType.BodySmall,
                color = if (trip.syncState == SyncState.FAILED) AutoLogColors.negative else AutoLogColors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(km(trip), style = PleosType.TitleSmall, color = AutoLogColors.text)
    }
}

/**
 * 최신순 목록용. key를 쓰는 LazyColumn은 맨 위에 새 항목이 들어오면 보던 항목에 위치를 고정해
 * 새 운행이 화면 위로 숨는다(에뮬레이터에서 확인). 맨 위를 보고 있었다면 새 항목으로 따라 올라간다.
 */
@Composable
private fun rememberNewestFirstListState(newestKey: String?): LazyListState {
    val state = rememberLazyListState()
    LaunchedEffect(newestKey) {
        if (state.firstVisibleItemIndex <= 1) state.scrollToItem(0)
    }
    return state
}

private fun whenLabel(trip: TripEntity): String {
    val start = trip.startedAt.atZone(PurposeEstimator.SEOUL)
    return "${DAY.format(start)} ${TIME.format(start)}"
}

private fun route(trip: TripEntity): String {
    val from = CsvExporter.endpointLabel(trip.originType, trip.originLabel)
    val to = CsvExporter.endpointLabel(trip.destType, trip.destLabel)
    return "$from → $to"
}

private fun km(trip: TripEntity) = String.format(Locale.ROOT, "%.1fkm", trip.distanceKm)

private fun syncLabel(trip: TripEntity) = when (trip.syncState) {
    SyncState.SYNCED -> "전송됨"
    SyncState.PENDING -> if (trip.answerPending) "확인 중" else "전송 대기"
    SyncState.FAILED -> "전송 실패(${trip.syncErrorCode}) · 관리자 문의"
}
