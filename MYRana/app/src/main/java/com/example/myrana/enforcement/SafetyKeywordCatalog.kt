package com.example.myrana.enforcement

/**
 * كلمات مراقبة — تنبيه ولي الأمر عند ظهورها في **أي تطبيق**.
 * الفئات عامة للحماية الرقمية (مشروع التخرج).
 */
object SafetyKeywordCatalog {

    data class Entry(val category: String, val keyword: String)

    private val categories: Map<String, List<String>> = mapOf(
        "إيذاء النفس" to listOf(
            "انتحار", "إيذاء النفس", "جرح النفس", "قتل النفس", "أفكار انتحارية",
            "إيذاء الجسد", "التخلص من الحياة", "أذى النفس", "اكتئاب شديد",
            "رغبة بالموت", "الانعزال الشديد", "suicide", "self harm", "kill myself",
        ),
        "التنمر" to listOf(
            "تنمر", "مضايقة", "سخرية", "إهانة", "إذلال", "تحقير", "تهديد",
            "ترهيب", "ابتزاز", "تحرش لفظي", "خطاب كراهية", "شتائم", "bullying", "harassment",
        ),
        "العنف" to listOf(
            "قتل", "اعتداء", "ضرب", "طعن", "إطلاق نار", "تعذيب", "اختطاف",
            "مجزرة", "عنف", "عصابة", "سرقة", "سطو", "تخريب", "جريمة", "weapon", "murder",
        ),
        "المخدرات" to listOf(
            "مخدرات", "تعاطي", "إدمان", "حشيش", "كوكايين", "هيروين", "أفيون",
            "مواد مخدرة", "تجارة المخدرات", "تهريب مخدرات", "drugs", "cocaine", "heroin", "weed",
        ),
        "المقامرة" to listOf(
            "قمار", "مراهنة", "مراهنات", "كازينو", "رهان", "يانصيب", "ألعاب حظ",
            "مقامرة إلكترونية", "gambling", "casino", "betting",
        ),
        "الكحول والتبغ" to listOf(
            "كحول", "خمور", "مشروبات كحولية", "تدخين", "سجائر", "تبغ", "نيكوتين",
            "فيب", "alcohol", "vape", "cigarette", "smoking",
        ),
        "الاحتيال الإلكتروني" to listOf(
            "احتيال", "نصب", "خداع", "سرقة هوية", "تصيد", "اختراق", "تهكير",
            "برمجيات خبيثة", "فيروس", "ابتزاز إلكتروني", "phishing", "hacking", "malware", "ransomware",
        ),
        "التطرف" to listOf(
            "إرهاب", "تطرف", "تجنيد", "تفجير", "عبوة ناسفة", "جماعة متطرفة",
            "عنف سياسي", "terrorism", "extremism",
        ),
        "الخصوصية الرقمية" to listOf(
            "دردشة مجهولة", "غرباء", "مشاركة الموقع", "مشاركة كلمة المرور",
            "معلومات شخصية", "بيانات شخصية", "رقم الهاتف", "عنوان المنزل",
            "stranger chat", "anonymous chat", "share password",
        ),
        "دارك ويب" to listOf(
            "دارك ويب", "داركويب", "ديب ويب", "ويب مظلم", "dark web", "deep web",
            "tor browser", "onion", ".onion", "hidden service", "anonymous browsing",
        ),
        "الجرائم الإلكترونية" to listOf(
            "هاكر", "هاكرز", "hacker", "keylogger", "botnet", "exploit",
            "تسريب بيانات", "بيانات مسروقة", "بطاقات مسروقة", "stolen cards", "leaked data",
            "سوق مخفي", "black market", "illicit market",
        ),
    )

    fun entries(): List<Entry> = categories.flatMap { (category, words) ->
        words.map { Entry(category, it) }
    }

    /** تطبيع للمطابقة: حروف صغيرة، بدون تشكيل، بعض بدائل الكتابة. */
    fun normalize(text: String): String {
        var t = text.lowercase().trim()
        t = t.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        t = t.replace('0', 'o').replace('1', 'i').replace('3', 'e')
            .replace('4', 'a').replace('5', 's').replace('@', 'a')
        return t
    }

    fun matchIn(text: String): Entry? {
        val blob = normalize(text)
        if (blob.length < 2) return null
        for (entry in entries()) {
            val needle = normalize(entry.keyword)
            if (needle.length < 3) continue
            if (blob.contains(needle)) return entry
        }
        return null
    }
}
