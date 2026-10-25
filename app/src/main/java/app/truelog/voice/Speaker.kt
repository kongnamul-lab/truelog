package app.truelog.voice

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import app.truelog.record.Trigger
import app.truelog.signals.SignalHub
import java.util.Locale

/**
 * 주행 중 안내는 화면이 아니라 음성으로 한다(Pleos는 주행 중 일반 앱 화면을 가린다).
 * 1순위: Android 표준 TextToSpeech (실차·TTS 엔진이 있는 기기)
 * 2순위: 앱에 넣은 안내 음성 파일 res/raw/tl_*.wav (에뮬레이터처럼 TTS 엔진이 없을 때)
 * Pleos TextToSpeech SDK가 공개되면 1순위를 그것으로 교체한다.
 * 문구 원칙: 행동 안내 + "기억했어요"만. 원인 진단·안전 보장 표현 금지.
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.KOREAN)
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            }
            SignalHub.log(
                when {
                    ttsReady -> "음성 안내 준비 완료 (TTS)"
                    hasClip("tl_hard_brake") -> "음성 안내 준비 완료 (녹음 파일)"
                    else -> "음성 엔진·녹음 파일이 없어 음성 안내를 못 해요"
                }
            )
        }
    }

    fun announce(trigger: Trigger) {
        val (clip, text) = when (trigger.reason) {
            "출력 제한 경고" -> "tl_power_down" to "출력 제한 경고가 들어왔어요. 계기판 안내를 확인하고, 안전한 곳에 정차하세요. 지금 상황을 기억하고 있어요."
            "비상등 켜짐" -> "tl_hazard" to "비상등이 켜졌어요. 지금 상황을 기억하고 있어요."
            "배터리 20% 미만" -> "tl_low_battery" to "배터리가 20퍼센트 아래로 내려갔어요. 충전 계획을 확인하세요. 이 순간을 기억했어요."
            "급제동" -> "tl_hard_brake" to "급제동 순간을 기억했어요."
            else -> "tl_manual_save" to "지금 상황을 저장할게요."
        }
        when {
            ttsReady -> tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "truelog-${System.currentTimeMillis()}")
            hasClip(clip) -> playClip(clip)
            else -> SignalHub.log("(음성) $text")
        }
    }

    private fun clipId(name: String): Int =
        appContext.resources.getIdentifier(name, "raw", appContext.packageName)

    private fun hasClip(name: String) = clipId(name) != 0

    private fun playClip(name: String) {
        runCatching {
            player?.release()
            player = MediaPlayer.create(appContext, clipId(name))?.apply {
                setOnCompletionListener { it.release(); if (player === it) player = null }
                start()
            }
        }.onFailure { SignalHub.log("음성 파일 재생 실패: ${it.message}") }
    }

    fun release() {
        runCatching { tts.stop(); tts.shutdown() }
        runCatching { player?.release() }
        player = null
    }
}
