sealed class ApiResult {
  const ApiResult();
}

class ApiOk extends ApiResult {
  const ApiOk(this.message, {this.data});
  final String message;
  final Map<String, dynamic>? data;
}

/// نجاح ربط الطفل — مطابق لـ GuardianApi.LinkSuccess.
class ApiLinkSuccess extends ApiResult {
  const ApiLinkSuccess({
    required this.message,
    this.parentId,
    this.childId,
    this.childCode,
    this.restoreToken,
  });
  final String message;
  final int? parentId;
  final int? childId;
  final String? childCode;
  final String? restoreToken;
}

class ApiError extends ApiResult {
  const ApiError(this.message, {this.errorCode});
  final String message;
  final String? errorCode;
}

class ApiEmailCodeSent extends ApiResult {
  const ApiEmailCodeSent({
    required this.message,
    this.verificationCode,
    this.devFallback = false,
  });
  final String message;
  final String? verificationCode;
  final bool devFallback;
}

class ApiDeviceVerified extends ApiResult {
  const ApiDeviceVerified({
    required this.message,
    required this.childCode,
    this.childEmail = '',
    this.deviceName = '',
    this.androidVersion = '',
  });
  final String message;
  final String childCode;
  final String childEmail;
  final String deviceName;
  final String androidVersion;
}

class ApiInstalledApps extends ApiResult {
  const ApiInstalledApps(this.items, this.count);
  final List<InstalledAppItem> items;
  final int count;
}

class ApiDevicePolicy extends ApiResult {
  const ApiDevicePolicy({
    required this.blockedHosts,
    required this.blockedPackages,
    this.videoKeywords = const [],
  });

  final List<String> blockedHosts;
  final List<String> blockedPackages;
  final List<String> videoKeywords;
}

class ApiAlerts extends ApiResult {
  const ApiAlerts(this.lines, [this.error]);
  final List<String> lines;
  final String? error;
}

class ApiChildrenList extends ApiResult {
  const ApiChildrenList(this.children);
  final List<LinkedChild> children;
}

class ApiReportText extends ApiResult {
  const ApiReportText(this.text);
  final String text;
}

class ApiWeeklyChart extends ApiResult {
  const ApiWeeklyChart(this.data);
  final WeeklyChartData data;
}

class ApiUsageList extends ApiResult {
  const ApiUsageList(this.apps);
  final List<UsageAppItem> apps;
}

class ApiScreenTimePolicy extends ApiResult {
  const ApiScreenTimePolicy(this.policy);
  final ScreenTimePolicy policy;
}

class ApiChildDashboard extends ApiResult {
  const ApiChildDashboard(this.data);
  final ChildDashboardData data;
}

class ApiGuardianSettings extends ApiResult {
  const ApiGuardianSettings(this.settings);
  final Map<String, dynamic> settings;
}

class ApiAuditLog extends ApiResult {
  const ApiAuditLog(this.lines);
  final List<String> lines;
}

class InstalledAppItem {
  const InstalledAppItem({
    required this.packageName,
    required this.appLabel,
    this.iconBase64,
  });
  final String packageName;
  final String appLabel;
  final String? iconBase64;

  factory InstalledAppItem.fromJson(Map<String, dynamic> row) {
    final pkg = (row['package_name'] ?? '').toString().trim();
    final label = (row['app_label'] ?? '').toString().trim();
    return InstalledAppItem(
      packageName: pkg,
      appLabel: label.isEmpty ? pkg.split('.').last : label,
      iconBase64: (row['icon_b64'] as String?)?.trim(),
    );
  }
}

class LinkedChild {
  const LinkedChild({
    required this.childCode,
    required this.name,
    this.age = 0,
    this.device = '',
    this.online = false,
  });
  final String childCode;
  final String name;
  final int age;
  final String device;
  final bool online;

  factory LinkedChild.fromJson(Map<String, dynamic> row) {
    return LinkedChild(
      childCode: (row['child_code'] ?? '').toString(),
      name: (row['name'] ?? row['child_name'] ?? 'طفل').toString(),
      age: (row['age'] as num?)?.toInt() ?? 0,
      device: (row['device'] ?? row['device_name'] ?? '').toString(),
      online: row['online'] == true,
    );
  }
}

class UsageAppItem {
  const UsageAppItem({
    required this.packageName,
    required this.totalSeconds,
    this.appLabel = '',
  });
  final String packageName;
  final int totalSeconds;
  final String appLabel;

  factory UsageAppItem.fromJson(Map<String, dynamic> row) {
    return UsageAppItem(
      packageName: (row['package_name'] ?? '').toString(),
      totalSeconds: (row['total_seconds'] as num?)?.toInt() ?? 0,
      appLabel: (row['app_label'] ?? '').toString(),
    );
  }
}

class ScreenTimePolicy {
  const ScreenTimePolicy({
    this.dailyLimitMinutes = 120,
    this.bedtimeStart = '22:00',
    this.bedtimeEnd = '07:00',
    this.enabled = true,
  });

  final int dailyLimitMinutes;
  final String bedtimeStart;
  final String bedtimeEnd;
  final bool enabled;

  factory ScreenTimePolicy.fromJson(Map<String, dynamic>? m) {
    if (m == null) return const ScreenTimePolicy();
    return ScreenTimePolicy(
      // السيرفر يستخدم block_minutes/sleep_start/sleep_end.
      dailyLimitMinutes:
          (m['block_minutes'] as num?)?.toInt() ??
          (m['daily_limit_minutes'] as num?)?.toInt() ??
          120,
      bedtimeStart:
          (m['sleep_start'] ?? m['bedtime_start'] ?? '22:00').toString(),
      bedtimeEnd:
          (m['sleep_end'] ?? m['bedtime_end'] ?? '07:00').toString(),
      enabled: m['enabled'] != false,
    );
  }

  Map<String, dynamic> toJson() => {
        'block_minutes': dailyLimitMinutes,
        'sleep_start': bedtimeStart,
        'sleep_end': bedtimeEnd,
        'enabled': enabled,
      };

  ScreenTimePolicy copyWith({
    int? dailyLimitMinutes,
    String? bedtimeStart,
    String? bedtimeEnd,
    bool? enabled,
  }) {
    return ScreenTimePolicy(
      dailyLimitMinutes: dailyLimitMinutes ?? this.dailyLimitMinutes,
      bedtimeStart: bedtimeStart ?? this.bedtimeStart,
      bedtimeEnd: bedtimeEnd ?? this.bedtimeEnd,
      enabled: enabled ?? this.enabled,
    );
  }
}

class WeeklyChartData {
  const WeeklyChartData({
    this.usageByDay = const [],
    this.topApps = const [],
    this.educationalApps = const [],
    this.alertsToday = 0,
    this.alertsWeek = 0,
    this.days = 7,
    this.avgDailyScreenSeconds = 0,
  });
  final List<Map<String, dynamic>> usageByDay;
  final List<Map<String, dynamic>> topApps;
  final List<Map<String, dynamic>> educationalApps;
  final int alertsToday;
  final int alertsWeek;
  final int days;
  final int avgDailyScreenSeconds;
}

class ChildDashboardData {
  const ChildDashboardData({
    this.childCode = '',
    this.childName = '',
    this.deviceName = '',
    this.online = false,
    this.todaySeconds = 0,
    this.educationalSeconds = 0,
    this.monitoredSeconds = 0,
    this.appsOpened = 0,
    this.permissionsOk = false,
    this.permissions = const {},
    this.lastSeenMs = 0,
    this.alertsToday = 0,
    this.alertsWeek = 0,
    this.topAppsToday = const [],
    this.policy = const ScreenTimePolicy(),
    this.batteryPct = -1,
  });

  final String childCode;
  final String childName;
  final String deviceName;
  final bool online;
  final int todaySeconds;
  final int educationalSeconds;
  final int monitoredSeconds;
  final int appsOpened;
  final bool permissionsOk;
  final Map<String, dynamic> permissions;
  final int lastSeenMs;
  final int alertsToday;
  final int alertsWeek;
  final List<Map<String, dynamic>> topAppsToday;
  final ScreenTimePolicy policy;
  final int batteryPct;

  factory ChildDashboardData.fromJson(Map<String, dynamic> json) {
    final policyMap = json['policy'];
    return ChildDashboardData(
      childCode: (json['child_code'] ?? '').toString(),
      childName: (json['child_name'] ?? '').toString(),
      deviceName: (json['device_name'] ?? '').toString(),
      online: json['online'] == true,
      todaySeconds: (json['today_seconds'] as num?)?.toInt() ?? 0,
      educationalSeconds: (json['educational_seconds'] as num?)?.toInt() ?? 0,
      monitoredSeconds: (json['monitored_seconds'] as num?)?.toInt() ?? 0,
      appsOpened: (json['apps_opened'] as num?)?.toInt() ?? 0,
      permissionsOk: json['permissions_ok'] == true,
      permissions: (json['permissions'] is Map)
          ? Map<String, dynamic>.from(json['permissions'] as Map)
          : const {},
      lastSeenMs: (json['last_seen_ms'] as num?)?.toInt() ?? 0,
      alertsToday: (json['alerts_today'] as num?)?.toInt() ?? 0,
      alertsWeek: (json['alerts_week'] as num?)?.toInt() ?? 0,
      topAppsToday: ((json['top_apps_today'] as List?) ?? [])
          .whereType<Map>()
          .map((e) => Map<String, dynamic>.from(e))
          .toList(),
      policy: ScreenTimePolicy.fromJson(
        policyMap is Map ? Map<String, dynamic>.from(policyMap) : null,
      ),
      batteryPct: (json['battery_pct'] as num?)?.toInt() ??
          (json['battery_level'] as num?)?.toInt() ??
          -1,
    );
  }
}
