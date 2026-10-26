package app.truelog.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import app.truelog.signals.SignalHub
import java.io.File

/**
 * 한마디 증상 듣기 (v0.8).
 * 1순위: Android 표준 음성 인식(SpeechRecognizer) → 문장으로 저장. Pleos SpeechToText SDK가 공개되면 교체.
 * 2순위(플랜 B): 음성 인식 엔진이 없는 기기 → 8초 음성 메모(m4a)를 차 안에 저장.
 * 듣기는 8초 한 번뿐. 다시 묻지 않는다(주의 분산 최소화). 답하지 않아도 기록은 이미 저장돼 있다.
 */
class SymptomListener(context: Context) {

    enum class Mode(val label: String) {
        ON_DEVICE("기기 안 음성 인식"),
        RECOGNIZER("차량 음성 인식 서비스"),
        MEMO("음성 메모 (8초 녹음)"),
    }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var recorder: MediaRecorder? = null
    private var busy = false

    /** 기기 안 인식 우선(말이 기기 밖으로 나가지 않음) → 차량 음성 인식 서비스 → 음성 메모. */
    var mode: Mode = when {
        Build.VERSION.SDK_INT >= 33 && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext) }.getOrDefault(false) -> Mode.ON_DEVICE
        SpeechRecognizer.isRecognitionAvailable(appContext) -> Mode.RECOGNIZER
        else -> Mode.MEMO
    }

    init {
        SignalHub.log("한마디 듣기 방식: ${mode.label}")
    }

    /**
     * 8초 동안 듣는다.
     * @param onText 음성 인식 문장
     * @param onAudio 음성 메모 저장 완료
     */
    fun listen(audioFile: File, onText: (String) -> Unit, onAudio: () -> Unit) {
        if (busy) return
        busy = true
        tone(ToneGenerator.TONE_PROP_BEEP, 250) // 시작 "삐": 이 소리 뒤에 말하면 된다
        main.postDelayed({
            countdown(LISTEN_MS.toInt() / 1000)
            if (mode == Mode.MEMO) recordMemo(audioFile, onAudio, LISTEN_MS) else recognize(onText, audioFile, onAudio)
        }, 400)
    }

    /** 시작·끝 신호음. 미디어 볼륨 최대 크기로 낸다(작게 들린다는 피드백 반영). */
    private fun tone(type: Int, ms: Int) {
        runCatching {
            val g = ToneGenerator(AudioManager.STREAM_MUSIC, 80) // 최대치는 깨져 들림
            g.startTone(type, ms)
            main.postDelayed({ runCatching { g.release() } }, ms + 200L)
        }
    }

    /** 앱 화면·알림에 "듣는 중 n초"를 보여 주기 위한 카운트다운. */
    private fun countdown(sec: Int) {
        SignalHub.update { it.copy(listeningSec = sec) }
        if (sec <= 0) {
            SignalHub.update { it.copy(listeningSec = null) }
            tone(ToneGenerator.TONE_PROP_ACK, 300) // 끝 "삐삐": 이제 그만 말해도 된다
            return
        }
        main.postDelayed({ countdown(sec - 1) }, 1000)
    }

    private fun recognize(onText: (String) -> Unit, audioFile: File, onAudio: () -> Unit) {
        var ready = false
        val r = runCatching {
            if (mode == Mode.ON_DEVICE && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            else SpeechRecognizer.createSpeechRecognizer(appContext)
        }.getOrNull()
        if (r == null) { SignalHub.log("음성 인식을 시작하지 못했어요"); busy = false; return }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) onText(text) else SignalHub.log("한마디가 없어 넘어갔어요")
                finish()
            }
            override fun onError(error: Int) {
                SignalHub.log(
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "한마디가 없어 넘어갔어요"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 없어요"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "음성 인식에 인터넷 연결이 필요해요"
                        else -> "음성 인식 오류 ($error)"
                    }
                )
                finish()
            }
            override fun onReadyForSpeech(params: Bundle?) { ready = true; SignalHub.log("한마디 듣는 중 (8초)") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { r.startListening(intent) }.onFailure { SignalHub.log("음성 인식 시작 실패: ${it.message}"); finish() }
        // 음성 인식 서비스가 등록만 돼 있고 응답하지 않는 기기(에뮬레이터 실측 10/26)가 있다.
        // 2초 안에 듣기 시작 신호가 없으면 남은 시간은 음성 메모로 녹음하고, 이후로는 음성 메모 방식을 쓴다.
        main.postDelayed({
            if (!ready && recognizer === r) {
                SignalHub.log("음성 인식 서비스가 응답하지 않아 음성 메모로 바꿔요")
                runCatching { r.cancel(); r.destroy() }
                recognizer = null
                mode = Mode.MEMO
                recordMemo(audioFile, onAudio, LISTEN_MS - FALLBACK_MS)
            }
        }, FALLBACK_MS)
        main.postDelayed({ if (recognizer === r) runCatching { r.stopListening() } }, LISTEN_MS)
    }

    private fun recordMemo(file: File, onAudio: () -> Unit, durationMs: Long) {
        val rec = runCatching {
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(appContext) else MediaRecorder()).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.onFailure { SignalHub.log("음성 메모 녹음 실패: ${it.message}") }.getOrNull()
        if (rec == null) { busy = false; return }
        recorder = rec
        SignalHub.log("한마디 음성 메모 녹음 중 (8초)")
        main.postDelayed({
            runCatching { recorder?.stop() }.onFailure { file.delete() }
            runCatching { recorder?.release() }
            recorder = null
            busy = false
            if (file.exists() && file.length() > 0) { SignalHub.log("음성 메모 저장: ${file.name}"); onAudio() }
        }, durationMs)
    }

    private fun finish() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        busy = false
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        runCatching { recorder?.release() }
    }

    private companion object {
        const val LISTEN_MS = 8_000L
        const val FALLBACK_MS = 2_000L
    }
}
