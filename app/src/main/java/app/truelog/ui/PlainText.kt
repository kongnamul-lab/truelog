package app.truelog.ui

import app.truelog.record.RecordSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 운전자용 쉬운 문장 (v0.6).
 * 정비사용 "재현 조건" 원문은 상세 화면과 리포트에만 남기고, 목록에서는 이 문장을 쓴다.
 * 원인 추측은 넣지 않는다.
 */
object PlainText {

    fun title(r: RecordSummary): String {
        val base = when (r.reason) {
            "급제동" -> "브레이크를 세게 밟았어요"
            "출력 제한 경고" -> "차가 출력 제한 경고를 보냈어요"
            "배터리 20% 미만" -> "배터리가 20% 아래로 내려갔어요"
            "비상등 켜짐" -> "비상등을 켰어요"
            "방금 상황 저장" -> "직접 저장한 순간이에요"
            else -> r.reason
        }
        return if (r.simulated) "$base (시뮬레이션)" else base
    }

    fun sub(r: RecordSummary): String {
        val moving = (r.speedAt ?: 0f) >= 1f
        val parts = when (r.reason) {
            "급제동" -> listOfNotNull(
                (r.peakKmh ?: r.speedAt)?.let { "%.0fkm/h로 달리던 중".format(it) },
                r.speedAt?.let { "%.0fkm/h까지 줄었어요".format(it) },
            )
            "배터리 20% 미만" -> listOfNotNull(
                r.socAt?.let { "남은 배터리 %.0f%%".format(it) },
                if (moving) "%.0fkm/h로 달리던 중".format(r.speedAt) else "정차 중",
            )
            else -> listOf(if (moving) "%.0fkm/h로 달리던 중".format(r.speedAt) else "정차 중")
        }
        return parts.joinToString(" · ")
    }

    /** "오후 4:50" */
    fun time(t: Long): String = SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(t))

    /** 오늘 / 어제 / 10월 23일 (목) */
    fun dayLabel(t: Long, now: Long = System.currentTimeMillis()): String {
        val diff = dayIndex(now) - dayIndex(t)
        return when (diff) {
            0L -> "오늘"
            1L -> "어제"
            else -> SimpleDateFormat("M월 d일 (E)", Locale.KOREA).format(Date(t))
        }
    }

    /** 날짜 묶음용 키 (기기 시간대 기준 자정). */
    fun dayIndex(t: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis / 86_400_000L
    }
}
