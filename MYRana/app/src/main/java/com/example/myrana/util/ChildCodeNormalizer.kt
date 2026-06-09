package com.example.myrana.util

/**
 * توحيد كود الطفل — السيرفر يخزّنه كـ CHILD-XXXXXXXX.
 * ولي الأمر قد يلصق الجزء فقط (مثل 2776E398) من واتساب.
 */
object ChildCodeNormalizer {

    private const val PREFIX = "CHILD-"

    fun normalize(raw: String?): String {
        var code = raw?.trim().orEmpty().uppercase()
        if (code.isEmpty()) return ""
        code = code.removePrefix(PREFIX)
        code = code.filter { it.isLetterOrDigit() }
        if (code.isEmpty()) return ""
        return PREFIX + code
    }
}
