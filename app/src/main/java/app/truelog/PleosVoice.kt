package app.truelog

import ai.pleos.playground.stt.SpeechToText as PleosSpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech as PleosTextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener as TtsEventListener
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import app.truelog.record.Trigger
import app.truelog.signals.SignalHub
import app.truelog.voice.Speaker
import app.truelog.voice.SymptomListener
import java.io.File
import java.util.Locale

/*
 * v0.9 — Pleos 음성 SDK(TextToSpeech·SpeechToText 3.3.1) 우선 사용.
 *
 * - 두 SDK 모두 ON_DEVICE 모드만 쓴다: 네트워크·앱 등록(클라이언트 시크릿)이 필요 없고,
 *   운전자의 말이 차 밖으로 나가지 않는다(개인정보 최소 수집 원칙).
 * - SDK를 쓸 수 없는 환경(초기화 실패, 응답 없음)에서는 v0.8의 방식으로 자동 전환한다.
 *   안내: Android TextToSpeech → 녹음 안내 음성(res/raw) / 듣기: 기기 음성 인식 → 8초 음성 메모.
 * - 문구 원칙은 v0.8과 같다: 행동 안내 + "기억했어요"만. 원인 추측·안전 보장 표현 금지.
 */

/** 주행 중 안내 음성. 1순위 Pleos TextToSpeech, 안 되면 [Speaker](v0.8). */
class VoiceOut(context: Context) {

    private val fallback = Speaker(context)
    private val main = Handler(Looper.getMainLooper())
    private var tts: PleosTextToSpeech? = null
    private var ready = false

    private var token = 0
    private var started = false
    private var pending: (() -> Unit)? = null
    private var pendingFallback: ((() -> Unit) -> Unit)? = null

    /** 개발 정보 화면 표시용. */
    val label = mutableStateOf("확인 중")

    init {
        runCatching {
            val t = PleosTextToSpeech(context.applicationContext, TtsMode.ON_DEVICE)
            val events = object : TtsEventListener {
                override fun onReady() = Unit
                override fun onStart() { main.post { started = true } }
                override fun onUpdatedRms(rms: Double) = Unit
                override fun onDone() { main.post { finish(error = false) } }
                override fun onStop() { main.post { finish(error = false) } }
                override fun onError(errorType: TtsErrorType) {
                    main.post {
                        SignalHub.log("Pleos 음성 합성 오류: ${errorType.name}")
                        finish(error = true)
                    }
                }
            }
            t.initialize(object : TtsInitListener {
                override fun onSuccess() {
                    main.post {
                        // SDK 요구 순서: initialize() 성공 뒤에 리스너 등록 (11/8 실측: 먼저 등록하면 lateinit 오류)
                        runCatching { t.addEventListener(events) }.onFailure {
                            SignalHub.log("Pleos 음성 합성 리스너 등록 실패: ${it.message}")
                            label.value = "녹음 안내 음성"
                            return@post
                        }
                        runCatching { t.setLocale(Locale.KOREA) }
                        // 내비 안내 음성과 같은 성격(짧은 운전 안내)으로 내보낸다.
                        runCatching {
                            t.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                        }
                        ready = true
                        label.value = "Pleos 음성 합성"
                        SignalHub.log("음성 안내 준비 완료 (Pleos TextToSpeech SDK, 기기 안)")
                    }
                }

                override fun onFailure(errorType: TtsErrorType) {
                    main.post {
                        label.value = "녹음 안내 음성"
                        SignalHub.log("Pleos 음성 합성을 쓸 수 없어 녹음 안내 음성으로 해요 (${errorType.name})")
                    }
                }
            })
            tts = t
        }.onFailure {
            label.value = "녹음 안내 음성"
            SignalHub.log("Pleos 음성 합성 연결 실패: ${it.message}")
        }
    }

    fun announce(trigger: Trigger, onDone: () -> Unit = {}) =
        say(textFor(trigger), { cb -> fallback.announce(trigger, cb) }, onDone)

    fun askSymptom(onDone: () -> Unit) =
        say(ASK_SYMPTOM, { cb -> fallback.askSymptom(cb) }, onDone)

    fun confirmSymptom() =
        say(SYMPTOM_SAVED, { cb -> fallback.confirmSymptom(); cb() }, {})

    private fun say(text: String, viaFallback: (() -> Unit) -> Unit, onDone: () -> Unit) {
        val t = tts
        if (!ready || t == null) { viaFallback(onDone); return }
        val my = ++token
        started = false
        pending = onDone
        pendingFallback = viaFallback
        runCatching { t.speak(text, true) }.onFailure {
            SignalHub.log("Pleos 음성 합성 요청 실패: ${it.message}")
            switchToFallback()
            return
        }
        // 엔진이 등록만 돼 있고 소리를 내지 않는 환경 대비: 3초 안에 재생이 시작되지 않으면 바꾼다.
        main.postDelayed({
            if (token == my && !started && pending != null) {
                SignalHub.log("Pleos 음성 합성이 응답하지 않아 녹음 안내 음성으로 바꿔요")
                runCatching { t.stop() }
                switchToFallback()
            }
        }, START_TIMEOUT_MS)
    }

    /** 지금 요청은 대체 방식으로 이어서 말하고, 이후로는 대체 방식만 쓴다. */
    private fun switchToFallback() {
        ready = false
        label.value = "녹음 안내 음성"
        val cb = pending
        val fb = pendingFallback
        clear()
        if (cb != null && fb != null) fb(cb)
    }

    private fun finish(error: Boolean) {
        val cb = pending ?: return
        val fb = pendingFallback
        val wasStarted = started
        clear()
        if (error && !wasStarted && fb != null) fb(cb) else cb()
    }

    private fun clear() {
        token++
        pending = null
        pendingFallback = null
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        runCatching { tts?.stop() }
        runCatching { tts?.release() }
        fallback.release()
    }

    private companion object {
        const val START_TIMEOUT_MS = 3_000L
        const val ASK_SYMPTOM = "삐 소리가 나면, 어떤 느낌이었는지 한마디 해 주세요."
        const val SYMPTOM_SAVED = "말씀도 같이 기억했어요."

        fun textFor(trigger: Trigger): String = when (trigger.reason) {
            "출력 제한 경고" -> "출력 제한 경고가 들어왔어요. 계기판 안내를 확인하고, 안전한 곳에 정차하세요. 지금 상황을 기억하고 있어요."
            "비상등 켜짐" -> "비상등이 켜졌어요. 지금 상황을 기억하고 있어요."
            "배터리 20% 미만" -> "배터리가 20퍼센트 아래로 내려갔어요. 충전 계획을 확인하세요. 이 순간을 기억했어요."
            "급제동" -> "급제동 순간을 기억했어요."
            else -> "지금 상황을 저장할게요."
        }
    }
}

/**
 * 한마디 증상 듣기 (v0.9.1).
 * 앱이 차량 마이크로 8초를 직접 녹음해(16kHz·16bit·mono) 음성 메모로 먼저 저장하고,
 * 같은 소리를 Pleos SpeechToText(ON_DEVICE)의 sendAudio()로 보내 문장으로 바꾼다.
 * → 문장 변환이 안 되는 환경에서도 운전자의 말은 음성 메모로 반드시 남는다.
 * Pleos SDK를 쓸 수 없으면 v0.8 [SymptomListener]로 넘긴다.
 */
class VoiceIn(context: Context) {

    private val appContext = context.applicationContext
    private val fallback = SymptomListener(context)
    private val main = Handler(Looper.getMainLooper())
    private var stt: PleosSpeechToText? = null
    private var ready = false

    private var busy = false
    private var waitingText = false
    private var best = ""
    private var session = 0
    private var onText: ((String) -> Unit)? = null

    /** 동의 화면·개발 정보 화면 표시용. */
    val label = mutableStateOf(fallback.mode.label)

    private val results = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit

        override fun onUpdated(stt: String, completed: Boolean) {
            main.post {
                if (!waitingText) return@post
                if (stt.isNotBlank()) best = stt
                if (completed && best.isNotBlank()) deliverText()
            }
        }

        override fun onEndedRecognition() { main.postDelayed({ deliverText() }, 300) }

        override fun onError(errorType: SttErrorType) {
            main.post {
                if (!waitingText) return@post
                SignalHub.log("문장 변환 오류 (${errorType.name}) · 음성 메모는 저장돼 있어요")
                deliverText()
            }
        }
    }

    init {
        runCatching {
            val s = PleosSpeechToText(appContext, SttMode.ON_DEVICE)
            s.initialize(object : SttInitListener {
                override fun onSuccess() {
                    main.post {
                        runCatching { s.setLocale(Locale.KOREA) }
                        s.addListener(results)
                        ready = true
                        label.value = "음성 메모 + Pleos 음성 인식 (기기 안)"
                        SignalHub.log("한마디 듣기 방식: 음성 메모 + Pleos SpeechToText SDK (기기 안)")
                    }
                }

                override fun onFailure(errorType: SttErrorType) {
                    main.post {
                        SignalHub.log("Pleos 음성 인식을 쓸 수 없어 ${fallback.mode.label}(으)로 해요 (${errorType.name})")
                        label.value = fallback.mode.label
                    }
                }
            })
            stt = s
        }.onFailure { SignalHub.log("Pleos 음성 인식 연결 실패: ${it.message}") }
    }

    /** 8초 동안 한 번만 듣는다. 다시 묻지 않는다(주의 분산 최소화). */
    fun listen(audioFile: File, onText: (String) -> Unit, onAudio: () -> Unit) {
        if (!ready || stt == null) { fallback.listen(audioFile, onText, onAudio); return }
        if (busy) return
        busy = true
        best = ""
        this.onText = onText
        val my = ++session
        tone(ToneGenerator.TONE_PROP_BEEP, 250) // 시작 "삐": 이 소리 뒤에 말하면 된다
        main.postDelayed({
            if (session != my) return@postDelayed
            countdown((LISTEN_MS / 1000).toInt(), my)
            Thread {
                val pcm = recordPcm(LISTEN_MS)
                main.post { afterRecording(pcm, audioFile, onAudio, my) }
            }.start()
        }, 400)
    }

    /** 차량 마이크로 PCM 녹음 (백그라운드 스레드). 실패하면 null. */
    @android.annotation.SuppressLint("MissingPermission") // 동의 화면에서 마이크 허용을 받은 뒤에만 호출된다
    private fun recordPcm(ms: Long): ByteArray? = runCatching {
        val min = android.media.AudioRecord.getMinBufferSize(SAMPLE_RATE, android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT)
        val rec = android.media.AudioRecord(
            android.media.MediaRecorder.AudioSource.MIC, SAMPLE_RATE, // 11/8 실측: VOICE_RECOGNITION은 에뮬레이터에서 무음
            android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, maxOf(min, BYTES_PER_100_MS * 4),
        )
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(BYTES_PER_100_MS)
        val total = (SAMPLE_RATE * 2 * ms / 1000).toInt()
        rec.startRecording()
        try {
            while (out.size() < total) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
        }
        out.toByteArray()
    }.onFailure { main.post { SignalHub.log("마이크 녹음 실패: ${it.message}") } }.getOrNull()

    private fun afterRecording(pcm: ByteArray?, audioFile: File, onAudio: () -> Unit, my: Int) {
        endCountdown()
        if (pcm == null || pcm.isEmpty() || peak(pcm) < SILENCE_PEAK) {
            busy = false
            SignalHub.log(if (pcm == null) "음성 메모를 남기지 못했어요" else "한마디가 없어 넘어갔어요 (마이크 입력 없음)")
            return
        }
        // 1) 음성 메모부터 저장: 문장 변환 결과와 상관없이 운전자의 말은 남는다.
        runCatching { writeWav(audioFile, pcm) }
            .onSuccess { SignalHub.log("음성 메모 저장: ${audioFile.name}"); onAudio() }
            .onFailure { SignalHub.log("음성 메모 저장 실패: ${it.message}") }
        // 2) 같은 소리를 Pleos 음성 인식으로 문장 변환
        val s = stt ?: run { busy = false; return }
        waitingText = true
        val sent = runCatching {
            pcm.toList().chunked(BYTES_PER_100_MS).forEach { s.sendAudio(it.toByteArray()) }
            s.completeAudioSend()
        }
        if (sent.isFailure) {
            SignalHub.log("문장 변환 요청 실패: ${sent.exceptionOrNull()?.message} · 음성 메모는 저장돼 있어요")
            waitingText = false; busy = false; return
        }
        main.postDelayed({ if (session == my) deliverText() }, TEXT_TIMEOUT_MS)
    }

    private fun deliverText() {
        if (!waitingText) return
        waitingText = false
        busy = false
        session++
        val text = best.trim()
        if (text.isNotEmpty()) {
            SignalHub.log("한마디: \"$text\"")
            onText?.invoke(text)
        } else {
            SignalHub.log("문장으로 바꾸지 못했어요 · 음성 메모로 남겼어요")
        }
    }

    private fun peak(pcm: ByteArray): Int {
        var max = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val v = kotlin.math.abs(((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort().toInt())
            if (v > max) max = v
            i += 2
        }
        return max
    }

    /** 16kHz·16bit·mono WAV. 기존 경로(.m4a 이름)를 그대로 쓰며, 재생기는 파일 내용으로 형식을 판별한다. */
    private fun writeWav(file: File, pcm: ByteArray) {
        file.parentFile?.mkdirs()
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(pcm.size)
        }.array()
        file.outputStream().use { it.write(header); it.write(pcm) }
    }

    private fun countdown(sec: Int, my: Int) {
        if (session != my || !busy) return
        if (sec <= 0) return
        SignalHub.update { it.copy(listeningSec = sec) }
        main.postDelayed({ countdown(sec - 1, my) }, 1_000)
    }

    private fun endCountdown() {
        SignalHub.update { it.copy(listeningSec = null) }
        tone(ToneGenerator.TONE_PROP_ACK, 300) // 끝 "삐삐": 이제 그만 말해도 된다
    }

    private fun tone(type: Int, ms: Int) {
        runCatching {
            val g = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            g.startTone(type, ms)
            main.postDelayed({ runCatching { g.release() } }, ms + 200L)
        }
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        runCatching { stt?.removeListener(results) }
        runCatching { stt?.release() }
        fallback.release()
    }

    private companion object {
        const val LISTEN_MS = 8_000L
        const val TEXT_TIMEOUT_MS = 6_000L
        const val SAMPLE_RATE = 16_000
        const val BYTES_PER_100_MS = 3_200 // 16kHz·16bit·mono 100ms (Pleos 문서 기준)
        const val SILENCE_PEAK = 150
    }
}
