package app.truelog.record

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 재현 레시피 (v0.8).
 * 같은 종류 기록이 2번 이상 쌓이면, 그 기록들이 "공통으로" 가진 조건만 뽑아 시운전 조건으로 정리한다.
 * 원인 추측은 하지 않는다. 조건의 교집합만 보여 준다.
 *
 * 규칙
 * - 대상: 같은 계기(reason) · 같은 실제/시뮬레이션 구분 · 최근 90일 · 2건 이상
 * - 속도: 10 단위로 맞춘 폭 40km/h 구간 중 가장 많은 기록을 담는 구간. 전체의 80% 이상이면 표시 (동떨어진 1~2건은 구간에서 제외)
 * - 배터리: 같은 방식, 폭 30%p
 * - 조향: ±15° 안 = 거의 직진, 밖 = 왼쪽/오른쪽. 80% 이상 같을 때만
 * - 기어·브레이크: 80% 이상 같을 때만. 급제동은 브레이크가 당연히 밟힘이라 생략
 */
data class Recipe(
    val total: Int,
    /** 시운전 조건 한 줄씩. 예: "시속 60~80km/h (3번 중 3번)" */
    val lines: List<String>,
)

object RecipeMaker {

    /** 조향각 부호: 오른쪽이 +인지. Virtual Console 실측으로 확정 필요(확인 필요). */
    const val POSITIVE_IS_RIGHT = true

    private const val WINDOW_MS = 90L * 24 * 60 * 60 * 1000
    private const val SHARE = 0.8
    private const val STRAIGHT_DEG = 15f

    fun make(target: RecordSummary, all: List<RecordSummary>, now: Long = System.currentTimeMillis()): Recipe? {
        val group = all.filter {
            it.reason == target.reason && it.simulated == target.simulated && now - it.triggeredAt <= WINDOW_MS
        }
        if (group.size < 2) return null
        val n = group.size
        val lines = mutableListOf<String>()

        val speeds = group.mapNotNull { if (it.reason == "급제동") it.peakKmh ?: it.speedAt else it.speedAt }
        if (speeds.isNotEmpty() && speeds.all { it < 1f } && speeds.size >= n * SHARE) {
            lines += "정차 중 (${n}번 중 ${speeds.size}번)"
        } else {
            band(speeds, 40f, n)?.let { (lo, hi, c) -> lines += "시속 ${lo}~${hi}km/h (${n}번 중 ${c}번)" }
        }

        val steer = group.mapNotNull { it.steerAt }.map {
            when {
                abs(it) <= STRAIGHT_DEG -> "거의 직진"
                (it > 0) == POSITIVE_IS_RIGHT -> "오른쪽으로 꺾는 중"
                else -> "왼쪽으로 꺾는 중"
            }
        }
        mode(steer, n)?.let { (v, c) -> lines += "핸들 $v (${n}번 중 ${c}번)" }

        band(group.mapNotNull { it.socAt }, 30f, n)?.let { (lo, hi, c) -> lines += "배터리 ${lo}~${hi}% (${n}번 중 ${c}번)" }

        mode(group.mapNotNull { it.gearAt }, n)?.let { (v, c) -> lines += "기어 $v (${n}번 중 ${c}번)" }

        if (target.reason != "급제동") {
            mode(group.mapNotNull { it.brakeAt?.let { b -> if (b) "밟은 상태" else "밟지 않은 상태" } }, n)
                ?.let { (v, c) -> lines += "브레이크 $v (${n}번 중 ${c}번)" }
        }
        return Recipe(n, lines)
    }

    /** 폭 width의 구간 중 가장 많은 값을 담는 구간. 전체 n의 80% 이상일 때만. (lo, hi, 개수) */
    private fun band(values: List<Float>, width: Float, n: Int): Triple<Int, Int, Int>? {
        if (values.size < 2) return null
        val maxV = values.max()
        var best: List<Float> = emptyList()
        var start = 0f
        while (start <= maxV) {
            val inside = values.filter { it >= start && it < start + width }
            if (inside.size > best.size) best = inside
            start += 10f
        }
        if (best.size < n * SHARE) return null
        val lo = (floor(best.min() / 10f) * 10f).toInt()
        var hi = (ceil(best.max() / 10f) * 10f).toInt()
        if (hi <= lo) hi = lo + 10
        return Triple(lo, hi, best.size)
    }

    /** 가장 많은 값. 전체 n의 80% 이상일 때만. */
    private fun mode(values: List<String>, n: Int): Pair<String, Int>? {
        val top = values.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        return if (top.value >= n * SHARE) top.key to top.value else null
    }
}
