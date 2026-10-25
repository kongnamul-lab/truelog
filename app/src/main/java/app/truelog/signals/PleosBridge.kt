package app.truelog.signals

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import ai.pleos.playground.vehicle.listener.PowerDownWarningIndicatorListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Pleos Vehicle SDK 2.3.2 연결부 (공개 권한 API만 사용).
 * 시그니처는 SDK sources jar(Vehicle-2.3.2-sources.jar)에서 확인했다.
 * 기어·주행 가능 거리는 SDK가 "Google 표준 속성을 쓰라"며 deprecated 처리해 CarPropertyBridge에서 읽는다.
 * 모든 콜백은 메인 스레드로 옮겨 SignalHub를 갱신한다.
 */
class PleosBridge(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private val cleanups = mutableListOf<() -> Unit>()

    private fun ui(block: () -> Unit) {
        main.post(block)
    }
    private fun fail(label: String): (Exception) -> Unit = { e -> ui { SignalHub.log("$label 오류: ${e.message}") } }

    private val powerDownListener = object : PowerDownWarningIndicatorListener {
        override fun onPowerDownWarningIndicatorUpdated(state: Boolean?) =
            ui { SignalHub.update { it.copy(powerDownWarning = state) } }
        override fun onFailed(e: Exception) = fail("출력 제한 경고")(e)
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) =
            ui { SignalHub.update { it.copy(brakePressed = status) } }
        override fun onFailed(e: Exception) = fail("브레이크")(e)
    }
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) =
            ui { SignalHub.update { it.copy(steeringDeg = angle) } }
        override fun onFailed(e: Exception) = fail("조향각")(e)
    }
    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) =
            ui { SignalHub.update { it.copy(batterySoc = value?.toFloat()) } }
        override fun onFailed(e: Exception) = fail("배터리 잔량")(e)
    }
    private val brakeFluidListener = object : OilLevelStateListener {
        override fun onOilLevelUpdated(level: OilLevelState?) =
            ui { SignalHub.update { it.copy(brakeFluid = levelName(level)) } }
        override fun onFailed(e: Exception) = fail("브레이크액")(e)
    }
    private val engineOilListener = object : OilLevelStateListener {
        override fun onOilLevelUpdated(level: OilLevelState?) =
            ui { SignalHub.update { it.copy(engineOil = levelName(level)) } }
        override fun onFailed(e: Exception) = fail("엔진오일")(e)
    }

    fun start() {
        val v = try {
            Vehicle(appContext).also { it.initialize() }
        } catch (t: Throwable) {
            SignalHub.log("Pleos SDK 연결 실패(차량 서비스 없음): ${t.javaClass.simpleName}")
            return
        }
        vehicle = v
        SignalHub.update { it.copy(sdkReady = true) }
        SignalHub.log("Pleos SDK 연결 완료")
        // 서비스 바인딩이 비동기라 잠시 뒤 구독한다.
        main.postDelayed({ subscribeAll(v) }, 800)
    }

    private fun subscribeAll(v: Vehicle) {
        val safety = v.getSafety()
        val brake = v.getBrake()
        val steering = v.getSteeringWheel()
        val ev = v.getEvBattery()

        // 1) 변경 알림 등록
        step("출력 제한 경고") {
            safety.registerPowerDownWarningIndicator(powerDownListener)
            cleanups += { safety.unregisterPowerDownWarningIndicator(powerDownListener) }
        }
        step("브레이크") {
            brake.registerBrakePedalStatus(brakeListener)
            cleanups += { brake.unregisterBrakePedalStatus(brakeListener) }
        }
        step("조향각") {
            steering.registerSteeringWheelAngle(steeringListener)
            cleanups += { steering.unregisterSteeringWheelAngle(steeringListener) }
        }
        step("배터리 잔량") {
            ev.registerEvBatteryStateOfCharge(socListener)
            cleanups += { ev.unregisterEvBatteryStateOfCharge(socListener) }
        }
        step("브레이크액") {
            safety.registerBrakeFluidLevelState(brakeFluidListener)
            cleanups += { safety.unregisterBrakeFluidLevelState(brakeFluidListener) }
        }
        step("엔진오일") {
            safety.registerEngineOilLevelState(engineOilListener)
            cleanups += { safety.unregisterEngineOilLevelState(engineOilListener) }
        }

        // 2) 시작 시 현재 값 한 번 읽기 (변경 알림은 값이 바뀔 때만 오므로)
        step("초기값") {
            safety.hasPowerDownWarningIndicator({ s -> ui { SignalHub.update { it.copy(powerDownWarning = s) } } }, fail("출력 제한 경고 읽기"))
            brake.getCurrentBrakePedalStatus({ s -> ui { SignalHub.update { it.copy(brakePressed = s) } } }, fail("브레이크 읽기"))
            steering.getCurrentSteeringWheelAngle({ a -> ui { SignalHub.update { it.copy(steeringDeg = a) } } }, fail("조향각 읽기"))
            ev.getEvBatteryStateOfCharge({ p -> ui { SignalHub.update { it.copy(batterySoc = p?.toFloat()) } } }, fail("배터리 잔량 읽기"))
            safety.getBrakeFluidLevelState({ l -> ui { SignalHub.update { it.copy(brakeFluid = levelName(l)) } } }, fail("브레이크액 읽기"))
            safety.getEngineOilLevelState({ l -> ui { SignalHub.update { it.copy(engineOil = levelName(l)) } } }, fail("엔진오일 읽기"))
        }
    }

    private inline fun step(label: String, action: () -> Unit) {
        try {
            action()
            SignalHub.log("$label 연결")
        } catch (t: Throwable) {
            SignalHub.log("$label 연결 실패: ${t.message}")
        }
    }

    fun release() {
        cleanups.forEach { runCatching(it) }
        cleanups.clear()
        runCatching { vehicle?.release() }
        vehicle = null
    }

    private fun levelName(l: OilLevelState?): String? = when (l) {
        null -> null
        OilLevelState.NORMAL -> "정상"
        OilLevelState.LOW -> "부족"
        OilLevelState.HIGH -> "높음"
        else -> "확인 필요"
    }
}
