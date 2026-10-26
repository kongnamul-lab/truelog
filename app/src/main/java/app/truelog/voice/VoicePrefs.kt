package app.truelog.voice

import android.content.Context

/** 한마디 기능 동의 (기본 꺼짐). null = 아직 묻지 않음. */
object VoicePrefs {
    private const val FILE = "truelog_voice"
    private const val KEY = "consent"

    fun get(context: Context): Boolean? {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return if (p.contains(KEY)) p.getBoolean(KEY, false) else null
    }

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
    }
}
