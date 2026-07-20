import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/services.dart';

/// قنوات أصلية للتنفيذ على جهاز الطفل (Android كامل، iOS stub).
/// Kotlin: ContentFilterAccessibilityService, UsageStatsCollector, EnforcementEngine
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

  /// هل خدمة إمكانية الوصول مفعّلة؟
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

  /// حظر تطبيق فورغروند — يحتاج AccessibilityService أصلي.
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

  /// سحب سياسة الحظر من السيرفر فوراً (hosts/packages/keywords).
  static Future<bool> syncPolicy() async {
    try {
      final v = await _enforcement.invokeMethod<bool>('syncPolicy');
      return v ?? false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<bool> permissionsReady() async {
    if (!isAndroid) return false;
    final usage = await hasUsageAccess();
    final a11y = await isAccessibilityEnabled();
    return usage && a11y;
  }
}
