package app.truelog.ui

import app.truelog.record.RecordSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 기록 종류 (목록 아이콘·제목용). */
enum class MomentKind { WARNING, HARD_BRAKE, LOW_BATTERY, MANUAL, HAZARD, OTHER }

/**
 * 운전자용 짧은 문장 (v0.7).
 * 제목은 4~8글자 이름, 둘째 줄은 숫자 변화. 원인 추측은 넣지 않는다.
 * 정비사용 "재현 조건" 원문은 상세 화면과 리포트에만 남긴다.
 */
object PlainText {

    fun kind(r: RecordSummary): MomentKind = when (r.reason) {
        "출력 제한 경고" -> MomentKind.WARNING
        "급제동" -> MomentKind.HARD_BRAKE
        "배터리 20% 미만" -> MomentKind.LOW_BATTERY
        "방금 상황 저장" -> MomentKind.MANUAL
        "비상등 켜짐" -> MomentKind.HAZARD
        else -> MomentKind.OTHER
    }

    fun title(r: RecordSummary): String = when (kind(r)) {
        MomentKind.WARNING -> "출력 제한 경고"
        MomentKind.HARD_BRAKE -> "급제동"
        MomentKind.LOW_BATTERY -> "배터리 20% 아래"
        MomentKind.MANUAL -> "직접 저장"
        MomentKind.HAZARD -> "비상등 켬"
        MomentKind.OTHER -> r.reason
    }

    fun sub(r: RecordSummary): String {
        val moving = (r.speedAt ?: 0f) >= 1f
        val state = if (moving) "%.0fkm/h 주행 중".format(r.speedAt) else "정차 중"
        return when (kind(r)) {
            MomentKind.HARD_BRAKE -> {
                val from = r.peakKmh
                val to = r.speedAt
                if (from != null && to != null && from - to >= 1f) "%.0f → %.0fkm/h로 줄어듦".format(from, to) else state
            }
            MomentKind.LOW_BATTERY -> listOfNotNull(r.socAt?.let { "남은 배터리 %.0f%%".format(it) }, state).joinToString(" · ")
            else -> state
        }
    }

    /** "오전 2:23" */
    fun time(t: Long): String = SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(t))

    /** 오늘 / 어제 / 10월 23일 (목) */
    fun dayLabel(t: Long, now: Long = System.currentTimeMillis()): String =
        when (dayIndex(now) - dayIndex(t)) {
            0L -> "오늘"
            1L -> "어제"
            else -> SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(Date(t))
        }

    /** 날짜 선택기용: "10월 26일 (월) · 오늘" */
    fun dayTitle(t: Long, now: Long = System.currentTimeMillis()): String {
        val base = SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(Date(t))
        return when (dayIndex(now) - dayIndex(t)) {
            0L -> "$base · 오늘"
            1L -> "$base · 어제"
            else -> base
        }
    }

    /** 날짜 묶음용 키 (기기 시간대 기준 자정). */
    fun dayIndex(t: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        // 시간대 보정: 로컬 자정을 하루 번호로
        return (c.timeInMillis + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 86_400_000L
    }
}
