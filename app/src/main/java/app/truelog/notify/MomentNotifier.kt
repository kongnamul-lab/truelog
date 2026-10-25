package app.truelog.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.truelog.MainActivity
import app.truelog.R
import app.truelog.record.Trigger
import app.truelog.record.label
import app.truelog.signals.SignalHub

/**
 * 기록 순간 시스템 알림 (v4 시안 A2).
 * Pleos는 주행 중 일반 앱 화면을 가리지만 시스템 알림은 다른 앱 위에 뜬다.
 * 알림은 읽기만 하면 되는 한 줄이며, 주행 중 터치를 요구하지 않는다(버튼 없음).
 */
class MomentNotifier(context: Context) {

    private val appContext = context.applicationContext
    private val manager = NotificationManagerCompat.from(appContext)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "이상 순간 기록", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "트루로그가 이상 순간을 기억했을 때 알려요"
            }
            appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    fun notify(trigger: Trigger) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            SignalHub.log("알림 권한이 없어 시스템 알림을 띄우지 못했어요")
            return
        }
        val open = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = when (trigger.reason) {
            "출력 제한 경고" -> "계기판 안내를 확인하고 안전한 곳에 정차하세요. 지금 상황을 기억하고 있어요."
            "급제동" -> "급제동 순간을 기억했어요. 정차 후 기록에서 볼 수 있어요."
            "배터리 20% 미만" -> "충전 계획을 확인하세요. 이 순간을 기억했어요."
            else -> "지금 상황을 기억하고 있어요."
        }
        val n = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_truelog_small)
            .setContentTitle("트루로그 · ${trigger.label()}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, n) }
            .onFailure { SignalHub.log("시스템 알림 실패: ${it.message}") }
    }

    private companion object {
        const val CHANNEL_ID = "truelog_moments"
        const val NOTIFICATION_ID = 1001
    }
}
