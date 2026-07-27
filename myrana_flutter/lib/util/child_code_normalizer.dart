/// مطابق لـ ChildCodeNormalizer.kt
class ChildCodeNormalizer {
  ChildCodeNormalizer._();

  static String clean(String raw) {
    var s = raw.trim().toUpperCase().replaceAll(RegExp(r'[\s\-_]'), '');
    if (s.startsWith('CHILD')) s = s.substring(5);
    return s.replaceAll(RegExp(r'[^A-Z0-9]'), '');
  }

  static String normalize(String raw) {
    final c = clean(raw);
    if (c.isEmpty) return '';
    return c.startsWith('CHILD') ? c : 'CHILD$c';
  }

  /// كما يرسله تطبيق Kotlin للسيرفر (بدون بادئة CHILD في التخزين).
  static String forApi(String raw) => clean(raw);
}
