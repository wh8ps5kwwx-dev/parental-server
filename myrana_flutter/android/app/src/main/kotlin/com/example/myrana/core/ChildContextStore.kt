package com.example.myrana.core

import android.content.Context
import android.content.SharedPreferences

/**
 * يخزن child_code محلياً كي تستخدمه خدمات أندرويد (Accessibility/WorkManager).
 *
 * نستخدم SharedPreferences مخصص بدل الاعتماد على Preferences الخاصة بـ Flutter
 * لتقليل أي اختلاف في أسماء الملفات.
 */
object ChildContextStore {

    private const val PREFS = "myrana_child_context"
    private const val KEY_CHILD_CODE = "child_code"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setChildCode(context: Context, childCode: String) {
        val code = ChildCodeNormalizer.forApi(childCode)
        prefs(context).edit().putString(KEY_CHILD_CODE, code).apply()
    }

    fun getChildCode(context: Context): String {
        return prefs(context).getString(KEY_CHILD_CODE, "")?.trim().orEmpty()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_CHILD_CODE).apply()
    }
}

