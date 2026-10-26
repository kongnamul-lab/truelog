package app.truelog.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 첫 실행: 한마디 기능 동의 (v0.8, 법무 조건).
 * 기본은 꺼짐. 운전자가 직접 "켜기"를 눌러야 마이크를 쓴다. 정차 중에만 보이는 화면.
 */
@Composable
fun VoiceConsentScreen(modeLabel: String, onChoose: (Boolean) -> Unit) {
    Box(Modifier.fillMaxSize().background(Pleos.Bg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(1100.dp).clip(RoundedCornerShape(32.dp)).background(Pleos.Surface).padding(horizontal = 64.dp, vertical = 52.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Text("운전 중 한마디도 같이 기억할까요?", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100)
            Text("정비소에서 가장 먼저 묻는 건 \"어떤 느낌이었어요?\"예요.", fontSize = 26.sp, color = Pleos.Text84)
            Spacer(Modifier.height(4.dp))
            listOf(
                "이상한 순간이 기록되면 \"어떤 느낌이었는지 한마디 해 주세요\"라고 물어요",
                "삐 소리 뒤 8초만 듣고, 그 말을 같은 시간의 차량 기록에 붙여요. 대답하지 않아도 괜찮아요",
                "말은 기기 안 음성 인식으로 문장이 돼요. 없으면 차량의 음성 인식 서비스를 쓰고, 그것도 없으면 8초 음성 메모로 차 안에만 남겨요",
                "원본은 차 안에만 있어요. 리포트에는 직접 고를 때만 들어가요. 기록 상세에서 언제든 지울 수 있어요",
                "동승자 목소리가 같이 들어갈 수 있어요. 언제든 끌 수 있어요",
            ).forEachIndexed { i, line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Pleos.Bg), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100)
                    }
                    Spacer(Modifier.width(20.dp))
                    Text(line, fontSize = 24.sp, color = Pleos.Text100)
                }
            }
            Text("이 차에서 듣는 방식: $modeLabel", fontSize = 20.sp, color = Pleos.Text64)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { onChoose(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = Pleos.Basic, contentColor = Pleos.Text100),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(88.dp),
                ) { Text("나중에", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { onChoose(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = Pleos.Filled),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(88.dp),
                ) { Text("켜기", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
