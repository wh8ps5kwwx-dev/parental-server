import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart';

/// قنوات أصلية للتنفيذ على جهاز الطفل.
/// Android: كامل (Accessibility + UsageStats + Foreground).
/// iOS: FamilyControls + ManagedSettings (Screen Time) عند التفويض؛ ولي الأمر عبر السيرفر.
const _enforcement = MethodChannel('com.example.myrana/enforcement');
const _usageStats = MethodChannel('com.example.myrana/usage_stats');
const _accessibility = MethodChannel('com.example.myrana/accessibility');

class EnforcementChannel {
  EnforcementChannel._();

  static Future<void> setChildContext({
    required String childCode,
  }) async {
    try {
      await _enforcement.invokeMethod('setChildContext', {
        'childCode': childCode,
      });
    } on MissingPluginException {
      // ignore
    }
  }

  static bool get isNativeMobile =>
      !kIsWeb && (Platform.isAndroid || Platform.isIOS);

  static bool get isAndroid => !kIsWeb && Platform.isAndroid;

  static bool get isIOS => !kIsWeb && Platform.isIOS;

  /// هل خدمة إمكانية الوصول مفعّلة؟ (على iOS = FamilyControls معتمد)
  static Future<bool> isAccessibilityEnabled() async {
    try {
      final v = await _accessibility.invokeMethod<bool>('isEnabled');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<void> openAccessibilitySettings() async {
    try {
      await _accessibility.invokeMethod('openSettings');
    } on MissingPluginException {
      return;
    }
  }

  static Future<bool> hasUsageAccess() async {
    try {
      final v = await _usageStats.invokeMethod<bool>('hasPermission');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<void> openUsageAccessSettings() async {
    try {
      await _usageStats.invokeMethod('openSettings');
    } on MissingPluginException {
      return;
    }
  }

  static Future<bool> addBlockedPackage(String packageName) async {
    try {
      final v = await _enforcement.invokeMethod<bool>('blockPackage', {
        'package': packageName,
      });
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<bool> removeBlockedPackage(String packageName) async {
    try {
      final v = await _enforcement.invokeMethod<bool>('unblockPackage', {
        'package': packageName,
      });
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<bool> clearBlockedPackages() async {
    try {
      final v = await _enforcement.invokeMethod<bool>('clearBlocked');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<bool> addBlockedHost(String host) async {
    try {
      final v = await _enforcement.invokeMethod<bool>('blockHost', {
        'host': host,
      });
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<bool> removeBlockedHost(String host) async {
    try {
      final v = await _enforcement.invokeMethod<bool>('unblockHost', {
        'host': host,
      });
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<List<String>> getBlockedPackages() async {
    try {
      final v = await _enforcement.invokeMethod<List>('getBlockedPackages');
      if (v == null) return [];
      return v.map((e) => e.toString()).toList();
    } on MissingPluginException {
      return [];
    }
  }

  /// حظر تطبيق فورغروند — Android: Accessibility؛ iOS: ManagedSettings بعد الاختيار.
  static Future<bool> blockPackage(String packageName) =>
      addBlockedPackage(packageName);

  static Future<bool> startForegroundMonitor() async {
    try {
      final v = await _enforcement.invokeMethod<bool>('startForeground');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<bool> stopForegroundMonitor() async {
    try {
      final v = await _enforcement.invokeMethod<bool>('stopForeground');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<Map<String, int>> queryUsageToday() async {
    try {
      final v = await _usageStats.invokeMethod<Map>('queryToday');
      if (v == null) return {};
      return v.map((k, val) => MapEntry(k.toString(), (val as num).toInt()));
    } on MissingPluginException {
      return {};
    }
  }

  static Future<List<Map<String, String?>>> getInstalledApps() async {
    try {
      final v = await _enforcement.invokeMethod<List>('getInstalledApps');
      if (v == null) return [];
      return v
          .whereType<Map>()
          .map((row) => row.map((k, val) => MapEntry(k.toString(), val?.toString())))
          .toList();
    } on MissingPluginException {
      return [];
    }
  }

  static Future<bool> enforceNow() async {
    try {
      final v = await _enforcement.invokeMethod<bool>('enforceNow');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  /// سحب سياسة الحظر من السيرفر فوراً (hosts/packages) وتطبيق الدرع على iOS إن وُجد.
  static Future<bool> syncPolicy() async {
    try {
      final v = await _enforcement.invokeMethod<bool>('syncPolicy');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<int> getBatteryPct() async {
    try {
      final v = await _enforcement.invokeMethod<int>('getBatteryPct');
      return v ?? -1;
    } on MissingPluginException {
      return -1;
    }
  }

  static Future<bool> isIgnoringBatteryOptimizations() async {
    try {
      final v =
          await _enforcement.invokeMethod<bool>('isIgnoringBatteryOptimizations');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<bool> openBatteryOptimizationSettings() async {
    try {
      final v =
          await _enforcement.invokeMethod<bool>('openBatteryOptimizationSettings');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  /// فتح إعدادات التطبيق (iOS) أو المسار المناسب.
  static Future<bool> openAppSettings() async {
    try {
      final v = await _enforcement.invokeMethod<bool>('openAppSettings');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  /// على iOS: يفتح إعدادات التطبيق (وقت الشاشة يُدار من إعدادات النظام).
  static Future<bool> openScreenTimeSettings() async {
    try {
      final v = await _enforcement.invokeMethod<bool>('openScreenTimeSettings');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  /// هل مُنحت صلاحية الكاميرا لتطبيق الطفل؟
  static Future<bool> hasCameraPermission() async {
    if (!isAndroid) return false;
    try {
      final v = await _enforcement.invokeMethod<bool>('hasCameraPermission');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  /// هل مُنحت صلاحية الميكروفون لتطبيق الطفل؟
  static Future<bool> hasMicrophonePermission() async {
    if (!isAndroid) return false;
    try {
      final v = await _enforcement.invokeMethod<bool>('hasMicrophonePermission');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  /// طلب صلاحية الكاميرا من النظام (حوار أندرويد).
  static Future<bool> requestCameraPermission() async {
    if (!isAndroid) return false;
    try {
      final v = await _enforcement.invokeMethod<bool>('requestCameraPermission');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  /// طلب صلاحية الميكروفون من النظام (حوار أندرويد).
  static Future<bool> requestMicrophonePermission() async {
    if (!isAndroid) return false;
    try {
      final v =
          await _enforcement.invokeMethod<bool>('requestMicrophonePermission');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  /// حالة المنصة من الطبقة الأصلية.
  static Future<Map<String, dynamic>> getPlatformStatus() async {
    if (!isNativeMobile) {
      return {
        'platform': 'unsupported',
        'enforcement_available': false,
        'parent_via_server': true,
      };
    }
    try {
      final v = await _enforcement.invokeMethod<Map>('getPlatformStatus');
      if (v == null) return {'platform': isIOS ? 'ios' : 'android'};
      return Map<String, dynamic>.from(v);
    } on MissingPluginException {
      return _fallbackPlatformStatus();
    } on PlatformException {
      return _fallbackPlatformStatus();
    }
  }

  static Map<String, dynamic> _fallbackPlatformStatus() {
    if (isAndroid) {
      return {
        'platform': 'android',
        'enforcement_available': true,
        'parent_via_server': true,
        'recommended_model': 'parent_any_child_android',
      };
    }
    return {
      'platform': 'ios',
      'enforcement_available': false,
      'parent_via_server': true,
      'recommended_model': 'parent_ios_child_android',
      'reason_ar':
          'حظر النظام على جهاز iPhone يحتاج FamilyControls من آبل.',
    };
  }

  static Future<Map<String, dynamic>> requestFamilyControlsAuthorization() async {
    try {
      final v =
          await _enforcement.invokeMethod<Map>('requestFamilyControlsAuthorization');
      if (v == null) return {'ok': false, 'status': 'unavailable'};
      return Map<String, dynamic>.from(v);
    } on MissingPluginException {
      return {'ok': false, 'status': 'unavailable'};
    } on PlatformException {
      return {'ok': false, 'status': 'unavailable'};
    }
  }

  /// منتقي تطبيقات وقت الشاشة (ApplicationToken) — مطلوب قبل الدرع على iOS.
  static Future<bool> presentFamilyActivityPicker() async {
    try {
      final v =
          await _enforcement.invokeMethod<bool>('presentFamilyActivityPicker');
      return v ?? false;
    } on MissingPluginException {
      return false;
    } on PlatformException {
      return false;
    }
  }

  static Future<bool> isFamilyControlsAvailable() async {
    try {
      final v = await _enforcement.invokeMethod<bool>('isFamilyControlsAvailable');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<Map<String, bool>> permissionSnapshot() async {
    if (isIOS) {
      final status = await getPlatformStatus();
      final authorized = status['family_controls_authorized'] == true;
      final apps = (status['family_activity_apps'] is int)
          ? status['family_activity_apps'] as int
          : 0;
      final cats = (status['family_activity_categories'] is int)
          ? status['family_activity_categories'] as int
          : 0;
      final webs = (status['family_activity_web_domains'] is int)
          ? status['family_activity_web_domains'] as int
          : 0;
      final hasTokens = apps > 0 || cats > 0 || webs > 0;
      return {
        'usage': authorized,
        'accessibility': authorized,
        'battery': true,
        'camera': false,
        'microphone': false,
        'mandatory_ok': authorized && hasTokens,
        'ios_ui_ok': true,
        'family_controls': authorized,
      };
    }
    if (!isAndroid) {
      return {
        'usage': false,
        'accessibility': false,
        'battery': false,
        'camera': false,
        'microphone': false,
        'mandatory_ok': false,
      };
    }
    final usage = await hasUsageAccess();
    final a11y = await isAccessibilityEnabled();
    final battery = await isIgnoringBatteryOptimizations();
    final camera = await hasCameraPermission();
    final mic = await hasMicrophonePermission();
    return {
      'usage': usage,
      'accessibility': a11y,
      'battery': battery,
      'camera': camera,
      'microphone': mic,
      'mandatory_ok': usage && a11y,
    };
  }

  static Future<bool> permissionsReady() async {
    if (isIOS) {
      final snap = await permissionSnapshot();
      return snap['mandatory_ok'] == true;
    }
    if (!isAndroid) return false;
    final usage = await hasUsageAccess();
    final a11y = await isAccessibilityEnabled();
    return usage && a11y;
  }
}
