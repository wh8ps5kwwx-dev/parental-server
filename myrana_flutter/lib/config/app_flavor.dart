import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// نكهة البناء: parent | child | (فارغ = اختيار دور قديم للتطوير).
///
/// المصدر الأساسي: `BuildConfig.FLAVOR` من Gradle عبر MethodChannel.
/// يمكن تجاوزه بـ `--dart-define=APP_FLAVOR=parent|child`.
class AppFlavor {
  AppFlavor._();

  static const String _fromDefine =
      String.fromEnvironment('APP_FLAVOR', defaultValue: '');

  static String _runtime = '';

  /// parent | child | '' 
  static String get name {
    if (_fromDefine == 'parent' || _fromDefine == 'child') return _fromDefine;
    if (_runtime == 'parent' || _runtime == 'child') return _runtime;
    return '';
  }

  static bool get isParent => name == 'parent';
  static bool get isChild => name == 'child';
  static bool get isLocked => isParent || isChild;

  static String get lockedRole => isParent ? 'parent' : isChild ? 'child' : 'none';

  static String get appTitle {
    if (isParent) return 'حماية الأطفال';
    if (isChild) return 'أكاديمية العباقرة';
    return 'MYRana Flutter';
  }

  /// يُستدعى مرة عند الإقلاع لقراءة نكهة Gradle على أندرويد.
  static Future<void> resolveFromPlatform() async {
    if (_fromDefine == 'parent' || _fromDefine == 'child') {
      _runtime = _fromDefine;
      return;
    }
    if (kIsWeb) return;
    try {
      const ch = MethodChannel('com.example.myrana/enforcement');
      final v = await ch.invokeMethod<String>('getAppFlavor');
      final s = (v ?? '').trim().toLowerCase();
      if (s == 'parent' || s == 'child') {
        _runtime = s;
      }
    } catch (_) {
      // منصات بلا قناة / اختبارات
    }
  }

  /// للاختبارات فقط.
  @visibleForTesting
  static void debugSet(String value) {
    _runtime = value;
  }
}
