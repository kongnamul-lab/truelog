package app.truelog.signals

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Android Automotive 표준 차량 속성: 속도, 기어, 주행 가능 거리, 비상등.
 */
class CarPropertyBridge(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var hazardState: Boolean? = null
    private var hazardSwitch: Boolean? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> (value.value as? Float)?.let { ms ->
                    main.post { SignalHub.update { it.copy(speedKmh = ms * 3.6f) } }
                }
                VehiclePropertyIds.GEAR_SELECTION -> (value.value as? Int)?.let { g ->
                    main.post { SignalHub.update { it.copy(gear = gearName(g)) } }
                }
                VehiclePropertyIds.RANGE_REMAINING -> (value.value as? Float)?.let { m ->
                    main.post { SignalHub.update { it.copy(rangeKm = m / 1000f) } }
                }
                VehiclePropertyIds.HAZARD_LIGHTS_STATE -> (value.value as? Int)?.let { v ->
                    hazardState = v == LIGHT_ON
                    publishHazard()
                }
                VehiclePropertyIds.HAZARD_LIGHTS_SWITCH -> (value.value as? Int)?.let { v ->
                    hazardSwitch = v == LIGHT_ON
                    publishHazard()
                }
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            main.post { SignalHub.log("차량 속성 오류: ${VehiclePropertyIds.toString(propertyId)}") }
        }
    }

    private fun publishHazard() {
        val on = hazardState == true || hazardSwitch == true
        main.post { SignalHub.update { it.copy(hazardOn = on) } }
    }

    fun start(canReadSpeed: Boolean, canReadLights: Boolean, canReadEnergy: Boolean) {
        try {
            val c = Car.createCar(appContext) ?: run {
                SignalHub.log("Car 서비스 연결 실패")
                return
            }
            car = c
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            manager = m
            SignalHub.update { it.copy(carServiceReady = true) }

            if (canReadSpeed) register(m, VehiclePropertyIds.PERF_VEHICLE_SPEED, 10f, "속도")
            else SignalHub.log("속도 권한이 없어 속도를 읽지 못해요")

            register(m, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE, "기어")

            if (canReadEnergy) register(m, VehiclePropertyIds.RANGE_REMAINING, CarPropertyManager.SENSOR_RATE_ONCHANGE, "주행 가능 거리")
            else SignalHub.log("에너지 권한이 없어 주행 가능 거리를 읽지 못해요")

            if (canReadLights) {
                register(m, VehiclePropertyIds.HAZARD_LIGHTS_STATE, CarPropertyManager.SENSOR_RATE_ONCHANGE, "비상등 상태")
                register(m, VehiclePropertyIds.HAZARD_LIGHTS_SWITCH, CarPropertyManager.SENSOR_RATE_ONCHANGE, "비상등 스위치")
            } else {
                SignalHub.log("외부등 권한이 없어 비상등을 읽지 못해요")
            }
        } catch (t: Throwable) {
            SignalHub.log("차량 속성 연결 실패: ${t.javaClass.simpleName} ${t.message}")
        }
    }

    private fun register(m: CarPropertyManager, propId: Int, rate: Float, label: String) {
        try {
            val ok = m.registerCallback(callback, propId, rate)
            SignalHub.log(if (ok) "$label 연결" else "$label 지원 안 됨")
        } catch (t: Throwable) {
            SignalHub.log("$label 연결 실패: ${t.javaClass.simpleName} ${t.message}")
        }
    }

    fun release() {
        runCatching { manager?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    private fun gearName(value: Int): String = when (value) {
        GEAR_PARK -> "P"
        GEAR_REVERSE -> "R"
        GEAR_NEUTRAL -> "N"
        GEAR_DRIVE -> "D"
        else -> "?($value)"
    }

    private companion object {
        // android.car.VehicleGear
        const val GEAR_NEUTRAL = 0x0001
        const val GEAR_REVERSE = 0x0002
        const val GEAR_PARK = 0x0004
        const val GEAR_DRIVE = 0x0008
        // android.car.hardware.property.VehicleLightState: OFF=0, ON=1, DAYTIME_RUNNING=2
        // android.car.hardware.property.VehicleLightSwitch: OFF=0, ON=1, ...
        const val LIGHT_ON = 1
    }
}
