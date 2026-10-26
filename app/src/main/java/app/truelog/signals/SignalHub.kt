package app.truelog.signals

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 차량에서 들어온 최신 값. null = 아직 받지 못함. */
data class LiveState(
    val sdkReady: Boolean = false,
    val carServiceReady: Boolean = false,
    val speedKmh: Float? = null,          // Android 표준 PERF_VEHICLE_SPEED
    val gear: String? = null,             // Pleos Gear
    val brakePressed: Boolean? = null,    // Pleos Brake (눌림 여부만 제공)
    val steeringDeg: Float? = null,       // Pleos SteeringWheel
    val batterySoc: Float? = null,        // Pleos EvBattery (%)
    val rangeKm: Float? = null,           // Pleos Odometer 주행 가능 거리
    val hazardOn: Boolean? = null,        // Android 표준 HAZARD_LIGHTS_*
    val powerDownWarning: Boolean? = null, // Pleos Safety (실제 신호)
    val powerDownSimulated: Boolean = false, // 데모용 시뮬레이션 (항상 별도 표기)
    val brakeFluid: String? = null,       // Pleos Safety OilLevelState
    val engineOil: String? = null,        // Pleos Safety OilLevelState
    val listeningSec: Int? = null,        // 한마디 듣는 중 남은 초 (v0.8.1)
)

/** 앱 전체가 공유하는 신호 저장소. 모든 갱신은 메인 스레드에서 한다. */
object SignalHub {
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _signatures = MutableStateFlow("")
    val signatures: StateFlow<String> = _signatures.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.KOREA)

    fun update(transform: (LiveState) -> LiveState) = _state.update(transform)

    fun log(message: String) {
        val line = "${timeFormat.format(Date())}  $message"
        android.util.Log.i("TrueLog", message)
        _logs.update { (listOf(line) + it).take(60) }
    }

    fun setSignatures(text: String) {
        android.util.Log.i("TrueLog", "SDK signatures:\n$text")
        _signatures.value = text
    }
}
