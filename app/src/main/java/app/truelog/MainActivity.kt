package app.truelog

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import app.truelog.record.Recorder
import app.truelog.signals.CarPropertyBridge
import app.truelog.signals.PleosBridge
import app.truelog.signals.SignalHub
import app.truelog.notify.MomentNotifier
import app.truelog.ui.AppRoot
import app.truelog.voice.Speaker
import app.truelog.voice.SymptomListener
import app.truelog.voice.VoicePrefs
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var pleos: PleosBridge
    private var carProperties: CarPropertyBridge? = null
    private lateinit var recorder: Recorder
    private lateinit var speaker: Speaker
    private lateinit var notifier: MomentNotifier
    private lateinit var listener: SymptomListener
    /** 한마디 기능 동의: null = 아직 안 물어봄 (첫 실행 화면), 기본 꺼짐. */
    private val voiceConsent = mutableStateOf<Boolean?>(null)
    private var simulationJob: Job? = null

    private val runtimePermissions = buildList {
        add(PERMISSION_SPEED)
        add(PERMISSION_ENERGY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(android.Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startCarProperties() }

    private val micRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            SignalHub.log(if (granted) "마이크 허용됨 (한마디 기능)" else "마이크가 허용되지 않아 한마디 기능은 쉬어요")
        }

    private fun voiceOn(): Boolean =
        voiceConsent.value == true && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun setVoice(on: Boolean) {
        VoicePrefs.set(this, on)
        voiceConsent.value = on
        if (on && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micRequest.launch(android.Manifest.permission.RECORD_AUDIO)
        }
        SignalHub.log(if (on) "한마디 기능 켬" else "한마디 기능 끔")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speaker = Speaker(this)
        notifier = MomentNotifier(this)
        listener = SymptomListener(this)
        voiceConsent.value = VoicePrefs.get(this)
        recorder = Recorder(this, onTrigger = { trigger, id ->
            notifier.notify(trigger) // 주행 중 앱 화면이 가려져도 보이는 시스템 알림
            speaker.announce(trigger) {
                // 한마디: 운전자가 켰을 때만, 직접 저장은 묻지 않는다. 한 번만 묻고 다시 묻지 않는다.
                if (voiceOn() && trigger.reason != "방금 상황 저장") {
                    notifier.notifyListening()
                    speaker.askSymptom {
                        listener.listen(
                            recorder.audioFileFor(id),
                            onText = { text -> recorder.attachNote(id, text); speaker.confirmSymptom() },
                            onAudio = { recorder.audioNoteSaved() },
                        )
                    }
                }
            }
        })
        pleos = PleosBridge(this)
        // 차량 서비스가 없는 일반 폰 에뮬레이터에서도 앱이 꺼지지 않게 한다.
        carProperties = runCatching { CarPropertyBridge(this) }
            .onFailure { SignalHub.log("차량 속성 서비스가 없는 기기예요 (Pleos 에뮬레이터에서 실행하세요)") }
            .getOrNull()

        pleos.start()
        val missing = runtimePermissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startCarProperties() else permissionRequest.launch(missing.toTypedArray())

        // 상시 기록: 0.1초마다 현재 상태를 5분 버퍼에 쌓는다.
        lifecycleScope.launch {
            while (isActive) {
                recorder.tick(System.currentTimeMillis(), SignalHub.state.value)
                delay(100)
            }
        }

        setContent {
            AppRoot(
                recorder = recorder,
                onManualSave = { recorder.manualSave(System.currentTimeMillis()) },
                onToggleSimulation = ::toggleSimulation,
                voiceConsent = voiceConsent.value,
                voiceModeLabel = listener.mode.label,
                onVoiceConsent = ::setVoice,
            )
        }
    }

    /**
     * 데모용 출력 제한 경고 시뮬레이션.
     * 주행 중에는 앱 화면이 가려져 버튼을 누를 수 없으므로, 정차 중에 예약하면 20초 뒤 켜지고 60초 뒤 꺼진다.
     * 화면·기록·로그 어디에나 "시뮬레이션"으로 표기된다.
     */
    private fun toggleSimulation() {
        if (SignalHub.state.value.powerDownSimulated || simulationJob?.isActive == true) {
            simulationJob?.cancel()
            SignalHub.update { it.copy(powerDownSimulated = false) }
            SignalHub.log("출력 제한 경고 시뮬레이션 끔")
            return
        }
        SignalHub.log("출력 제한 경고 시뮬레이션 예약: 20초 뒤 시작 (데모용)")
        simulationJob = lifecycleScope.launch {
            delay(20_000)
            SignalHub.update { it.copy(powerDownSimulated = true) }
            SignalHub.log("출력 제한 경고 시뮬레이션 켬 (데모용)")
            delay(60_000)
            SignalHub.update { it.copy(powerDownSimulated = false) }
            SignalHub.log("출력 제한 경고 시뮬레이션 자동 종료")
        }
    }

    private fun startCarProperties() {
        val granted = { p: String -> checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED }
        carProperties?.start(
            canReadSpeed = granted(PERMISSION_SPEED),
            // 비상등(CAR_EXTERIOR_LIGHTS)은 signature|privileged라 일반 앱은 받을 수 없다(10/25 확인).
            canReadLights = granted(PERMISSION_EXTERIOR_LIGHTS),
            canReadEnergy = granted(PERMISSION_ENERGY),
        )
    }

    override fun onDestroy() {
        pleos.release()
        carProperties?.release()
        speaker.release()
        listener.release()
        super.onDestroy()
    }

    private companion object {
        const val PERMISSION_SPEED = "android.car.permission.CAR_SPEED"
        const val PERMISSION_ENERGY = "android.car.permission.CAR_ENERGY"
        const val PERMISSION_EXTERIOR_LIGHTS = "android.car.permission.CAR_EXTERIOR_LIGHTS"
    }
}
