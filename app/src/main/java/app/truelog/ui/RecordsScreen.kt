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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.truelog.record.Recorder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 기억한 순간 목록 (정차 중 확인용). */
@Composable
fun RecordsScreen(recorder: Recorder, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val records by recorder.records.collectAsState()
    Column(Modifier.fillMaxSize().background(Pleos.Bg).padding(horizontal = 48.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicButton("← 처음 화면", onClick = onBack)
            Spacer(Modifier.width(24.dp))
            Text("기억한 순간 ${records.size}건", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100)
        }
        if (records.isEmpty()) {
            Text("아직 기억한 순간이 없어요. 급제동·배터리 20% 미만·출력 제한 경고가 오면 자동으로 남아요.", fontSize = 26.sp, color = Pleos.Text64)
        }
        LazyColumn(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Pleos.Surface),
        ) {
            items(records, key = { it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(r.id) }.padding(horizontal = 40.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (r.unchanged) Pleos.Active else Pleos.Warning))
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.reason + (if (r.simulated) " (시뮬레이션)" else "") + " · " + SimpleDateFormat("M월 d일 HH:mm:ss", Locale.KOREA).format(Date(r.triggeredAt)),
                            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(r.conditions.ifEmpty { "초기 버전 기록 (조건 없음)" }, fontSize = 22.sp, color = Pleos.Text84)
                    }
                    Text(if (r.unchanged) "변경 없음" else "변경 감지", fontSize = 22.sp, color = Pleos.Text64)
                    Spacer(Modifier.width(20.dp))
                    Text("›", fontSize = 36.sp, color = Pleos.Text64)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Pleos.Line))
            }
        }
    }
}
