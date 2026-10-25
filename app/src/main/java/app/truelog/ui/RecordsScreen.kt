package app.truelog.ui

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.truelog.record.RecordSummary
import app.truelog.record.Recorder

private const val RECENT_DAYS = 30L

/**
 * 기억한 순간 목록 (정차 중 확인용, v0.6).
 * - 날짜별로 묶는다 (오늘 / 어제 / 10월 23일 (목))
 * - 최근 30일을 기본으로 보여 주고, 그 이전은 같은 화면의 "이전 기록 보기"로 펼친다
 * - 제목은 운전자용 쉬운 문장. 정비사용 재현 조건은 상세 화면에만
 */
@Composable
fun RecordsScreen(recorder: Recorder, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val records by recorder.records.collectAsState()
    var showOlder by remember { mutableStateOf(false) }

    val today = PlainText.dayIndex(System.currentTimeMillis())
    val (recent, older) = records.partition { today - PlainText.dayIndex(it.triggeredAt) < RECENT_DAYS }
    val visible = if (showOlder) records else recent
    val groups = visible.groupBy { PlainText.dayIndex(it.triggeredAt) }.toSortedMap(reverseOrder())

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
                    "최근 30일 ${recent.size}건 · 정비소에 보여 줄 순간을 골라 리포트로 보낼 수 있어요",
                    fontSize = 22.sp, color = Pleos.Text64,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(Pleos.Warning, "차량 경고")
            Legend(Pleos.Gray400, "운전 중 순간")
        }
        if (records.isEmpty()) {
            Text(
                "아직 기억한 순간이 없어요. 급제동·배터리 20% 미만·출력 제한 경고가 오면 자동으로 남아요.",
                fontSize = 26.sp, color = Pleos.Text64,
            )
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f, fill = false).clip(RoundedCornerShape(24.dp)).background(Pleos.Surface),
        ) {
            groups.forEach { (day, list) ->
                item(key = "day-$day") {
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
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 22.sp, color = Pleos.Text84)
    }
}

@Composable
private fun RecordRow(r: RecordSummary, onOpen: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(horizontal = 40.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(if (r.isWarning) Pleos.Warning else Pleos.Gray400))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                PlainText.time(r.triggeredAt) + " · " + PlainText.title(r),
                fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100,
            )
            Spacer(Modifier.height(6.dp))
            Text(PlainText.sub(r), fontSize = 22.sp, color = Pleos.Text84)
        }
        if (r.shared) {
            Text("보냄", fontSize = 22.sp, color = Pleos.Text64)
            Spacer(Modifier.width(20.dp))
        }
        if (!r.unchanged) {
            Text("내용 바뀜", fontSize = 22.sp, color = Pleos.Warning)
            Spacer(Modifier.width(20.dp))
        }
        Text("›", fontSize = 36.sp, color = Pleos.Text64)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Pleos.Line))
}
