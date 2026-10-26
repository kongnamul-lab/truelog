package app.truelog.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.truelog.record.Recorder

private enum class Route { MAIN, RECORDS, DEV }

/** 화면 이동: 메인 → 기록 목록 → 기록 상세(리포트 보내기) / 개발 정보. */
@Composable
fun AppRoot(
    recorder: Recorder,
    onManualSave: () -> Unit,
    onToggleSimulation: () -> Unit,
    voiceConsent: Boolean? = false,
    voiceModeLabel: String = "",
    onVoiceConsent: (Boolean) -> Unit = {},
) {
    var route by remember { mutableStateOf(Route.MAIN) }
    var detailId by remember { mutableStateOf<String?>(null) }
    val records by recorder.records.collectAsState()

    // 첫 실행: 한마디 기능 동의 (기본 꺼짐)
    if (voiceConsent == null) {
        VoiceConsentScreen(voiceModeLabel, onChoose = onVoiceConsent)
        return
    }

    val openId = detailId
    if (openId != null) {
        val detail = remember(openId, records) { recorder.loadDetail(openId) }
        if (detail != null) {
            RecordDetailScreen(
                detail,
                recorder.similarTo(detail.summary),
                recipe = recorder.recipeFor(detail.summary),
                audioFile = recorder.audioFileFor(detail.summary.id),
                onBack = { detailId = null },
                onShared = { recorder.markShared(it) },
                onDelete = { recorder.delete(it); detailId = null },
            )
            return
        }
    }

    when (route) {
        Route.MAIN -> MainScreen(
            recorder = recorder,
            onOpenRecords = { route = Route.RECORDS },
            onOpenRecord = { detailId = it },
            onManualSave = onManualSave,
            onOpenDev = { route = Route.DEV },
        )
        Route.RECORDS -> RecordsScreen(recorder, onBack = { route = Route.MAIN }, onOpen = { detailId = it })
        Route.DEV -> TrueLogScreen(
            recorder, onManualSave, onToggleSimulation,
            onBack = { route = Route.MAIN },
            voiceLine = "한마디 기능: " + (if (voiceConsent == true) "켜짐" else "꺼짐") + " · 듣는 방식: $voiceModeLabel",
            onToggleVoice = { onVoiceConsent(voiceConsent != true) },
        )
    }
}
