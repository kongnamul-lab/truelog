package app.truelog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.truelog.BuildConfig
import app.truelog.record.Recorder
import app.truelog.signals.SignalHub
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Pleos Connect 라이트 테마 토큰
private val Bg = Color(0xFFF7F8FA)
private val Surface = Color(0xFFFFFFFF)
private val Line = Color(0xFFEDEEF2)
private val Text100 = Color(0xFF131417)
private val Text64 = Color(0xA3131417)
private val Filled = Color(0xFF44464E)
private val Active = Color(0xFF02C265)
private val Warning = Color(0xFFFF8A00)

@Composable
fun TrueLogScreen(recorder: Recorder, onManualSave: () -> Unit, onToggleSimulation: () -> Unit, onBack: () -> Unit = {}) {
    val live by SignalHub.state.collectAsState()
    val status by recorder.status.collectAsState()
    val records by recorder.records.collectAsState()
    val logs by SignalHub.logs.collectAsState()
    var selectedId by remember { mutableStateOf<String?>(null) }

    // 기록을 누르면 상세 화면(정차 후 확인용)
    val openId = selectedId
    if (openId != null) {
        val detail = remember(openId, records) { recorder.loadDetail(openId) }
        if (detail != null) {
            RecordDetailScreen(detail, recorder.similarTo(detail.summary), onBack = { selectedId = null })
            return
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 왼쪽: 실시간 차량 신호
        Column(
            modifier = Modifier.width(560.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BasicButton("← 처음 화면", onClick = onBack)
            Text("개발 정보 · v0.6", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Text100)
            Text(
                "Vehicle SDK ${BuildConfig.VEHICLE_SDK_VERSION} · " +
                    (if (live.sdkReady) "SDK 연결됨" else "SDK 연결 전") + " · " +
                    (if (live.carServiceReady) "차량 속성 연결됨" else "차량 속성 연결 전"),
                fontSize = 20.sp, color = Text64,
            )
            Card {
                SignalRow("속도", live.speedKmh?.let { "%.0f km/h".format(it) })
                SignalRow("기어", live.gear)
                SignalRow("브레이크", live.brakePressed?.let { if (it) "밟음" else "뗌" })
                SignalRow("조향각", live.steeringDeg?.let { "%.0f°".format(it) })
                SignalRow("배터리 잔량", live.batterySoc?.let { "%.0f %%".format(it) })
                SignalRow("주행 가능 거리", live.rangeKm?.let { "%.0f km".format(it) })
                // 비상등: Android CAR_EXTERIOR_LIGHTS 권한이 시스템 전용이라 일반 앱은 읽을 수 없다(10/25 확인).
                SignalRow("비상등", live.hazardOn?.let { if (it) "켜짐" else "꺼짐" } ?: "제조사 연계 시 제공", highlight = live.hazardOn == true)
                SignalRow(
                    "출력 제한 경고",
                    when {
                        live.powerDownSimulated -> "켜짐 (시뮬레이션)"
                        live.powerDownWarning == true -> "켜짐"
                        live.powerDownWarning == false -> "꺼짐"
                        else -> null
                    },
                    highlight = live.powerDownWarning == true || live.powerDownSimulated,
                )
                SignalRow("브레이크액", live.brakeFluid)
                SignalRow("엔진오일", live.engineOil, last = true)
            }
            Card {
                Text("최근 5분 상시 기록", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Text100)
                Spacer(Modifier.height(8.dp))
                Text("버퍼 ${status.bufferSeconds}초 / 300초", fontSize = 22.sp, color = Text100)
                status.lockingReason?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("잠금 중: $it · 사후 ${status.lockRemainingSeconds}초 남음", fontSize = 22.sp, color = Warning, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onManualSave,
                    colors = ButtonDefaults.buttonColors(containerColor = Filled),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                ) {
                    Text("방금 상황 저장", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onToggleSimulation,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x0D131417), contentColor = Text100),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                ) {
                    Text(
                        if (live.powerDownSimulated) "출력 제한 경고 시뮬레이션 끄기" else "출력 제한 경고 시뮬레이션 (20초 뒤 시작 · 데모용)",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // 오른쪽: 잠금 기록, 로그, SDK 시그니처
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Text("잠금 저장된 기록 ${records.size}건 · 눌러서 자세히 보기", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Text100)
                if (records.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("아직 없어요. 급제동·비상등·배터리 20% 미만·출력 제한 경고 또는 \"방금 상황 저장\"으로 만들어져요.", fontSize = 20.sp, color = Text64)
                }
                records.forEach { r ->
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedId = r.id }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(if (r.unchanged) Active else Warning))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "${r.reason}${if (r.simulated) " (시뮬레이션)" else ""} · ${timeText(r.triggeredAt)} · ${r.sampleCount}개",
                                fontSize = 22.sp, color = Text100, fontWeight = FontWeight.Bold,
                            )
                            if (r.conditions.isNotEmpty()) {
                                Text("재현 조건: ${r.conditions}", fontSize = 18.sp, color = Text100)
                            }
                            Text(
                                (if (r.unchanged) "기록 이후 변경 없음" else "변경 감지") + " · 고유값 ${r.hash.take(8)}…${r.hash.takeLast(8)}",
                                fontSize = 18.sp, color = Text64,
                            )
                        }
                    }
                }
            }
            Card {
                Text("동작 로그", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Text100)
                Spacer(Modifier.height(8.dp))
                logs.take(15).forEach { Text(it, fontSize = 18.sp, color = Text100, fontFamily = FontFamily.Monospace) }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Surface)
            .padding(28.dp),
    ) { content() }
}

@Composable
private fun SignalRow(label: String, value: String?, highlight: Boolean = false, last: Boolean = false) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 26.sp, color = Text100)
            Text(
                value ?: "수신 대기",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    highlight -> Warning
                    value == null -> Text64
                    else -> Text100
                },
            )
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

private fun timeText(t: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(t))
