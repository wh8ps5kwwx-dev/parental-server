package com.example.myrana.academy

import android.content.Context

/** واجهة التقدم — تُفوَّض إلى `academy_game.py` داخل APK. */
object AcademyProgressStore {

    fun load(context: Context): AcademyProgress =
        AcademyPythonBridge.loadProgress(context)

    fun save(context: Context, progress: AcademyProgress) {
        AcademyPythonBridge.saveProgress(context, progress)
    }

    fun statsLine(context: Context): String =
        AcademyPythonBridge.statsLine(context)

    fun statsLine(progress: AcademyProgress): String =
        "🏆 المستوى: ${progress.level}\n" +
            "⭐ النجوم: ${progress.stars}   🪙 العملات: ${progress.coins}   ⚡ XP: ${progress.xp}"
}
