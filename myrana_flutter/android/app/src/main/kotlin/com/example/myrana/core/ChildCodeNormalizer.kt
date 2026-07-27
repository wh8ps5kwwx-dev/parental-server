package com.example.myrana.core

/**
 * مطابق لـ ChildCodeNormalizer في Flutter:
 * - تنظيف: إزالة CHILD- / الفراغات / _ / -
 * - تحويل إلى uppercase
 * - إزالة أي محارف غير [A-Z0-9]
 * - إرجاع without prefix (كما يتوقع السيرفر عبر db_child_code)
 */
object ChildCodeNormalizer {

    fun clean(raw: String): String {
        var s = raw.trim().uppercase().replace(Regex("[\\s\\-_]"), "")
        if (s.startsWith("CHILD")) s = s.substring(5)
        return s.replace(Regex("[^A-Z0-9]"), "")
    }

    /** كما يرسلها السيرفر/Flutter للتخزين (بدون بادئة CHILD). */
    fun forApi(raw: String): String = clean(raw)
}
