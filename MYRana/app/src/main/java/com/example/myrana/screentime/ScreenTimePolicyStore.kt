package com.example.myrana.screentime

import android.content.Context
import com.google.gson.Gson

object ScreenTimePolicyStore {

    private const val PREFS = "myrana_screen_time_policy"
    private const val KEY_JSON = "policy_json"
    private val gson = Gson()

    fun load(context: Context): ScreenTimePolicy {
        val json = prefs(context).getString(KEY_JSON, null) ?: return ScreenTimePolicy()
        return try {
            gson.fromJson(json, ScreenTimePolicy::class.java) ?: ScreenTimePolicy()
        } catch (_: Exception) {
            ScreenTimePolicy()
        }
    }

    fun save(context: Context, policy: ScreenTimePolicy) {
        prefs(context).edit().putString(KEY_JSON, gson.toJson(policy)).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
