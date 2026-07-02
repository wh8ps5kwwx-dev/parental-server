package com.example.myrana.academy

/** بنك الأسئلة — مطابق لملف Python academy_game.py */
data class AcademyQuestion(
    val question: String,
    val options: List<String>,
    val answer: String,
)

enum class ChallengeType(val title: String) {
    MATH("➕ تحدي الرياضيات"),
    SCIENCE("🔬 تحدي العلوم"),
    LOGIC("🧩 تحدي الألغاز"),
}

object AcademyQuestionBank {

    val math = listOf(
        AcademyQuestion("كم ناتج 5 + 3؟", listOf("8", "6", "10"), "8"),
        AcademyQuestion("كم ناتج 9 - 4؟", listOf("5", "3", "7"), "5"),
        AcademyQuestion("كم ناتج 3 × 2؟", listOf("6", "5", "8"), "6"),
        AcademyQuestion("أي عدد أكبر؟", listOf("12", "9", "6"), "12"),
    )

    val science = listOf(
        AcademyQuestion("ما الكوكب الذي نعيش عليه؟", listOf("الأرض", "المريخ", "زحل"), "الأرض"),
        AcademyQuestion("ما العضو الذي يضخ الدم؟", listOf("القلب", "العين", "الأذن"), "القلب"),
        AcademyQuestion("ما رمز الماء؟", listOf("H2O", "O2", "CO2"), "H2O"),
        AcademyQuestion("النبات يحتاج إلى؟", listOf("ماء وضوء", "حديد فقط", "رمل فقط"), "ماء وضوء"),
    )

    val logic = listOf(
        AcademyQuestion("شيء نراه في الليل ولا نلمسه؟", listOf("القمر", "الكتاب", "القلم"), "القمر"),
        AcademyQuestion("له أسنان ولا يعض؟", listOf("المشط", "الكلب", "السمكة"), "المشط"),
        AcademyQuestion("كلما أخذت منه كبر؟", listOf("الحفرة", "الكأس", "الكتاب"), "الحفرة"),
        AcademyQuestion("ما الشيء الذي يمشي بلا رجلين؟", listOf("الوقت", "الكرسي", "الحجر"), "الوقت"),
    )

    fun forType(type: ChallengeType): List<AcademyQuestion> = when (type) {
        ChallengeType.MATH -> math
        ChallengeType.SCIENCE -> science
        ChallengeType.LOGIC -> logic
    }
}

data class CityBuilding(val emojiLabel: String, val name: String, val starCost: Int)

object AcademyCityCatalog {
    val buildings = listOf(
        CityBuilding("🏠 بيت المعرفة", "بيت المعرفة", 10),
        CityBuilding("🏫 مدرسة العلوم", "مدرسة العلوم", 20),
        CityBuilding("🌳 حديقة الذكاء", "حديقة الذكاء", 30),
        CityBuilding("🚀 مركز الفضاء", "مركز الفضاء", 40),
    )
}
