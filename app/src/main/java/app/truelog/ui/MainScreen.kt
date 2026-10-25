package app.truelog.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.truelog.record.Recorder
import app.truelog.signals.SignalHub

/**
 * 메인 화면 (v4 시안 A1 평상시 / A4 정차 직후).
 * 주행 중에는 Pleos가 앱 화면을 가리므로, 이 화면은 정차 중·출발 전 확인용이다.
 */
@Composable
fun MainScreen(
    recorder: Recorder,
    onOpenRecords: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onManualSave: () -> Unit,
    onOpenDev: () -> Unit,
) {
    val live by SignalHub.state.collectAsState()
    val status by recorder.status.collectAsState()
    val records by recorder.records.collectAsState()

    val parked = live.gear == "P" || (live.speedKmh ?: 0f) < 1f
    val now = System.currentTimeMillis()
    val recentSaved = status.lastSavedAt?.let { now - it < 10 * 60_000L } == true
    val weekCount = records.count { now - it.triggeredAt < 7L * 24 * 60 * 60 * 1000 }

    Column(
        modifier = Modifier.fillMaxSize().background(Pleos.Bg).padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // 헤더
        Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Pleos.Text100), contentAlignment = Alignment.Center) {
                Wave(Color.White)
            }
            Spacer(Modifier.width(20.dp))
            Text("트루로그", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100, modifier = Modifier.weight(1f))
            Row(
                Modifier.height(56.dp).clip(RoundedCornerShape(28.dp)).background(Pleos.Surface).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(if (status.lockingReason != null) Pleos.Warning else Pleos.Active))
                Spacer(Modifier.width(12.dp))
                Text(
                    when {
                        status.lockingReason != null -> "이 순간을 기억하는 중"
                        parked -> "정차 중 · 기록 대기"
                        else -> "주행 기록 중"
                    },
                    fontSize = 24.sp, color = Pleos.Text84,
                )
            }
        }

        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            // 상태 카드
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Pleos.Surface).padding(56.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            ) {
                Text("지금 차량 상태", fontSize = 26.sp, color = Pleos.Text64)
                when {
                    status.lockingReason != null -> {
                        Text("이 순간을 기억하고 있어요", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100, lineHeight = 66.sp)
                        Text("${status.lockingReason} · 전후 30초 저장까지 ${status.lockRemainingSeconds}초", fontSize = 30.sp, color = Pleos.Warning, fontWeight = FontWeight.Bold)
                    }
                    parked && recentSaved -> {
                        Text("정차했어요", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100)
                        Text("계기판 안내를 먼저 확인하세요.", fontSize = 30.sp, color = Pleos.Text84)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pleos.Bg).padding(horizontal = 28.dp, vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "방금 '${status.lastSavedReason}' 순간을 기억했어요. 리포트는 기록에서 보낼 수 있어요.",
                                fontSize = 26.sp, color = Pleos.Text100, modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(20.dp))
                            BasicButton("기록 보기") { status.lastSavedId?.let(onOpenRecord) }
                        }
                    }
                    parked -> {
                        Text("정차 중", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100)
                        Text("최근 5분은 계속 기록하고 있어요.\n이상 순간이 오면 자동으로 기억해요.", fontSize = 30.sp, color = Pleos.Text84, lineHeight = 44.sp)
                    }
                    else -> {
                        Text("정상 주행 중", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100)
                        Text("이상 순간이 오면 자동으로 기억해요.", fontSize = 30.sp, color = Pleos.Text84)
                    }
                }
            }

            // 차량 신호
            Column(
                Modifier.width(680.dp).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Pleos.Surface).padding(horizontal = 40.dp, vertical = 16.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(84.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("차량 신호", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100, modifier = Modifier.weight(1f))
                    Text("실시간", fontSize = 22.sp, color = Pleos.Text64)
                }
                SignalLine("속도", live.speedKmh?.let { "%.0f km/h".format(it) })
                SignalLine("기어", live.gear)
                SignalLine("브레이크", live.brakePressed?.let { if (it) "밟음" else "뗌" })
                SignalLine("배터리 잔량", live.batterySoc?.let { "%.0f%%".format(it) }, warn = (live.batterySoc ?: 100f) < 20f)
                SignalLine(
                    "출력 제한 경고",
                    when {
                        live.powerDownSimulated -> "켜짐 (시뮬레이션)"
                        live.powerDownWarning == true -> "켜짐"
                        live.powerDownWarning == false -> "없음"
                        else -> null
                    },
                    warn = live.powerDownWarning == true || live.powerDownSimulated,
                )
                SignalLine("이번 주 기억한 순간", "${weekCount}건", last = true)
            }
        }

        // 최근 5분 리본
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Pleos.Surface).padding(horizontal = 40.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("최근 5분 주행 기록", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Pleos.Text100)
                Spacer(Modifier.width(16.dp))
                Text("이상 순간이 없으면 계속 덮어써요", fontSize = 22.sp, color = Pleos.Text64, modifier = Modifier.weight(1f))
                BasicButton("기록 보기 (${records.size})", onClick = onOpenRecords)
                Spacer(Modifier.width(12.dp))
                BasicButton("방금 상황 저장", onClick = onManualSave)
            }
            Ribbon(status.ribbon, locking = status.lockingReason != null)
            Row(Modifier.fillMaxWidth()) {
                Text("5분 전", fontSize = 20.sp, color = Pleos.Text64, modifier = Modifier.weight(1f))
                Text(
                    "개발 정보",
                    fontSize = 20.sp, color = Pleos.Text64,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpenDev).padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text("지금 · ${live.speedKmh?.let { "%.0f km/h".format(it) } ?: "-"}", fontSize = 20.sp, color = Pleos.Text100, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Wave(color: Color) {
    Canvas(Modifier.size(36.dp)) {
        val s = size.width / 48f
        val p = Path().apply {
            moveTo(6 * s, 30 * s); lineTo(14 * s, 30 * s); lineTo(19 * s, 18 * s); lineTo(25 * s, 36 * s); lineTo(30 * s, 26 * s); lineTo(42 * s, 26 * s)
        }
        drawPath(p, color, style = Stroke(width = 3.6f * s, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun SignalLine(label: String, value: String?, warn: Boolean = false, last: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().height(92.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(if (warn) Pleos.Warning else if (value != null) Pleos.Active else Pleos.Gray400))
            Spacer(Modifier.width(16.dp))
            Text(label, fontSize = 28.sp, color = Pleos.Text100, modifier = Modifier.weight(1f))
            Text(value ?: "수신 대기", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = if (value == null) Pleos.Text64 else if (warn) Pleos.Warning else Pleos.Text100)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Pleos.Line))
    }
}

@Composable
fun BasicButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Pleos.Basic, contentColor = Pleos.Text100),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.height(80.dp),
    ) { Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Ribbon(points: List<Float?>, locking: Boolean) {
    val maxV = (points.filterNotNull().maxOrNull() ?: 0f).coerceAtLeast(20f)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val n = 60
        val step = size.width / n
        drawLine(Pleos.Line2, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 2f)
        val offset = n - points.size
        points.forEachIndexed { i, v ->
            if (v == null) return@forEachIndexed
            val h = (v / maxV) * (size.height - 8f)
            val x = (i + offset) * step
            val color = if (locking && i >= points.size - 6) Pleos.Warning else if (i >= points.size - 6) Pleos.Text100 else Pleos.Gray400
            drawRect(color, Offset(x + step * 0.15f, size.height - h), androidx.compose.ui.geometry.Size(step * 0.7f, h))
        }
    }
}
