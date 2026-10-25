package app.truelog.record

import android.content.Context
import app.truelog.signals.LiveState
import app.truelog.signals.SignalHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 0.1초마다 찍는 차량 상태 한 장. */
data class Sample(
    val t: Long,
    val speedKmh: Float?,
    val gear: String?,
    val brake: Boolean?,
    val steeringDeg: Float?,
    val soc: Float?,
    val hazard: Boolean?,
    val powerDown: Boolean?,
)

/** 기록을 남긴 계기. simulated = 데모용 시뮬레이션 신호(실제 차량 신호 아님). */
data class Trigger(val reason: String, val simulated: Boolean = false)

data class RecordSummary(
    val id: String,
    val reason: String,
    val simulated: Boolean,
    val triggeredAt: Long,
    val sampleCount: Int,
    val conditions: String,
    val hash: String,
    val unchanged: Boolean,
    /** 쉬운 문장용 값 (v0.6). 기록 순간 속도, 감지 직전 5초 최고 속도, 배터리. */
    val speedAt: Float? = null,
    val peakKmh: Float? = null,
    val socAt: Float? = null,
    /** 리포트로 보낸 적이 있는 기록 (자동 정리에서 제외). */
    val shared: Boolean = false,
) {
    /** 정비소에 가져갈 "차량 경고" 기록인지 (목록의 주황 점). */
    val isWarning: Boolean get() = reason == "출력 제한 경고"
}

/** 정차 후 상세 화면용 전체 기록. */
data class RecordDetail(
    val summary: RecordSummary,
    val prevHash: String,
    val samples: List<Sample>,
)

data class RecorderStatus(
    val bufferSeconds: Int = 0,
    val lockingReason: String? = null,
    val lockRemainingSeconds: Int = 0,
    /** 메인 화면 "최근 5분 주행 기록" 리본용: 5초 간격 속도(최대 60점). */
    val ribbon: List<Float?> = emptyList(),
    /** 가장 최근에 저장 완료된 기록(정차 직후 안내용). */
    val lastSavedId: String? = null,
    val lastSavedReason: String? = null,
    val lastSavedAt: Long? = null,
)

/**
 * 상시 5분 버퍼 + 이상 순간 전후 30초 잠금 저장 + SHA-256 해시 체인.
 * 해시는 "저장 이후 변경 여부를 알 수 있게" 하는 용도이며 변경을 막지는 않는다.
 * 원인 판단(고장 부품 등)은 하지 않는다. 기록 계기와 재현 조건만 남긴다.
 */
class Recorder(
    context: Context,
    private val onTrigger: (Trigger) -> Unit = {},
) {

    private val dir = File(context.filesDir, "records").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("truelog_chain", Context.MODE_PRIVATE)
    private val sharedPrefs = context.getSharedPreferences("truelog_shared", Context.MODE_PRIVATE)
    private val buffer = ArrayDeque<Sample>()

    private var pending: Pair<Trigger, Long>? = null
    private var previous: LiveState? = null
    private var tickCount = 0L
    private var ribbon: List<Float?> = emptyList()
    private var lastSaved: Triple<String, String, Long>? = null // id, 사유, 저장 시각

    /** 5분 버퍼를 5초 간격 60칸으로 줄인 속도(칸 안의 최댓값). */
    private fun buildRibbon(now: Long): List<Float?> = (59 downTo 0).map { k ->
        val from = now - (k + 1) * 5_000L
        val to = now - k * 5_000L
        buffer.filter { it.t in from until to }.mapNotNull { it.speedKmh }.maxOrNull()
    }

    private val _status = MutableStateFlow(RecorderStatus())
    val status: StateFlow<RecorderStatus> = _status.asStateFlow()

    private val _records = MutableStateFlow<List<RecordSummary>>(emptyList())
    val records: StateFlow<List<RecordSummary>> = _records.asStateFlow()

    init {
        reloadRecords()
        cleanupOld(System.currentTimeMillis())
    }

    /** QR 리포트를 만든 기록은 자동 정리에서 제외한다. */
    fun markShared(id: String) {
        if (sharedPrefs.getBoolean(id, false)) return
        sharedPrefs.edit().putBoolean(id, true).apply()
        reloadRecords()
    }

    /**
     * 90일이 지난 기록을 정리한다(개인정보 최소 보관).
     * 차량 경고 기록, 직접 저장한 기록, 리포트로 보낸 기록은 남긴다.
     */
    private fun cleanupOld(now: Long) {
        val old = _records.value.filter {
            now - it.triggeredAt > KEEP_MS && !it.isWarning && !it.shared && it.reason != "방금 상황 저장"
        }
        if (old.isEmpty()) return
        old.forEach { File(dir, "${it.id}.json").delete() }
        SignalHub.log("90일 지난 기록 ${old.size}건 정리")
        reloadRecords()
    }

    /** 0.1초마다 호출. */
    fun tick(now: Long, s: LiveState) {
        buffer.addLast(
            Sample(
                t = now,
                speedKmh = s.speedKmh,
                gear = s.gear,
                brake = s.brakePressed,
                steeringDeg = s.steeringDeg,
                soc = s.batterySoc,
                hazard = s.hazardOn,
                powerDown = s.powerDownWarning == true || s.powerDownSimulated,
            )
        )
        while (buffer.isNotEmpty() && now - buffer.first().t > BUFFER_MS) buffer.removeFirst()

        detectTrigger(now, s)?.let { trigger(it, now) }
        previous = s

        val p = pending
        if (p != null && now - p.second >= POST_MS) {
            finalize(p.first, p.second)
            pending = null
        }

        tickCount++
        if (tickCount % 10L == 0L) ribbon = buildRibbon(now) // 1초마다 갱신
        _status.value = RecorderStatus(
            bufferSeconds = ((now - (buffer.firstOrNull()?.t ?: now)) / 1000).toInt(),
            lockingReason = pending?.first?.label(),
            lockRemainingSeconds = pending?.let { ((POST_MS - (now - it.second)) / 1000).toInt().coerceAtLeast(0) } ?: 0,
            ribbon = ribbon,
            lastSavedId = lastSaved?.first,
            lastSavedReason = lastSaved?.second,
            lastSavedAt = lastSaved?.third,
        )
    }

    /** 상세 화면용: 파일을 다시 읽고 해시를 다시 계산해 변경 여부를 확인한다. */
    fun loadDetail(id: String): RecordDetail? = runCatching {
        val wrapper = JSONObject(File(dir, "$id.json").readText())
        val record = wrapper.getString("record")
        val stored = wrapper.getString("hash")
        val body = JSONObject(record)
        val arr = body.getJSONArray("samples")
        val samples = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Sample(
                t = o.getLong("t"),
                speedKmh = o.optFloatOrNull("speedKmh"),
                gear = if (o.isNull("gear")) null else o.optString("gear"),
                brake = if (o.has("brake") && !o.isNull("brake")) o.getBoolean("brake") else null,
                steeringDeg = o.optFloatOrNull("steeringDeg"),
                soc = o.optFloatOrNull("soc"),
                hazard = if (o.has("hazard") && !o.isNull("hazard")) o.getBoolean("hazard") else null,
                powerDown = if (o.has("powerDown") && !o.isNull("powerDown")) o.getBoolean("powerDown") else null,
            )
        }
        RecordDetail(
            summary = summaryOf(body, record, stored),
            prevHash = body.optString("prevHash", ""),
            samples = samples,
        )
    }.onFailure { SignalHub.log("기록 열기 실패: ${it.message}") }.getOrNull()

    /** 반복 패턴: 최근 14일 안에 같은 계기로 남은 다른 기록들. */
    fun similarTo(summary: RecordSummary): List<RecordSummary> =
        records.value.filter {
            it.id != summary.id && it.reason == summary.reason &&
                kotlin.math.abs(it.triggeredAt - summary.triggeredAt) <= PATTERN_WINDOW_MS
        }


    /** 파일 내용으로 목록·상세 공통 요약을 만든다. */
    private fun summaryOf(body: JSONObject, record: String, stored: String): RecordSummary {
        val t0 = body.getLong("triggeredAt")
        val arr = body.getJSONArray("samples")
        var at: JSONObject? = null
        var best = Long.MAX_VALUE
        var peak: Float? = null
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val t = o.getLong("t")
            val d = kotlin.math.abs(t - t0)
            if (d < best) { best = d; at = o }
            if (t in (t0 - PEAK_LOOKBACK_MS)..t0) {
                o.optFloatOrNull("speedKmh")?.let { v -> peak = maxOf(peak ?: v, v) }
            }
        }
        val id = body.getString("id")
        return RecordSummary(
            id = id,
            reason = body.getString("reason"),
            simulated = body.optBoolean("simulated", false),
            triggeredAt = t0,
            sampleCount = arr.length(),
            conditions = body.optString("conditions", ""),
            hash = stored,
            unchanged = sha256(record) == stored,
            speedAt = at?.optFloatOrNull("speedKmh"),
            peakKmh = peak,
            socAt = at?.optFloatOrNull("soc"),
            shared = sharedPrefs.getBoolean(id, false),
        )
    }

    private fun JSONObject.optFloatOrNull(key: String): Float? =
        if (!has(key) || isNull(key)) null else optDouble(key).toFloat()

    /** 정차 후 "방금 상황 저장". */
    fun manualSave(now: Long) = trigger(Trigger("방금 상황 저장"), now)

    private fun detectTrigger(now: Long, s: LiveState): Trigger? {
        val prev = previous ?: return null
        if (s.powerDownWarning == true && prev.powerDownWarning != true) return Trigger("출력 제한 경고")
        if (s.powerDownSimulated && !prev.powerDownSimulated) return Trigger("출력 제한 경고", simulated = true)
        if (s.hazardOn == true && prev.hazardOn != true) return Trigger("비상등 켜짐")
        val soc = s.batterySoc
        val prevSoc = prev.batterySoc
        if (soc != null && prevSoc != null && prevSoc >= LOW_SOC && soc < LOW_SOC) return Trigger("배터리 20% 미만")
        if (s.brakePressed == true && isHardBraking(now)) return Trigger("급제동")
        return null
    }

    /** 최근 1초 사이 속도가 기준 이상 떨어졌는지. */
    private fun isHardBraking(now: Long): Boolean {
        val current = buffer.lastOrNull()?.speedKmh ?: return false
        val oneSecondAgo = buffer.lastOrNull { now - it.t >= 1000 }?.speedKmh ?: return false
        return oneSecondAgo - current >= HARD_BRAKE_KMH_PER_S
    }

    private fun trigger(t: Trigger, now: Long) {
        if (pending != null) return // 이미 잠금 중이면 같은 기록에 포함
        pending = t to now
        SignalHub.log("기록 잠금 시작: ${t.label()} (전후 30초)")
        onTrigger(t)
    }

    private fun finalize(t: Trigger, t0: Long) {
        val samples = buffer.filter { it.t >= t0 - PRE_MS && it.t <= t0 + POST_MS }
        val atTrigger = samples.minByOrNull { kotlin.math.abs(it.t - t0) }
        // 급제동은 "1초에 15km/h 이상 감속"을 확인한 뒤 감지되므로, 감지 직전 최고 속도를 함께 남긴다.
        val peak = samples.filter { it.t in (t0 - PEAK_LOOKBACK_MS)..t0 }.mapNotNull { it.speedKmh }.maxOrNull()
        val conditions = conditionText(atTrigger, peak.takeIf { t.reason == "급제동" })
        val prevHash = prefs.getString(KEY_HEAD, GENESIS) ?: GENESIS
        val id = "TL-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.KOREA).format(Date(t0))

        val record = JSONObject().apply {
            put("format", "truelog-record-2")
            put("id", id)
            put("reason", t.reason)
            put("simulated", t.simulated)
            put("triggeredAt", t0)
            put("conditions", conditions)
            put("prevHash", prevHash)
            put("samples", JSONArray().apply {
                samples.forEach { s ->
                    put(JSONObject().apply {
                        put("t", s.t)
                        put("speedKmh", s.speedKmh ?: JSONObject.NULL)
                        put("gear", s.gear ?: JSONObject.NULL)
                        put("brake", s.brake ?: JSONObject.NULL)
                        put("steeringDeg", s.steeringDeg ?: JSONObject.NULL)
                        put("soc", s.soc ?: JSONObject.NULL)
                        put("hazard", s.hazard ?: JSONObject.NULL)
                        put("powerDown", s.powerDown ?: JSONObject.NULL)
                    })
                }
            })
        }.toString()

        val hash = sha256(record)
        File(dir, "$id.json").writeText(JSONObject().put("hash", hash).put("record", record).toString())
        prefs.edit().putString(KEY_HEAD, hash).apply()
        lastSaved = Triple(id, t.label(), System.currentTimeMillis())
        SignalHub.log("기록 저장 완료: $id · ${samples.size}개 · ${hash.take(8)}")
        reloadRecords()
    }

    /** 정비사가 시운전으로 다시 만들 수 있게 하는 "재현 조건" 한 줄. 원인 추측은 넣지 않는다. */
    private fun conditionText(s: Sample?, peakKmh: Float? = null): String {
        if (s == null) return "기록 없음"
        return listOfNotNull(
            s.speedKmh?.let { "%.0f km/h".format(it) },
            peakKmh?.let { "직전 최고 %.0f km/h".format(it) },
            s.gear?.let { "기어 $it" },
            s.brake?.let { if (it) "브레이크 밟음" else "브레이크 없음" },
            s.steeringDeg?.let { "조향 %.0f°".format(it) },
            s.soc?.let { "배터리 %.0f%%".format(it) },
            s.hazard?.takeIf { it }?.let { "비상등 켜짐" },
        ).joinToString(" · ").ifEmpty { "수신된 신호 없음" }
    }

    private fun reloadRecords() {
        _records.value = dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .sortedByDescending { it.name }
            .mapNotNull { file ->
                runCatching {
                    val wrapper = JSONObject(file.readText())
                    val record = wrapper.getString("record")
                    val stored = wrapper.getString("hash")
                    summaryOf(JSONObject(record), record, stored)
                }.getOrNull()
            }
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val BUFFER_MS = 5 * 60 * 1000L
        private const val PRE_MS = 30_000L
        private const val POST_MS = 30_000L
        private const val LOW_SOC = 20f
        private const val HARD_BRAKE_KMH_PER_S = 15f
        private const val PEAK_LOOKBACK_MS = 5_000L
        private const val KEEP_MS = 90L * 24 * 60 * 60 * 1000
        private const val PATTERN_WINDOW_MS = 14L * 24 * 60 * 60 * 1000
        private const val KEY_HEAD = "head"
        private const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"
    }
}

fun Trigger.label(): String = if (simulated) "$reason (시뮬레이션)" else reason
