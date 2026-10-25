package app.truelog.report

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import app.truelog.record.RecordDetail
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/** 운전자가 공유 범위 팝업에서 고른 내용. 위치는 v0.4에서 수집하지 않는다. */
data class ShareScope(val includeDrivingData: Boolean)

/**
 * 휴대폰 리포트용 요약 데이터.
 * 서버에 올리지 않고 URL의 # 뒤(브라우저가 서버로 보내지 않는 부분)에 압축해 담아 QR로 전달한다.
 * 형식: viewerUrl#d=<base64url(raw deflate(JSON))>
 * 표시 기한(24시간)은 보기 페이지가 지키는 "표시 제한"이며, 데이터 자체를 지우는 기능은 아니다.
 */
object ReportPayload {

    private const val VERSION = 2 // v2: 2초 간격 31점, 해시 앞 16자리 (QR 칸 수 줄이기)
    private const val SHOW_LIMIT_MS = 24L * 60 * 60 * 1000

    fun build(detail: RecordDetail, scope: ShareScope, now: Long = System.currentTimeMillis()): String {
        val r = detail.summary
        val t0 = r.triggeredAt
        val o = JSONObject()
            .put("v", VERSION)
            .put("id", r.id)
            .put("r", r.reason)
            .put("sim", r.simulated)
            .put("t0", t0)
            .put("c", r.conditions)
            .put("ok", r.unchanged)
            .put("h", r.hash.take(16))
            .put("exp", now + SHOW_LIMIT_MS)

        if (scope.includeDrivingData) {
            // 2초 간격 31개 점(-30초 ~ +30초)으로 줄여 담는다.
            val speed = JSONArray()
            val brake = JSONArray()
            val steer = JSONArray()
            for (sec in -30..30 step 2) {
                val from = t0 + sec * 1000L - 1000
                val to = t0 + sec * 1000L + 1000
                val bucket = detail.samples.filter { it.t in from until to }
                val sp = bucket.mapNotNull { it.speedKmh }.average().takeIf { !it.isNaN() }
                val st = bucket.mapNotNull { it.steeringDeg }.average().takeIf { !it.isNaN() }
                speed.put(sp?.let { Math.round(it).toInt() } ?: JSONObject.NULL)
                brake.put(if (bucket.any { it.brake == true }) 1 else 0)
                steer.put(st?.let { Math.round(it).toInt() } ?: JSONObject.NULL)
            }
            o.put("s", speed).put("b", brake).put("a", steer)
        }
        return BuildConfigUrl.viewer + "#d=" + encode(o.toString())
    }

    private fun encode(json: String): String {
        val bytes = ByteArrayOutputStream()
        DeflaterOutputStream(bytes, Deflater(Deflater.BEST_COMPRESSION, true)).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return Base64.encodeToString(bytes.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun qrBitmap(text: String, sizePx: Int = 720): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix.get(i % matrix.width, i / matrix.width)) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    }
}

/** BuildConfig 의존을 한곳에 모은다. */
private object BuildConfigUrl {
    // 빌드 설정 캐시에 영향받지 않도록 코드에 직접 둔다 (GitHub Pages, 익명 계정)
    const val viewer: String = "https://kongnamul-lab.github.io/truelog/report.html"
}
