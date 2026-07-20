import '../../util/child_code_normalizer.dart';
import '../models/api_models.dart';
import 'api_client.dart';

/// مسارات جهاز الطفل — مطابق لـ NetworkModule.kt.
class ChildApi {
  ChildApi(this._client);

  final ApiClient _client;

  bool _ok(Map<String, dynamic> json) =>
      json['status']?.toString() == 'success' || json['success'] == true;

  Future<ApiResult> registerDevice({
    required String childCode,
    required String deviceName,
    String childEmail = '',
    String androidVersion = '',
    String? deviceVerifyCode,
  }) async {
    try {
      final body = <String, dynamic>{
        'child_code': ChildCodeNormalizer.forApi(childCode),
        'device_name': deviceName,
        'device': deviceName,
        'child_email': childEmail,
        'android_version': androidVersion,
      };
      if (deviceVerifyCode != null && deviceVerifyCode.trim().length == 6) {
        body['device_verify_code'] = deviceVerifyCode.trim();
      }
      final json = await _client.postRoot('register-child-device', body);
      if (_ok(json)) {
        return ApiOk(
          (json['message'] ?? 'تم تسجيل الجهاز').toString(),
          data: json,
        );
      }
      return ApiError((json['message'] ?? 'فشل التسجيل').toString());
    } catch (e) {
      return ApiError(e.toString());
    }
  }

  Future<ApiResult> linkStatus(String childCode) async {
    try {
      final json = await _client.getRoot(
        'child-link-status',
        query: {'child_code': ChildCodeNormalizer.forApi(childCode)},
      );
      if (_ok(json)) {
        return ApiOk(
          (json['message'] ?? 'حالة الربط').toString(),
          data: json,
        );
      }
      return ApiError((json['message'] ?? 'خطأ').toString());
    } catch (e) {
      return ApiError(e.toString());
    }
  }

  /// نبضة حياة للجهاز — `/child-heartbeat`.
  Future<ApiResult> heartbeat({
    required String childCode,
    int batteryPct = -1,
    bool permissionsOk = false,
  }) async {
    try {
      final json = await _client.postRoot('child-heartbeat', {
        'child_code': ChildCodeNormalizer.forApi(childCode),
        'battery_pct': batteryPct,
        'battery_level': batteryPct,
        // السيرفر يتوقع خريطة permissions ويحدد mandatory_ok منها.
        'permissions': {
          'mandatory_ok': permissionsOk,
        },
        'platform': 'flutter',
      });
      if (_ok(json)) {
        return ApiOk((json['message'] ?? 'heartbeat').toString(), data: json);
      }
      return ApiError((json['message'] ?? 'فشل النبضة').toString());
    } catch (e) {
      return ApiError(e.toString());
    }
  }

  /// جلب أوامر ولي الأمر — `/get-command`.
  Future<ApiResult> pollCommand(String childCode) async {
    try {
      final json = await _client.getRoot(
        'get-command',
        query: {'child_code': ChildCodeNormalizer.forApi(childCode)},
      );
      return ApiOk((json['message'] ?? 'أمر').toString(), data: json);
    } catch (e) {
      return ApiError(e.toString());
    }
  }

  /// رفع استخدام التطبيقات — يحتاج UsageStats أصلي لجمع البيانات.
  Future<ApiResult> uploadUsage({
    required String childCode,
    required Map<String, int> secondsByPackage,
  }) async {
    try {
      final json = await _client.postRoot('upload-usage', {
        'child_code': ChildCodeNormalizer.forApi(childCode),
        'usage': secondsByPackage.map((k, v) => MapEntry(k, v)),
        'seconds_by_package': secondsByPackage,
      });
      if (_ok(json)) {
        return ApiOk((json['message'] ?? 'تم رفع الاستخدام').toString(), data: json);
      }
      return ApiError((json['message'] ?? 'فشل رفع الاستخدام').toString());
    } catch (e) {
      return ApiError(e.toString());
    }
  }

  /// مزامنة قائمة التطبيقات المثبتة — يحتاج PackageManager أصلي.
  Future<ApiResult> syncInstalledApps({
    required String childCode,
    required List<Map<String, String?>> apps,
  }) async {
    try {
      final json = await _client.postRoot('sync-child-apps', {
        'child_code': ChildCodeNormalizer.forApi(childCode),
        'apps': apps,
      });
      if (_ok(json)) {
        return ApiOk((json['message'] ?? 'تمت المزامنة').toString(), data: json);
      }
      return ApiError((json['message'] ?? 'فشل مزامنة التطبيقات').toString());
    } catch (e) {
      return ApiError(e.toString());
    }
  }

  Future<ApiResult> postAlert({
    required String childCode,
    required String message,
  }) async {
    try {
      final json = await _client.postRoot('alerts', {
        'child_code': ChildCodeNormalizer.forApi(childCode),
        'message': message.trim(),
      });
      if (_ok(json)) {
        return ApiOk((json['message'] ?? 'تم').toString(), data: json);
      }
      return ApiError((json['message'] ?? 'فشل إرسال التنبيه').toString());
    } catch (e) {
      return ApiError(e.toString());
    }
  }
}
