package com.example.myrana.screentime

import java.util.Calendar
import java.util.Locale

object ScreenTimeSleepHelper {

    /** هل الوقت الحالي ضمن نافذة النوم؟ */
    fun isSleepTime(policy: ScreenTimePolicy, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (policy.allowDuringSleep) return false
        val cal = Calendar.getInstance(Locale.getDefault()).apply { timeInMillis = nowMs }
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = parseHm(policy.sleepStart) ?: return false
        val end = parseHm(policy.sleepEnd) ?: return false
        return if (start <= end) {
            nowMin in start until end
        } else {
            nowMin >= start || nowMin < end
        }
    }

    private fun parseHm(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
}
