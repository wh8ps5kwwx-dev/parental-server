package com.example.myrana.util

/**
 * توحيد كود الطفل — يُرسل دائماً في الحقل `child_code`.
 * السيرفر يقبل CHILD-1DF71288 و 1DF71288 ويبحث عن 1DF71288 في قاعدة البيانات.
 */
object ChildCodeNormalizer {

    private const val PREFIX = "CHILD-"

    /** الصيغة القياسية: CHILD-XXXXXXXX */
    fun normalize(raw: String?): String {
        val suffix = suffix(raw)
        if (suffix.isEmpty()) return ""
        return PREFIX + suffix
    }

    /** الجزء بدون البادئة — للبحث: 1DF71288 */
    fun suffix(raw: String?): String {
        var code = raw?.trim().orEmpty().uppercase()
        if (code.isEmpty()) return ""
        code = code.removePrefix(PREFIX)
        return code.filter { it.isLetterOrDigit() }
    }

    /** قيمة حقل `child_code` في JSON — السيرفر يبحث عن 1DF71288 */
    fun forApi(raw: String?): String {
        val cleaned = suffix(raw)
        return cleaned.ifEmpty { normalize(raw) }
    }
}
