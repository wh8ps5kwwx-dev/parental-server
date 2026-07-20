/// مطابق لـ AcademyQuestionBank.kt / academy_game.py
class AcademyQuestion {
  const AcademyQuestion({
    required this.question,
    required this.options,
    required this.answer,
  });

  final String question;
  final List<String> options;
  final String answer;
}

enum ChallengeType {
  math('➕ تحدي الرياضيات'),
  science('🔬 تحدي العلوم'),
  logic('🧩 تحدي الألغاز');

  const ChallengeType(this.title);
  final String title;
}

class AcademyQuestionBank {
  AcademyQuestionBank._();

  static const math = <AcademyQuestion>[
    AcademyQuestion(question: 'كم ناتج 5 + 3؟', options: ['8', '6', '10'], answer: '8'),
    AcademyQuestion(question: 'كم ناتج 9 - 4؟', options: ['5', '3', '7'], answer: '5'),
    AcademyQuestion(question: 'كم ناتج 3 × 2؟', options: ['6', '5', '8'], answer: '6'),
    AcademyQuestion(question: 'أي عدد أكبر؟', options: ['12', '9', '6'], answer: '12'),
  ];

  static const science = <AcademyQuestion>[
    AcademyQuestion(
      question: 'ما الكوكب الذي نعيش عليه؟',
      options: ['الأرض', 'المريخ', 'زحل'],
      answer: 'الأرض',
    ),
    AcademyQuestion(
      question: 'ما العضو الذي يضخ الدم؟',
      options: ['القلب', 'العين', 'الأذن'],
      answer: 'القلب',
    ),
    AcademyQuestion(
      question: 'ما رمز الماء؟',
      options: ['H2O', 'O2', 'CO2'],
      answer: 'H2O',
    ),
    AcademyQuestion(
      question: 'النبات يحتاج إلى؟',
      options: ['ماء وضوء', 'حديد فقط', 'رمل فقط'],
      answer: 'ماء وضوء',
    ),
  ];

  static const logic = <AcademyQuestion>[
    AcademyQuestion(
      question: 'شيء نراه في الليل ولا نلمسه؟',
      options: ['القمر', 'الكتاب', 'القلم'],
      answer: 'القمر',
    ),
    AcademyQuestion(
      question: 'له أسنان ولا يعض؟',
      options: ['المشط', 'الكلب', 'السمكة'],
      answer: 'المشط',
    ),
    AcademyQuestion(
      question: 'كلما أخذت منه كبر؟',
      options: ['الحفرة', 'الكأس', 'الكتاب'],
      answer: 'الحفرة',
    ),
    AcademyQuestion(
      question: 'ما الشيء الذي يمشي بلا رجلين؟',
      options: ['الوقت', 'الكرسي', 'الحجر'],
      answer: 'الوقت',
    ),
  ];

  static List<AcademyQuestion> forType(ChallengeType type) {
    switch (type) {
      case ChallengeType.math:
        return math;
      case ChallengeType.science:
        return science;
      case ChallengeType.logic:
        return logic;
    }
  }
}

class CityBuilding {
  const CityBuilding({
    required this.emojiLabel,
    required this.name,
    required this.starCost,
  });
  final String emojiLabel;
  final String name;
  final int starCost;
}

class AcademyCityCatalog {
  AcademyCityCatalog._();

  static const buildings = <CityBuilding>[
    CityBuilding(emojiLabel: '🏠 بيت المعرفة', name: 'بيت المعرفة', starCost: 10),
    CityBuilding(emojiLabel: '🏫 مدرسة العلوم', name: 'مدرسة العلوم', starCost: 20),
    CityBuilding(emojiLabel: '🌳 حديقة الذكاء', name: 'حديقة الذكاء', starCost: 30),
    CityBuilding(emojiLabel: '🚀 مركز الفضاء', name: 'مركز الفضاء', starCost: 40),
  ];
}
