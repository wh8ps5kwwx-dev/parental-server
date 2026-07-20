import '../../util/child_code_normalizer.dart';
import '../models/api_models.dart';
import 'api_client.dart';

/// مطابق لـ GuardianApi.kt — مسارات تطبيق ولي الأمر.
class GuardianApi {
  GuardianApi(this._client);

  final ApiClient _client;

  bool _ok(Map<String, dynamic> json) =>
      json['status']?.toString() == 'success' || json['success'] == true;

  String _msg(Map<String, dynamic> json, [String fallback = 'خطأ']) =>
      _translate((json['message']?.toString() ?? fallback));

  Future<ApiResult> sendEmailCode(String email) async {
    try {
      final json = await _client.postRoot('send-email-code', {'email': email.trim()});
      if (_ok(json)) {
        final devFallback = json['dev_fallback'] == true;
        final emailSent = json['email_sent'] == true;
        final code = (json['email_verify_code'] ??
                    (devFallback ? json['verification_code'] : null))
                ?.toString()
                .trim() ??
            '';
        final base = _msg(json, 'تم');
        final display = emailSent
            ? '$base\n\nتحققي من صندوق البريد وأدخلي الرمز.'
            : (code.isNotEmpty
                ? '$base\n\nرمز التحقق (تطوير): $code'
                : base);
        return ApiEmailCodeSent(
          message: display,
          verificationCode: code.isEmpty ? null : code,
          devFallback: devFallback,
        );
      }
      return ApiError(_msg(json));
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  Future<ApiResult> verifyEmailCode(String email, String code) async {
    return _post('verify-email-code', {
      'email': email.trim(),
      'code': code.trim(),
    });
  }

  /// التحقق من رمز جهاز الطفل — verify-child-device-code.
  Future<ApiResult> verifyChildDeviceCode(String childCode, String code) async {
    try {
      final json = await _client.postRoot('verify-child-device-code', {
        'child_code': ChildCodeNormalizer.forApi(childCode),
        'code': code.trim(),
      });
      if (_ok(json)) {
        return ApiDeviceVerified(
          message: _msg(json, 'تم التحقق'),
          childCode: ChildCodeNormalizer.normalize(
            (json['child_code'] ?? childCode).toString(),
          ),
          childEmail: (json['child_email'] ?? '').toString(),
          deviceName: (json['device_name'] ?? '').toString(),
          androidVersion: (json['android_version'] ?? '').toString(),
        );
      }
      return ApiError(_msg(json));
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  Future<ApiResult> sendLinkCode(String guardianEmail, String childCode) async {
    try {
      final json = await _client.postRoot('send-link-code', {
        'guardian_email': guardianEmail.trim(),
        'child_code': ChildCodeNormalizer.forApi(childCode),
      });
      if (_ok(json)) {
        final code = (json['link_code'] ?? json['verification_code'])
                ?.toString()
                .trim() ??
            '';
        return ApiEmailCodeSent(
          message: _msg(json, 'تم'),
          verificationCode: code.isEmpty ? null : code,
          devFallback: json['dev_fallback'] == true,
        );
      }
      final extra = (json['child_code_clean'] ?? '').toString().trim();
      final msg = _msg(json);
      return ApiError(extra.isNotEmpty ? '$msg\n(كود منظّف: $extra)' : msg);
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  /// استعادة الربط بعد إعادة تشغيل Render — restore-link.
  Future<ApiResult> restoreLink({
    required String guardianEmail,
    required String childCode,
    required String restoreToken,
    required String name,
    required int age,
    String guardianRole = 'ولي أمر',
  }) {
    return _post('restore-link', {
      'parent_email': guardianEmail.trim(),
      'guardian_email': guardianEmail.trim(),
      'child_code': ChildCodeNormalizer.forApi(childCode),
      'restore_token': restoreToken.trim(),
      'name': name.trim().isEmpty ? 'طفل' : name.trim(),
      'child_name': name.trim().isEmpty ? 'طفل' : name.trim(),
      'age': age,
      'guardian_role': guardianRole.trim(),
    }, linkPath: true);
  }

  Future<ApiResult> addChild({
    required String name,
    required int age,
    required String childCode,
    required String deviceVerifyCode,
    required String guardianEmail,
    String guardianRole = 'ولي أمر',
    String childEmail = '',
    String device = '',
    String androidVersion = '',
  }) async {
    final email = guardianEmail.trim();
    final code = ChildCodeNormalizer.forApi(childCode);
    final verify = deviceVerifyCode.trim();
    return _post('add-child', {
      'parent_email': email,
      'child_code': code,
      'verification_code': verify,
      'name': name.trim().isEmpty ? 'طفل' : name.trim(),
      'child_name': name.trim().isEmpty ? 'طفل' : name.trim(),
      'age': age,
      'child_email': childEmail.trim(),
      'device': device.trim(),
      'android_version': androidVersion.trim(),
      'device_verify_code': verify,
      'otp': verify,
      'guardian_email': email,
      'email': email,
      'guardian_role': guardianRole.trim(),
    }, linkPath: true);
  }

  /// جدولة حظر/تجميد بين ساعتين (HH:MM).
  Future<ApiResult> addSchedule({
    required String childCode,
    required String action,
    required String value,
    required String startTime,
    required String endTime,
  }) {
    return _post('add-schedule', {
      'child_code': ChildCodeNormalizer.forApi(childCode),
      'action': action.trim(),
      'value': value.trim(),
      'start_time': startTime.trim(),
      'end_time': endTime.trim(),
    });
  }

  Future<ApiResult> sendCommand({
    required String action,
    required String value,
    required String childCode,
    required String guardianEmail,
  }) {
    return _post('send-command', {
      'action': action,
      'value': value.trim(),
      'child_code': ChildCodeNormalizer.forApi(childCode),
      'guardian_email': guardianEmail.trim(),
    });
  }

  Future<ApiResult> requestUsageFromChild(String childCode, String email) {
    return sendCommand(
      action: 'request_usage',
      value: '',
      childCode: childCode,
      guardianEmail: email,
    );
  }

  Future<ApiResult> fetchInstalledApps(String childCode) async {
    try {
      final json = await _client.getRoot(
        'child-installed-apps',
        query: {'child_code': ChildCodeNormalizer.forApi(childCode)},
      );
      if (json['success'] != true) {
        return ApiError(_msg(json, 'فشل جلب التطبيقات'));
      }
      final rows = (json['apps'] as List?) ?? [];
      final items = <InstalledAppItem>[];
      for (final row in rows) {
        if (row is Map) {
          final m = Map<String, dynamic>.from(row);
          final pkg = (m['package_name'] ?? '').toString().trim();
          if (pkg.isNotEmpty) items.add(InstalledAppItem.fromJson(m));
        }
      }
      return ApiInstalledApps(items, (json['count'] as num?)?.toInt() ?? items.length);
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  Future<ApiResult> requestInstalledAppsSync(String childCode, String email) {
    return sendCommand(
      action: 'sync_installed_apps',
      value: '',
      childCode: childCode,
      guardianEmail: email,
    );
  }

  Future<ApiResult> fetchAlerts(String childCode) async {
    try {
      final raw = await _client.getRootRaw(
        'alerts',
        query: {'child_code': ChildCodeNormalizer.forApi(childCode)},
      );
      if (raw is! List) {
        if (raw is Map) {
          return ApiAlerts([], (raw['message'] ?? 'لا توجد تنبيهات').toString());
        }
        return const ApiAlerts([]);
      }
      final lines = <String>[];
      for (final row in raw) {
        if (row is Map) {
          final msg = (row['message'] ?? '').toString().trim();
          final time = (row['time'] ?? '').toString().trim();
          if (msg.isEmpty) continue;
          lines.add(time.isEmpty ? msg : '$time — $msg');
        }
      }
      return ApiAlerts(lines);
    } catch (e) {
      return ApiAlerts([], _friendlyError(e));
    }
  }

  Future<ApiResult> sendGuardianMessage({
    required String childCode,
    required String message,
    String guardianRole = 'ولي الأمر',
  }) {
    return _post('send-guardian-message', {
      'child_code': ChildCodeNormalizer.forApi(childCode),
      'guardian_role': guardianRole.trim().isEmpty ? 'ولي الأمر' : guardianRole,
      'message': message.trim(),
    });
  }

  Future<ApiResult> fetchLinkedChildren(String parentEmail) async {
    try {
      final email = parentEmail.trim();
      final json = await _client.getRoot('list-children', query: {
        'parent_email': email,
        'email': email,
      });
      if (_ok(json)) {
        final list = (json['children'] as List?) ?? [];
        final children = list
            .whereType<Map>()
            .map((e) => LinkedChild.fromJson(Map<String, dynamic>.from(e)))
            .toList();
        return ApiChildrenList(children);
      }
      return ApiError(_msg(json));
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  Future<ApiResult> fetchDailyReport(String childCode) async {
    try {
      final json = await _client.getRoot(
        'daily-report',
        query: {'child_code': ChildCodeNormalizer.forApi(childCode)},
      );
      final apps = (json['apps'] as List?) ?? [];
      if (apps.isEmpty) return const ApiReportText('لا توجد بيانات لليوم بعد.');
      final day = (json['day'] ?? '').toString();
      final lines = <String>[];
      for (var i = 0; i < apps.length; i++) {
        final row = apps[i];
        if (row is! Map) continue;
        final pkg = (row['package_name'] ?? '?').toString();
        final sec = (row['total_seconds'] as num?)?.toInt() ?? 0;
        lines.add('${i + 1}. $pkg — ${sec ~/ 60} د');
      }
      return ApiReportText('تقرير اليوم ($day):\n${lines.join('\n')}');
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  Future<ApiResult> fetchWeeklyChart(String childCode, {int days = 7}) async {
    try {
      final span = days.clamp(1, 30);
      final json = await _client.getRoot('weekly-chart', query: {
        'child_code': ChildCodeNormalizer.forApi(childCode),
        'days': '$span',
      });
      if (json['success'] != true) {
        return ApiError(_msg(json, 'فشل جلب البيانات'));
      }
      List<Map<String, dynamic>> mapList(dynamic v) =>
          ((v as List?) ?? [])
              .whereType<Map>()
              .map((e) => Map<String, dynamic>.from(e))
              .toList();
      return ApiWeeklyChart(WeeklyChartData(
        usageByDay: mapList(json['usage_by_day']),
        topApps: mapList(json['top_apps']),
        educationalApps: mapList(json['educational_apps']),
        alertsToday: (json['alerts_today'] as num?)?.toInt() ?? 0,
        alertsWeek: (json['alerts_week'] as num?)?.toInt() ?? 0,
        days: (json['days'] as num?)?.toInt() ?? span,
        avgDailyScreenSeconds:
            (json['avg_daily_screen_seconds'] as num?)?.toInt() ?? 0,
      ));
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  Future<ApiResult> fetchWeeklyUsage(String childCode, {int days = 7}) async {
    try {
      final json = await _client.getRoot('weekly-report', query: {
        'child_code': ChildCodeNormalizer.forApi(childCode),
        'days': '${days.clamp(1, 30)}',
      });
      final apps = ((json['apps'] as List?) ?? [])
          .whereType<Map>()
          .map((e) => UsageAppItem.fromJson(Map<String, dynamic>.from(e)))
          .toList();
      return ApiUsageList(apps);
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  Future<ApiResult> fetchScreenTimePolicy(String childCode) async {
    try {
      final json = await _client.getRoot(
        'screen-time-policy',
        query: {'child_code': ChildCodeNormalizer.forApi(childCode)},
      );
      final policyRaw = json['policy'] ?? json;
      final map = policyRaw is Map
          ? Map<String, dynamic>.from(policyRaw)
          : <String, dynamic>{};
      return ApiScreenTimePolicy(ScreenTimePolicy.fromJson(map));
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  Future<ApiResult> saveScreenTimePolicy(
    String childCode,
    ScreenTimePolicy policy,
  ) async {
    return _post('screen-time-policy', {
      'child_code': ChildCodeNormalizer.forApi(childCode),
      'policy': policy.toJson(),
    });
  }

  Future<ApiResult> fetchChildDashboard(String childCode) async {
    try {
      final json = await _client.getRoot(
        'child-dashboard',
        query: {'child_code': ChildCodeNormalizer.forApi(childCode)},
      );
      if (json['success'] == false) return ApiError(_msg(json));
      return ApiChildDashboard(ChildDashboardData.fromJson(json));
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  Future<ApiResult> applyDefaultBlocklist(String childCode, {bool merge = true}) async {
    try {
      final json = await _client.postRoot('apply-default-blocklist', {
        'child_code': ChildCodeNormalizer.forApi(childCode),
        'merge': merge,
      });
      if (_ok(json)) {
        final counts = json['counts'] is Map
            ? Map<String, dynamic>.from(json['counts'] as Map)
            : <String, dynamic>{};
        final pkgs = (counts['packages'] as num?)?.toInt() ?? 0;
        final sites = (counts['sites'] as num?)?.toInt() ?? 0;
        final kw = (counts['video_keywords'] as num?)?.toInt() ?? 0;
        return ApiOk('تم: $pkgs تطبيق، $sites موقع، $kw كلمة فيديو', data: json);
      }
      return ApiError(_msg(json));
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  /// سياسة الحظر الحالية (blockedHosts/blockedPackages) من السيرفر.
  Future<ApiResult> fetchBlockedPolicy(String childCode) async {
    try {
      final code = ChildCodeNormalizer.forApi(childCode);
      final json = await _client.getRoot(
        'api/v1/devices/$code/policy',
      );
      if (json['success'] == false) {
        // endpoint سياسة الحظر لا يضمن وجود success — إذا وُجد فاعتبره.
      }
      final blockedHosts = ((json['blockedHosts'] as List?) ?? [])
          .whereType<dynamic>()
          .map((e) => e.toString())
          .where((s) => s.trim().isNotEmpty)
          .toList();
      final blockedPackages = ((json['blockedPackages'] as List?) ?? [])
          .whereType<dynamic>()
          .map((e) => e.toString())
          .where((s) => s.trim().isNotEmpty)
          .toList();
      final videoKeywords = ((json['videoKeywords'] as List?) ?? [])
          .whereType<dynamic>()
          .map((e) => e.toString())
          .where((s) => s.trim().isNotEmpty)
          .toList();

      return ApiDevicePolicy(
        blockedHosts: blockedHosts,
        blockedPackages: blockedPackages,
        videoKeywords: videoKeywords,
      );
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  Future<ApiResult> fetchGuardianSettings(String parentEmail) async {
    try {
      final json = await _client.getRoot('guardian-settings', query: {
        'parent_email': parentEmail.trim(),
      });
      if (json['success'] == true) {
        final settings = json['settings'] is Map
            ? Map<String, dynamic>.from(json['settings'] as Map)
            : <String, dynamic>{};
        return ApiGuardianSettings(settings);
      }
      return ApiError(_msg(json));
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  Future<ApiResult> saveGuardianSettings(
    String parentEmail,
    Map<String, dynamic> settings,
  ) {
    return _post('guardian-settings', {
      'parent_email': parentEmail.trim(),
      'email': parentEmail.trim(),
      'settings': settings,
    });
  }

  Future<ApiResult> fetchAuditLog(String parentEmail, String? childCode) async {
    try {
      final query = <String, String>{
        'parent_email': parentEmail.trim(),
      };
      if (childCode != null && childCode.trim().isNotEmpty) {
        query['child_code'] = ChildCodeNormalizer.forApi(childCode);
      }
      final json = await _client.getRoot('audit-log', query: query);
      if (json['success'] == true) {
        final entries = ((json['entries'] as List?) ?? [])
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
        final lines = entries.map((row) {
          final time = (row['created_at'] ?? '').toString();
          final action = (row['action'] ?? '').toString();
          final detail = (row['detail'] ?? '').toString();
          final code = (row['child_code'] ?? '').toString();
          final codePart = code.isNotEmpty ? ' ($code)' : '';
          return '$time — $action$codePart\n$detail';
        }).toList();
        return ApiAuditLog(lines);
      }
      return ApiError(_msg(json));
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  Future<ApiResult> sendEmailSummary(
    String parentEmail,
    String childCode,
    String period,
  ) {
    return _post('send-email-summary', {
      'parent_email': parentEmail.trim(),
      'child_code': ChildCodeNormalizer.forApi(childCode),
      'period': period,
    });
  }

  Future<ApiResult> _post(
    String path,
    Map<String, dynamic> body, {
    bool linkPath = false,
  }) async {
    try {
      final json = await _client.postRoot(path, body);
      if (_ok(json)) {
        if (linkPath) {
          return ApiLinkSuccess(
            message: _msg(json, 'تم'),
            parentId: (json['parent_id'] as num?)?.toInt(),
            childId: (json['child_id'] as num?)?.toInt(),
            childCode: json['child_code']?.toString().trim(),
            restoreToken: json['restore_token']?.toString().trim(),
          );
        }
        return ApiOk(_msg(json, 'تم'), data: json);
      }
      return ApiError(_msg(json), errorCode: json['error_code']?.toString());
    } catch (e) {
      return ApiError(_friendlyError(e));
    }
  }

  String _translate(String message) {
    final m = message.toLowerCase();
    if (m.contains('child device not found')) {
      return 'لم يُعثر على جهاز الطفل — سجّلي من جوال الطفل أولاً واستخدمي CHILD-...';
    }
    if (m.contains('device already linked')) {
      return 'الجهاز مربوط مسبقاً — امسحي بيانات التطبيقين وأعيدي المحاولة';
    }
    if (m.contains('invalid verification code') ||
        m.contains('invalid or expired verification code') ||
        m.contains('invalid_verification_code') ||
        message.contains('كود التحقق غير صحيح')) {
      return 'رمز الربط غير صحيح — أرسلي رمز الربط من جديد واستخدمي آخر رمز من البريد';
    }
    if (m.contains('expired_code') || message.contains('منتهي الصلاحية')) {
      return 'كود التحقق منتهي الصلاحية — أرسلي رمزاً جديداً';
    }
    if (m.contains('child not found') ||
        m.contains('child_not_found') ||
        message.contains('الطفل غير موجود') ||
        message.contains('لم يُعثر على جهاز الطفل')) {
      return 'الطفل غير مسجّل على السيرفر.\n\n'
          '① جوال الطفل: «تسجيل الجهاز»\n'
          '② انسخي الكود CHILD-... للأم\n'
          '③ جوال الأم: الصقي الكود → «إتمام الربط»';
    }
    return message;
  }

  String _friendlyError(Object e) {
    final raw = e.toString();
    final jsonStart = raw.indexOf('{');
    if (jsonStart >= 0) {
      try {
        // ignore: avoid_dynamic_calls
        final slice = raw.substring(jsonStart);
        if (slice.contains('"message"')) {
          final match = RegExp(r'"message"\s*:\s*"([^"]*)"').firstMatch(slice);
          if (match != null) {
            return _translate(match.group(1)!);
          }
        }
      } catch (_) {}
    }
    final t = _translate(raw);
    return t.isEmpty ? 'خطأ في الاتصال — حاولي مرة أخرى' : t;
  }
}
