package com.example.myrana.academy

import android.content.Context
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

/**
 * جسر Chaquopy — تشغيل `academy_game.py` داخل APK.
 * واجهة Android (Kotlin) للعرض فقط؛ المنطق من Python.
 */
object AcademyPythonBridge {

    private val gson = Gson()
    private var started = false

    fun ensureStarted(context: Context) {
        if (started) return
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        started = true
    }

    private fun module(context: Context): PyObject {
        ensureStarted(context)
        return Python.getInstance().getModule("academy_game")
    }

    private fun storageDir(context: Context): String =
        context.applicationContext.filesDir.absolutePath

    fun loadProgressJson(context: Context): String =
        module(context).callAttr("load_progress_from_storage", storageDir(context)).toString()

    fun loadProgress(context: Context): AcademyProgress {
        val type = object : TypeToken<AcademyProgress>() {}.type
        return gson.fromJson(loadProgressJson(context), type) ?: AcademyProgress()
    }

    fun saveProgress(context: Context, progress: AcademyProgress) {
        module(context).callAttr(
            "save_progress_to_storage",
            storageDir(context),
            gson.toJson(progress),
        )
    }

    fun statsLine(context: Context): String =
        module(context).callAttr("stats_line", loadProgressJson(context)).toString()

    data class Question(val q: String, val options: List<String>, val answer: String)

    fun randomQuestion(context: Context, category: String): Question {
        val json = module(context).callAttr("random_question", category).toString()
        val obj = JSONObject(json)
        val opts = mutableListOf<String>()
        val arr = obj.getJSONArray("options")
        for (i in 0 until arr.length()) opts.add(arr.getString(i))
        return Question(
            q = obj.getString("q"),
            options = opts,
            answer = obj.getString("answer"),
        )
    }

    data class GradeResult(
        val progress: AcademyProgress,
        val message: String,
        val leveled: Boolean,
        val level: Int,
        val event: String,
        val eventValue: String,
    )

    fun gradeAnswer(
        context: Context,
        category: String,
        selected: String,
        correctAnswer: String,
    ): GradeResult {
        val raw = module(context).callAttr(
            "grade_answer",
            storageDir(context),
            loadProgressJson(context),
            category,
            selected,
            correctAnswer,
        ).toString()
        val obj = JSONObject(raw)
        val type = object : TypeToken<AcademyProgress>() {}.type
        val progress = gson.fromJson<AcademyProgress>(obj.getString("progress_json"), type)
            ?: AcademyProgress()
        return GradeResult(
            progress = progress,
            message = obj.getString("message"),
            leveled = obj.getBoolean("leveled"),
            level = obj.getInt("level"),
            event = obj.getString("event"),
            eventValue = obj.getString("event_value"),
        )
    }

    data class CityInfo(val stars: Int, val cityText: String)

    fun cityInfo(context: Context): CityInfo {
        val obj = JSONObject(
            module(context).callAttr("city_info", loadProgressJson(context)).toString(),
        )
        return CityInfo(obj.getInt("stars"), obj.getString("city_text"))
    }

    fun cityBuild(context: Context, name: String, cost: Int): Pair<String, String?> {
        val obj = JSONObject(
            module(context).callAttr(
                "city_build",
                storageDir(context),
                loadProgressJson(context),
                name,
                cost,
            ).toString(),
        )
        val msg = obj.getString("message")
        val event = if (obj.optBoolean("ok")) obj.optString("event_value") else null
        return msg to event
    }

    fun rewardsText(context: Context): String =
        module(context).callAttr("rewards_text", loadProgressJson(context)).toString()
}
