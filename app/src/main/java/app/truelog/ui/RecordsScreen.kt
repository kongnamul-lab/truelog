package app.truelog.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.truelog.record.RecordSummary
import app.truelog.record.Recorder

private const val RECENT_DAYS = 30L

/**
 * 기억한 순간 목록 (정차 중 확인용, v0.7).
 * - 기본: 하루 단위 보기. ‹ › 로 기록이 있는 날만 건너뛴다 (Search 버튼 없음, 누르면 바로 바뀜)
 * - [전체 30일]: 날짜 머리글이 붙은 30일 목록 + "이전 기록 보기"
 * - [차량 경고만]: 두 보기 모두에 걸리는 필터
 * - 행: 아이콘 + 짧은 제목(굵게) + 숫자 변화(작게) + 시각(오른쪽)
 */
@Composable
fun RecordsScreen(recorder: Recorder, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val all by recorder.records.collectAsState()
    var warningOnly by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var showOlder by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<Long?>(null) }

    val records = if (warningOnly) all.filter { it.isWarning } else all
    val days = records.map { PlainText.dayIndex(it.triggeredAt) }.distinct().sortedDescending()
    val day = selectedDay?.takeIf { it in days } ?: days.firstOrNull()
    val dayPos = days.indexOf(day)
    val today = PlainText.dayIndex(System.currentTimeMillis())
    val recentCount = all.count { today - PlainText.dayIndex(it.triggeredAt) < RECENT_DAYS }

    Column(
        Modifier.fillMaxSize().background(Pleos.Bg).padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicButton("← 처음 화면", onClick = onBack)
            Spacer(Modifier.width(24.dp))
            Column {
                Text("기억한 순간", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100)
                Text(
                    "최근 30일 ${recentCount}건 · 정비소에 보여 줄 순간을 골라 리포트로 보낼 수 있어요",
                    fontSize = 22.sp, color = Pleos.Text64,
                )
            }
        }

        // 날짜 선택기 + 칩
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!showAll) {
                val olderDay = days.getOrNull(dayPos + 1)
                val newerDay = days.getOrNull(dayPos - 1)
                ArrowButton("‹", enabled = olderDay != null) { selectedDay = olderDay }
                Box(Modifier.width(420.dp), contentAlignment = Alignment.Center) {
                    val count = records.count { PlainText.dayIndex(it.triggeredAt) == day }
                    val t = records.firstOrNull { PlainText.dayIndex(it.triggeredAt) == day }?.triggeredAt
                    Text(
                        if (t == null) "기록 없음" else PlainText.dayTitle(t) + " · ${count}건",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100,
                    )
                }
                ArrowButton("›", enabled = newerDay != null) { selectedDay = newerDay }
            } else {
                Text("최근 30일 전체", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100)
            }
            Spacer(Modifier.weight(1f))
            Chip("전체 30일", selected = showAll) { showAll = !showAll }
            Spacer(Modifier.width(16.dp))
            Chip("차량 경고만", selected = warningOnly) { warningOnly = !warningOnly; selectedDay = null }
        }

        if (records.isEmpty()) {
            Text(
                if (warningOnly) "차량 경고 기록이 없어요."
                else "아직 기억한 순간이 없어요. 급제동·배터리 20% 미만·출력 제한 경고가 오면 자동으로 남아요.",
                fontSize = 26.sp, color = Pleos.Text64,
            )
        }

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false).clip(RoundedCornerShape(24.dp)).background(Pleos.Surface),
        ) {
            if (!showAll) {
                items(records.filter { PlainText.dayIndex(it.triggeredAt) == day }, key = { it.id }) { r -> RecordRow(r, onOpen) }
            } else {
                val (recent, older) = records.partition { today - PlainText.dayIndex(it.triggeredAt) < RECENT_DAYS }
                val visible = if (showOlder) records else recent
                visible.groupBy { PlainText.dayIndex(it.triggeredAt) }.toSortedMap(reverseOrder()).forEach { (d, list) ->
                    item(key = "day-$d") {
                        Text(
                            PlainText.dayLabel(list.first().triggeredAt) + "  ${list.size}건",
                            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Pleos.Text64,
                            modifier = Modifier.fillMaxWidth().background(Pleos.Bg).padding(horizontal = 40.dp, vertical = 14.dp),
                        )
                    }
                    items(list, key = { it.id }) { r -> RecordRow(r, onOpen) }
                }
                if (older.isNotEmpty()) {
                    item(key = "older") {
                        Text(
                            if (showOlder) "이전 기록 접기" else "이전 기록 보기 (${older.size}건)",
                            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100,
                            modifier = Modifier.fillMaxWidth().clickable { showOlder = !showOlder }.padding(horizontal = 40.dp, vertical = 28.dp),
                        )
                    }
                }
            }
            item(key = "note") {
                Text(
                    "90일이 지난 운전 중 순간은 자동으로 정리돼요. 차량 경고·직접 저장·리포트를 보낸 기록은 남아요.",
                    fontSize = 20.sp, color = Pleos.Text64,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun ArrowButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Pleos.Basic else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = if (enabled) Pleos.Text100 else Pleos.Line2)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(64.dp).clip(RoundedCornerShape(32.dp))
            .background(if (selected) Pleos.Filled else Pleos.Basic)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            (if (selected) "✓ " else "") + label,
            fontSize = 24.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else Pleos.Text100,
        )
    }
}

@Composable
private fun RecordRow(r: RecordSummary, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(horizontal = 40.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MomentIcon(PlainText.kind(r))
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(PlainText.title(r), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100)
                if (r.simulated) { Spacer(Modifier.width(12.dp)); Tag("데모") }
                if (r.shared) { Spacer(Modifier.width(12.dp)); Tag("보냄") }
                if (!r.unchanged) { Spacer(Modifier.width(12.dp)); Tag("내용 바뀜", Pleos.Warning) }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                (r.note?.let { "\u201C$it\u201D · " } ?: if (r.hasAudioNote) "음성 메모 · " else "") + PlainText.sub(r),
                fontSize = 22.sp, color = if (r.note != null) Pleos.Text100 else Pleos.Text64,
                maxLines = 1,
            )
        }
        Text(PlainText.time(r.triggeredAt), fontSize = 26.sp, color = Pleos.Text84)
        Spacer(Modifier.width(20.dp))
        Text("›", fontSize = 36.sp, color = Pleos.Text64)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Pleos.Line))
}

@Composable
private fun Tag(label: String, color: Color = Pleos.Text64) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(Pleos.Basic).padding(horizontal = 10.dp, vertical = 2.dp),
    ) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color) }
}

/**
 * 직접 그린 단순 기호 (48dp 그리드). 실제 차량 표시등 모양은 쓰지 않는다(법무 원칙).
 * 경고 = 주황 배경 삼각형, 급제동 = 아래 화살표와 멈춤선, 배터리 = 배터리 윤곽, 직접 저장 = 책갈피.
 */
@Composable
private fun MomentIcon(kind: MomentKind) {
    val warn = kind == MomentKind.WARNING
    val bg = if (warn) Pleos.Warning.copy(alpha = 0.16f) else Pleos.Basic
    val fg = if (warn) Color(0xFFCC6E00) else Pleos.Filled
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(32.dp)) {
            val w = size.width
            val s = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (kind) {
                MomentKind.WARNING -> {
                    val p = Path().apply { moveTo(w * 0.5f, w * 0.1f); lineTo(w * 0.92f, w * 0.86f); lineTo(w * 0.08f, w * 0.86f); close() }
                    drawPath(p, fg, style = s)
                    drawLine(fg, Offset(w * 0.5f, w * 0.38f), Offset(w * 0.5f, w * 0.6f), strokeWidth = s.width, cap = StrokeCap.Round)
                    drawCircle(fg, radius = s.width * 0.6f, center = Offset(w * 0.5f, w * 0.73f))
                }
                MomentKind.HARD_BRAKE -> {
                    drawLine(fg, Offset(w * 0.5f, w * 0.1f), Offset(w * 0.5f, w * 0.66f), strokeWidth = s.width, cap = StrokeCap.Round)
                    val head = Path().apply { moveTo(w * 0.28f, w * 0.46f); lineTo(w * 0.5f, w * 0.68f); lineTo(w * 0.72f, w * 0.46f) }
                    drawPath(head, fg, style = s)
                    drawLine(fg, Offset(w * 0.18f, w * 0.88f), Offset(w * 0.82f, w * 0.88f), strokeWidth = s.width, cap = StrokeCap.Round)
                }
                MomentKind.LOW_BATTERY -> {
                    drawRoundRect(fg, Offset(w * 0.08f, w * 0.28f), Size(w * 0.74f, w * 0.44f), CornerRadius(w * 0.08f), style = s)
                    drawLine(fg, Offset(w * 0.92f, w * 0.42f), Offset(w * 0.92f, w * 0.58f), strokeWidth = s.width, cap = StrokeCap.Round)
                    drawRect(fg, Offset(w * 0.18f, w * 0.38f), Size(w * 0.14f, w * 0.24f))
                }
                MomentKind.MANUAL -> {
                    val p = Path().apply {
                        moveTo(w * 0.25f, w * 0.1f); lineTo(w * 0.75f, w * 0.1f); lineTo(w * 0.75f, w * 0.9f)
                        lineTo(w * 0.5f, w * 0.68f); lineTo(w * 0.25f, w * 0.9f); close()
                    }
                    drawPath(p, fg, style = s)
                }
                MomentKind.HAZARD, MomentKind.OTHER -> drawCircle(fg, radius = w * 0.18f)
            }
        }
    }
}
