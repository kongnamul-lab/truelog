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
fun AppRoot(recorder: Recorder, onManualSave: () -> Unit, onToggleSimulation: () -> Unit) {
    var route by remember { mutableStateOf(Route.MAIN) }
    var detailId by remember { mutableStateOf<String?>(null) }
    val records by recorder.records.collectAsState()

    val openId = detailId
    if (openId != null) {
        val detail = remember(openId, records) { recorder.loadDetail(openId) }
        if (detail != null) {
            RecordDetailScreen(
                detail,
                recorder.similarTo(detail.summary),
                onBack = { detailId = null },
                onShared = { recorder.markShared(it) },
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
        Route.DEV -> TrueLogScreen(recorder, onManualSave, onToggleSimulation, onBack = { route = Route.MAIN })
    }
}
