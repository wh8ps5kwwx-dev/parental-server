package com.example.myrana.util

/**
 * توحيد كود الطفل — يُرسل دائماً في الحقل `child_code`.
 * السيرفر يقبل CHILD-1DF71288 و 1DF71288 ويبحث عن 1DF71288 في قاعدة البيانات.
 */
object ChildCodeNormalizer {

    private const val PREFIX = "CHILD-"
    private val CHILD_CODE_PATTERN =
        Regex("""CHILD[-\s]?([A-Z0-9]{6,12})""", RegexOption.IGNORE_CASE)

    /** الصيغة القياسية: CHILD-XXXXXXXX */
    fun normalize(raw: String?): String {
        val suffix = suffix(raw)
        if (suffix.isEmpty()) return ""
        return PREFIX + suffix
    }

    /** استخراج CHILD-XXXXXXXX من نص Gmail كامل أو لصق جزئي */
    fun extractFromPaste(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return ""
        if (text.contains("@")) {
            CHILD_CODE_PATTERN.find(text)?.let { match ->
                return normalize("CHILD-${match.groupValues[1]}")
            }
            return ""
        }
        val normalized = normalize(text)
        return if (isValid(normalized)) normalized else ""
    }

    /** يبدو أن المستخدم لصق البريد بدل كود CHILD (مثل parentcontrolapp@gmail.com). */
    fun looksLikeEmailMistake(raw: String?): Boolean {
        val text = raw?.trim().orEmpty()
        if (text.contains("@")) return true
        val s = suffix(text)
        if (s.length > 12) return true
        if (s.contains("GMAIL", ignoreCase = true)) return true
        if (s.contains("PARENTCONTROL", ignoreCase = true)) return true
        if (s.startsWith("PARENT") && s.length > 10) return true
        return false
    }

    /** كود صالح: CHILD- ثم 6–12 حرف/رقم — ليس بريداً */
    fun isValid(raw: String?): Boolean {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return false
        if (looksLikeEmailMistake(text)) return false
        val s = suffix(text)
        if (s.length !in 6..12) return false
        return s.all { it.isLetterOrDigit() }
    }

    /** رسالة عربية عند لصق بريد بدل الكود */
    fun pasteErrorHint(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (looksLikeEmailMistake(text)) {
            return "هذا يشبه البريد — انسخي CHILD-88278A25 من Gmail (رسالة «كود جهاز الطفل») وليس البريد"
        }
        if (text.contains("@")) {
            return "هذا بريد إلكتروني — انسخي كود CHILD-XXXXXXXX من Gmail (رسالة «كود جهاز الطفل»)"
        }
        val s = suffix(text)
        if (s.length > 12) {
            return "الكود طويل جداً — الصيغة الصحيحة: CHILD-88278A25 (8 أحرف بعد CHILD-)"
        }
        if (s.isNotEmpty() && !isValid(normalize(text))) {
            return "كود غير صالح — انسخي CHILD-... من Gmail وليس البريد"
        }
        return null
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
