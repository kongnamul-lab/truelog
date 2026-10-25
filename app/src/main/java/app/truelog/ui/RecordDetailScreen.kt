package app.truelog.ui

import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.truelog.record.RecordDetail
import app.truelog.record.RecordSummary
import app.truelog.record.Sample
import app.truelog.report.ReportPayload
import app.truelog.report.ShareScope
import app.truelog.signals.SignalHub
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Pleos Connect 라이트 테마 토큰
private val Bg = Color(0xFFF7F8FA)
private val Surface = Color(0xFFFFFFFF)
private val Line = Color(0xFFE0E1E6)
private val Text100 = Color(0xFF131417)
private val Text64 = Color(0xA3131417)
private val Filled = Color(0xFF44464E)
private val Active = Color(0xFF02C265)
private val Warning = Color(0xFFFF8A00)

private const val WINDOW_MS = 30_000L

/**
 * 정차 후 기록 상세 (v4 시안 B1 기준).
 * 정비사가 바로 쓰는 정보: 재현 조건 / 전후 30초 데이터 / 반복 패턴 / 진단기 대조 안내.
 * 원인 추측은 하지 않는다.
 */
@Composable
fun RecordDetailScreen(
    detail: RecordDetail,
    similar: List<RecordSummary>,
    onBack: () -> Unit,
    onShared: (String) -> Unit = {},
) {
    val r = detail.summary
    val t0 = r.triggeredAt
    var shareStep by remember(r.id) { mutableStateOf(ShareStep.NONE) }
    var includeDriving by remember(r.id) { mutableStateOf(true) }
    var forMechanic by remember(r.id) { mutableStateOf(false) }
    var reportUrl by remember(r.id) { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
    Row(
        modifier = Modifier.fillMaxSize().background(Bg).padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 왼쪽: 무엇이 있었나 + 정비소에 줄 정보
        Column(
            modifier = Modifier.width(620.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x0D131417), contentColor = Text100),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(72.dp),
            ) { Text("← 기록 목록", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

            Panel {
                Text(
                    r.reason + if (r.simulated) " (시뮬레이션)" else "",
                    fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Text100,
                )
                Spacer(Modifier.height(6.dp))
                Text(dateTime(t0) + " · 전후 30초 · 데이터 ${r.sampleCount}개", fontSize = 20.sp, color = Text64)
                if (r.simulated) {
                    Spacer(Modifier.height(8.dp))
                    Text("데모용 시뮬레이션 신호로 만든 기록이에요. 실제 차량 경고가 아니에요.", fontSize = 18.sp, color = Warning)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (r.unchanged) Active else Warning))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (r.unchanged) "기록 이후 변경 없음" else "기록 이후 내용이 바뀌었어요",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Text100,
                    )
                }
            }

            Panel {
                Text("재현 조건", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Text100)
                Spacer(Modifier.height(4.dp))
                Text("이 순간 차량 상태예요. 시운전 때 같은 조건을 만들어 볼 수 있어요.", fontSize = 18.sp, color = Text64)
                Spacer(Modifier.height(12.dp))
                ConditionRows(detail.samples, t0)
            }

            Panel {
                Text("반복 패턴 (최근 14일)", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Text100)
                Spacer(Modifier.height(8.dp))
                if (similar.isEmpty()) {
                    Text("같은 계기로 남은 다른 기록은 없어요.", fontSize = 20.sp, color = Text64)
                } else {
                    Text("같은 \"${r.reason}\" 기록이 ${similar.size + 1}번 있어요.", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Text100)
                    similar.take(5).forEach { s ->
                        Spacer(Modifier.height(8.dp))
                        Text("${dateTime(s.triggeredAt)} · ${s.conditions.ifEmpty { "초기 버전 기록 (조건 없음)" }}", fontSize = 18.sp, color = Text100)
                    }
                }
            }

            Panel {
                Text("정비소 진단기와 함께 보기", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Text100)
                Spacer(Modifier.height(8.dp))
                Text(
                    "진단기에서 ${time(t0)} 전후에 저장된 고장 코드를 함께 확인하면, 이 기록의 운전 상황과 시간으로 맞춰 볼 수 있어요.",
                    fontSize = 20.sp, color = Text100,
                )
            }

            Button(
                onClick = { shareStep = ShareStep.SCOPE },
                colors = ButtonDefaults.buttonColors(containerColor = Filled),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(88.dp),
            ) { Text("리포트 보내기", fontSize = 26.sp, fontWeight = FontWeight.Bold) }

            Text(
                "운전자 측 참고 기록이에요. 공식 사고 기록(EDR)을 대신하지 않고, 고장 원인을 진단하지 않아요.",
                fontSize = 16.sp, color = Text64,
            )
        }

        // 오른쪽: 전후 30초 데이터
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Panel {
                Text("전후 30초 주행 데이터", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Text100)
                Spacer(Modifier.height(16.dp))
                ChartLabel("속도 (km/h)", maxLabel(detail.samples.mapNotNull { it.speedKmh }, "km/h"))
                FlatOr(detail.samples.mapNotNull { it.speedKmh }, "km/h") { SpeedChart(detail.samples, t0) }
                Spacer(Modifier.height(20.dp))
                ChartLabel("브레이크 (밟은 구간)", null)
                BrakeChart(detail.samples, t0)
                Spacer(Modifier.height(20.dp))
                ChartLabel("조향각 (°)", maxLabel(detail.samples.mapNotNull { it.steeringDeg?.let { a -> kotlin.math.abs(a) } }, "°"))
                FlatOr(detail.samples.mapNotNull { it.steeringDeg }, "°") { SteeringChart(detail.samples, t0) }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("-30초", fontSize = 18.sp, color = Text64)
                    Text("${r.reason} ${time(t0)}", fontSize = 18.sp, color = Text100, fontWeight = FontWeight.Bold)
                    Text("+30초", fontSize = 18.sp, color = Text64)
                }
            }
            Panel {
                Text("기록 확인 정보", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Text100)
                Spacer(Modifier.height(8.dp))
                Text("저장할 때 만든 고유값(SHA-256)을 지금 다시 계산해 비교했어요.", fontSize = 18.sp, color = Text100)
                Spacer(Modifier.height(8.dp))
                Text("고유값  ${r.hash}", fontSize = 14.sp, color = Text64, fontFamily = FontFamily.Monospace)
                Text("앞 기록  ${detail.prevHash}", fontSize = 14.sp, color = Text64, fontFamily = FontFamily.Monospace)
            }
        }
    }

    when (shareStep) {
        ShareStep.SCOPE -> ShareScopeOverlay(
            reason = r.reason,
            includeDriving = includeDriving,
            onIncludeDriving = { includeDriving = it },
            forMechanic = forMechanic,
            onForMechanic = { forMechanic = it },
            onCancel = { shareStep = ShareStep.NONE },
            onConfirm = {
                reportUrl = ReportPayload.build(detail, ShareScope(includeDrivingData = includeDriving))
                SignalHub.log("리포트 링크 생성: ${reportUrl?.length ?: 0}자")
                android.util.Log.i("TrueLog", "REPORT_URL ${reportUrl}")
                onShared(r.id) // 보낸 기록은 자동 정리에서 제외
                shareStep = ShareStep.QR
            },
        )
        ShareStep.QR -> reportUrl?.let { url ->
            QrOverlay(url = url, includeDriving = includeDriving, forMechanic = forMechanic, onClose = { shareStep = ShareStep.NONE })
        }
        ShareStep.NONE -> Unit
    }
    }
}

private enum class ShareStep { NONE, SCOPE, QR }

/** v4 시안 B2: 공유 범위 선택 (정차 중, 운전자가 직접 열 때만). */
@Composable
private fun ShareScopeOverlay(
    reason: String,
    includeDriving: Boolean,
    onIncludeDriving: (Boolean) -> Unit,
    forMechanic: Boolean,
    onForMechanic: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0x7A131417)).clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(1000.dp).clip(RoundedCornerShape(32.dp)).background(Surface).padding(horizontal = 56.dp, vertical = 44.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("리포트를 어디로 보낼까요?", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Text100)
            Text("$reason 기록", fontSize = 24.sp, color = Text64)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Bg).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TabChip("내 휴대폰으로", selected = !forMechanic, modifier = Modifier.weight(1f)) { onForMechanic(false) }
                TabChip("정비소 직원에게 보여주기", selected = forMechanic, modifier = Modifier.weight(1f)) { onForMechanic(true) }
            }
            Text("리포트에 넣을 내용", fontSize = 22.sp, color = Text64)
            CheckRow("경고·기록 정보", "계기·시각·재현 조건·변경 없음 확인 (항상 포함)", checked = true, enabled = false) {}
            CheckRow("주행 데이터", "속도·브레이크·조향 전후 30초", checked = includeDriving, enabled = true, onChange = onIncludeDriving)
            CheckRow("위치", "이 버전은 위치를 기록하지 않아요", checked = false, enabled = false) {}
            Text("QR로 연 리포트는 24시간 동안만 보여요. 원본 기록은 차 안에만 남고, 서버로 보내지 않아요.", fontSize = 20.sp, color = Text64)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x0D131417), contentColor = Text100),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(88.dp),
                ) { Text("취소", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Filled),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(88.dp),
                ) { Text("QR 코드 만들기", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(80.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) Surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 24.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = Text100)
    }
}

@Composable
private fun CheckRow(title: String, sub: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(80.dp).clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Filled),
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 26.sp, color = if (enabled || checked) Text100 else Text64)
            Text(sub, fontSize = 20.sp, color = Text64)
        }
    }
}

/** v4 시안 B3: QR 코드. */
@Composable
private fun QrOverlay(url: String, includeDriving: Boolean, forMechanic: Boolean, onClose: () -> Unit) {
    val bitmap = remember(url) { runCatching { ReportPayload.qrBitmap(url) }.getOrNull() }
    Box(Modifier.fillMaxSize().background(Bg).clickable(enabled = false) {}) {
        Row(Modifier.fillMaxSize().padding(40.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Box(
                Modifier.width(620.dp).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Surface),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap.asImageBitmap(),
                        contentDescription = "리포트 QR 코드",
                        modifier = Modifier.size(580.dp),
                        filterQuality = FilterQuality.None, // 확대할 때 칸 경계가 번지지 않게(인식률)
                    )
                } else {
                    Text("QR 코드를 만들지 못했어요", fontSize = 24.sp, color = Warning)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(Surface).padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    if (forMechanic) "정비소 직원 휴대폰으로 찍어 주세요" else "휴대폰 카메라로 찍어 주세요",
                    fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Text100,
                )
                Text("앱 설치나 로그인 없이 리포트가 열려요.", fontSize = 24.sp, color = Text64)
                listOf(
                    "휴대폰 기본 카메라로 QR 코드를 비춰요",
                    "뜨는 링크를 눌러 리포트를 열어요",
                    "PDF로 저장하거나 원하는 곳으로 공유해요",
                ).forEachIndexed { i, step ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(Bg), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Text100)
                        }
                        Spacer(Modifier.width(20.dp))
                        Text(step, fontSize = 26.sp, color = Text100)
                    }
                }
                Text(
                    "들어간 내용: 기록 정보" + (if (includeDriving) " · 주행 데이터 전후 30초" else "") + " · 위치 없음",
                    fontSize = 22.sp, color = Text64,
                )
                Text("리포트는 24시간 동안만 보여요.", fontSize = 22.sp, color = Text64)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Filled),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(88.dp),
                ) { Text("완료", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface).padding(28.dp),
    ) { content() }
}

@Composable
private fun ConditionRows(samples: List<Sample>, t0: Long) {
    val at = samples.minByOrNull { kotlin.math.abs(it.t - t0) }
    val before = samples.lastOrNull { it.t <= t0 - 5_000 }
    val rows = listOf(
        "속도" to (at?.speedKmh?.let { "%.0f km/h".format(it) } ?: "기록 없음"),
        "감지 직전 5초 최고 속도" to (samples.filter { it.t in (t0 - 5_000)..t0 }.mapNotNull { it.speedKmh }.maxOrNull()?.let { "%.0f km/h".format(it) } ?: "기록 없음"),
        "5초 전 속도" to (before?.speedKmh?.let { "%.0f km/h".format(it) } ?: "기록 없음"),
        "기어" to (at?.gear ?: "기록 없음"),
        "브레이크" to (at?.brake?.let { if (it) "밟음" else "밟지 않음" } ?: "기록 없음"),
        "조향각" to (at?.steeringDeg?.let { "%.0f°".format(it) } ?: "기록 없음"),
        "배터리 잔량" to (at?.soc?.let { "%.0f%%".format(it) } ?: "기록 없음"),
    )
    rows.forEachIndexed { i, (k, v) ->
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(k, fontSize = 22.sp, color = Text64)
            Text(v, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Text100)
        }
        if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    }
}

@Composable
private fun ChartLabel(title: String, right: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 18.sp, color = Text64)
        if (right != null) Text(right, fontSize = 18.sp, color = Text64)
    }
    Spacer(Modifier.height(6.dp))
}

/** 값이 내내 같으면 평평한 그래프 대신 "변화 없음 (값)"을 글자로 보여 준다. */
@Composable
private fun FlatOr(values: List<Float>, unit: String, chart: @Composable () -> Unit) {
    val min = values.minOrNull()
    val max = values.maxOrNull()
    if (min != null && max != null && max - min < 0.5f) {
        Box(
            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(Bg),
            contentAlignment = Alignment.Center,
        ) { Text("변화 없음 (%.0f%s)".format(min, unit), fontSize = 22.sp, color = Text64) }
    } else {
        chart()
    }
}

private fun maxLabel(values: List<Float>, unit: String): String? =
    values.maxOrNull()?.let { "최대 %.0f %s".format(it, unit) }

private fun DrawScope.xOf(t: Long, t0: Long): Float =
    ((t - (t0 - WINDOW_MS)).toFloat() / (2 * WINDOW_MS)).coerceIn(0f, 1f) * size.width

private fun DrawScope.triggerMarker(t0: Long) {
    val x = xOf(t0, t0)
    drawLine(Warning, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
}

@Composable
private fun SpeedChart(samples: List<Sample>, t0: Long) {
    val maxV = (samples.mapNotNull { it.speedKmh }.maxOrNull() ?: 0f).coerceAtLeast(10f)
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        drawLine(Line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        val path = Path()
        var started = false
        samples.forEach { s ->
            val v = s.speedKmh ?: return@forEach
            val x = xOf(s.t, t0)
            val y = size.height - (v / maxV) * size.height
            if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
        }
        if (started) drawPath(path, Text100, style = Stroke(width = 4f))
        triggerMarker(t0)
    }
}

@Composable
private fun BrakeChart(samples: List<Sample>, t0: Long) {
    Canvas(Modifier.fillMaxWidth().height(40.dp)) {
        drawRect(Line, Offset(0f, size.height / 2 - 2f), Size(size.width, 4f))
        val step = 100L // 샘플 간격 0.1초
        samples.filter { it.brake == true }.forEach { s ->
            val x = xOf(s.t, t0)
            val w = (xOf(s.t + step, t0) - x).coerceAtLeast(2f)
            drawRect(Filled, Offset(x, 4f), Size(w, size.height - 8f))
        }
        triggerMarker(t0)
    }
}

@Composable
private fun SteeringChart(samples: List<Sample>, t0: Long) {
    val maxA = (samples.mapNotNull { it.steeringDeg?.let { a -> kotlin.math.abs(a) } }.maxOrNull() ?: 0f).coerceAtLeast(30f)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val mid = size.height / 2
        drawLine(Line, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2f)
        val path = Path()
        var started = false
        samples.forEach { s ->
            val a = s.steeringDeg ?: return@forEach
            val x = xOf(s.t, t0)
            val y = mid - (a / maxA) * mid
            if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
        }
        if (started) drawPath(path, Text100, style = Stroke(width = 4f))
        triggerMarker(t0)
    }
}

private fun dateTime(t: Long): String = SimpleDateFormat("M월 d일 HH:mm:ss", Locale.KOREA).format(Date(t))
private fun time(t: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(t))
