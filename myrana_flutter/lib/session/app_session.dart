import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// جلسات ولي الأمر / الطفل — بديل SharedPreferences في Kotlin.
class AppSession extends ChangeNotifier {
  static const _kRole = 'role'; // parent | child | none
  static const _kEmail = 'parent_email';
  static const _kEmailVerified = 'parent_email_verified';
  static const _kChildCode = 'child_code';
  static const _kChildName = 'child_name';
  static const _kDeviceVerify = 'device_verify_code';
  static const _kLinked = 'child_linked';
  static const _kGuardianRole = 'guardian_role';
  static const _kRestoreToken = 'restore_token';

  SharedPreferences? _prefs;

  String role = 'none';
  String parentEmail = '';
  bool parentEmailVerified = false;
  String childCode = '';
  String childName = '';
  String deviceVerifyCode = '';
  bool childLinked = false;
  String guardianRole = 'ولي أمر';
  String restoreToken = '';

  Future<void> load() async {
    _prefs = await SharedPreferences.getInstance();
    role = _prefs!.getString(_kRole) ?? 'none';
    parentEmail = _prefs!.getString(_kEmail) ?? '';
    parentEmailVerified = _prefs!.getBool(_kEmailVerified) ?? false;
    childCode = _prefs!.getString(_kChildCode) ?? '';
    childName = _prefs!.getString(_kChildName) ?? '';
    deviceVerifyCode = _prefs!.getString(_kDeviceVerify) ?? '';
    childLinked = _prefs!.getBool(_kLinked) ?? false;
    guardianRole = _prefs!.getString(_kGuardianRole) ?? 'ولي أمر';
    restoreToken = _prefs!.getString(_kRestoreToken) ?? '';
    notifyListeners();
  }

  Future<void> setRole(String value) async {
    role = value;
    await _prefs?.setString(_kRole, value);
    notifyListeners();
  }

  Future<void> setParentSession({
    required String email,
    required bool verified,
  }) async {
    parentEmail = email.trim();
    parentEmailVerified = verified;
    await _prefs?.setString(_kEmail, parentEmail);
    await _prefs?.setBool(_kEmailVerified, verified);
    notifyListeners();
  }

  Future<void> setActiveChild({
    required String code,
    String name = '',
  }) async {
    childCode = code;
    childName = name;
    await _prefs?.setString(_kChildCode, code);
    await _prefs?.setString(_kChildName, name);
    notifyListeners();
  }

  Future<void> setChildRegistration({
    required String code,
    required String verifyCode,
    bool linked = false,
  }) async {
    childCode = code;
    deviceVerifyCode = verifyCode;
    childLinked = linked;
    await _prefs?.setString(_kChildCode, code);
    await _prefs?.setString(_kDeviceVerify, verifyCode);
    await _prefs?.setBool(_kLinked, linked);
    notifyListeners();
  }

  Future<void> setChildLinked(bool linked) async {
    childLinked = linked;
    await _prefs?.setBool(_kLinked, linked);
    notifyListeners();
  }

  Future<void> setGuardianRole(String value) async {
    guardianRole = value;
    await _prefs?.setString(_kGuardianRole, value);
    notifyListeners();
  }

  Future<void> setRestoreToken(String token) async {
    restoreToken = token.trim();
    await _prefs?.setString(_kRestoreToken, restoreToken);
    notifyListeners();
  }

  Future<void> logoutParent() async {
    parentEmail = '';
    parentEmailVerified = false;
    childCode = '';
    childName = '';
    restoreToken = '';
    role = 'none';
    await _prefs?.remove(_kEmail);
    await _prefs?.remove(_kEmailVerified);
    await _prefs?.remove(_kChildCode);
    await _prefs?.remove(_kChildName);
    await _prefs?.remove(_kRestoreToken);
    await _prefs?.setString(_kRole, 'none');
    notifyListeners();
  }

  Future<void> clearChild() async {
    childCode = '';
    deviceVerifyCode = '';
    childLinked = false;
    role = 'none';
    await _prefs?.remove(_kChildCode);
    await _prefs?.remove(_kDeviceVerify);
    await _prefs?.remove(_kLinked);
    await _prefs?.setString(_kRole, 'none');
    notifyListeners();
  }
}
