import 'package:flutter/material.dart';

import '../../native/enforcement_channel.dart';
import '../../widgets/common_widgets.dart';

/// صلاحيات جهاز الطفل — Android: تفعيل كامل؛ iOS: FamilyControls + منتقي التطبيقات.
class ChildPermissionsScreen extends StatefulWidget {
  const ChildPermissionsScreen({super.key});

  @override
  State<ChildPermissionsScreen> createState() => _ChildPermissionsScreenState();
}

class _ChildPermissionsScreenState extends State<ChildPermissionsScreen>
    with WidgetsBindingObserver {
  bool _usage = false;
  bool _a11y = false;
  bool _battery = false;
  bool _camera = false;
  bool _microphone = false;
  bool _monitor = false;
  String _note = '';
  Map<String, dynamic> _iosStatus = {};
  bool _fcBusy = false;

  Future<void> _refresh() async {
    if (EnforcementChannel.isIOS) {
      final status = await EnforcementChannel.getPlatformStatus();
      final battery = await EnforcementChannel.getBatteryPct();
      final fcAvailable = await EnforcementChannel.isFamilyControlsAvailable();
      if (!mounted) return;
      final authorized = status['family_controls_authorized'] == true;
      final fcStatus = (status['family_controls_status'] ?? 'unknown').toString();
      final apps = status['family_activity_apps'];
      final cats = status['family_activity_categories'];
      final webs = status['family_activity_web_domains'];
      final appCount = apps is int ? apps : 0;
      final catCount = cats is int ? cats : 0;
      final webCount = webs is int ? webs : 0;
      final tokenCount = appCount + catCount + webCount;
      setState(() {
        _iosStatus = {
          ...status,
          'family_controls_compile_enabled': fcAvailable,
        };
        _battery = battery >= 0;
        if (!fcAvailable) {
          _note =
              'إطار FamilyControls غير متاح في هذا البناء — ابنِي من Mac مع Xcode 15+.';
        } else if (authorized && tokenCount > 0) {
          _note =
              'FamilyControls ممنوح ✓ والتطبيقات/الفئات مختارة ($tokenCount). '
              'زامني السياسة لتطبيق درع ManagedSettings عند وجود حظر من ولي الأمر.';
        } else if (authorized) {
          _note =
              'FamilyControls ممنوح ✓ — اختاري التطبيقات عبر المنتقي ثم زامني السياسة.';
        } else if (fcStatus == 'denied') {
          _note =
              'FamilyControls مرفوض ✗ — من إعدادات النظام → Screen Time يمكن إعادة السماح، '
              'أو اضغطي طلب الإذن مجدداً.';
        } else {
          _note =
              'اطلبي إذن FamilyControls (وقت الشاشة). بدون التفويض لا يعمل الحظر النظامي على iPhone.';
        }
      });
      return;
    }
    if (!EnforcementChannel.isAndroid) {
      setState(() {
        _note = 'المنصة الحالية لا تدعم الإنفاذ الأصلي.';
      });
      return;
    }
    final usage = await EnforcementChannel.hasUsageAccess();
    final a11y = await EnforcementChannel.isAccessibilityEnabled();
    final battery = await EnforcementChannel.isIgnoringBatteryOptimizations();
    final camera = await EnforcementChannel.hasCameraPermission();
    final mic = await EnforcementChannel.hasMicrophonePermission();
    setState(() {
      _usage = usage;
      _a11y = a11y;
      _battery = battery;
      _camera = camera;
      _microphone = mic;
      if (!_usage || !_a11y) {
        _note = 'فعّلي الصلاحيات أدناه ثم ارجعي للتطبيق واضغطي «تحديث الحالة».';
      } else if (_monitor) {
        _note = 'الصلاحيات جاهزة والمراقبة نشطة.';
      } else {
        _note = 'الصلاحيات جاهزة — اضغطي «تشغيل المراقبة».';
      }
    });
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refresh();
    }
  }

  String _fcStatusLabel() {
    final s = (_iosStatus['family_controls_status'] ?? '—').toString();
    switch (s) {
      case 'approved':
        return 'ممنوح ✓';
      case 'denied':
        return 'مرفوض ✗';
      case 'not_determined':
        return 'لم يُطلب بعد';
      default:
        return s;
    }
  }

  Color? _fcStatusColor() {
    final s = (_iosStatus['family_controls_status'] ?? '').toString();
    if (s == 'approved') return Colors.green;
    if (s == 'denied') return Colors.red;
    return null;
  }

  Widget _iosBody() {
    final authorized = _iosStatus['family_controls_authorized'] == true;
    final batteryPct = _iosStatus['battery_pct'];
    final batteryLabel = batteryPct is int && batteryPct >= 0
        ? '$batteryPct%'
        : '—';
    final appCount = _iosStatus['family_activity_apps'] is int
        ? _iosStatus['family_activity_apps'] as int
        : 0;
    final catCount = _iosStatus['family_activity_categories'] is int
        ? _iosStatus['family_activity_categories'] as int
        : 0;
    final webCount = _iosStatus['family_activity_web_domains'] is int
        ? _iosStatus['family_activity_web_domains'] as int
        : 0;
    final tokenCount = appCount + catCount + webCount;
    final enforcement = _iosStatus['enforcement_available'] == true;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Text(
          'ما يعمل على iPhone (جهاز طفل)',
          style: TextStyle(fontWeight: FontWeight.bold),
          textAlign: TextAlign.right,
        ),
        const SizedBox(height: 8),
        const Text(
          '• تسجيل الجهاز وربطه بولي الأمر\n'
          '• النبضة والتقارير عبر REST\n'
          '• الأكاديمية ورسائل ولي الأمر\n'
          '• قراءة نسبة البطارية\n'
          '• بعد إذن FamilyControls: درع ManagedSettings للتطبيقات المختارة',
          textAlign: TextAlign.right,
        ),
        const SizedBox(height: 16),
        Card(
          color: authorized
              ? Colors.green.withOpacity(0.08)
              : Colors.orange.withOpacity(0.08),
          child: ListTile(
            title: const Text(
              'إذن FamilyControls (وقت الشاشة)',
              textAlign: TextAlign.right,
            ),
            subtitle: Text(
              'الحالة: ${_fcStatusLabel()}\n'
              'اضغطي لطلب التفويض من النظام (Face ID / رمز المرور).',
              textAlign: TextAlign.right,
            ),
            trailing: _fcBusy
                ? const SizedBox(
                    width: 28,
                    height: 28,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(
                    authorized ? Icons.verified_user : Icons.shield_outlined,
                    color: _fcStatusColor(),
                  ),
            onTap: _fcBusy
                ? null
                : () async {
                    setState(() => _fcBusy = true);
                    final r = await EnforcementChannel
                        .requestFamilyControlsAuthorization();
                    if (!mounted) return;
                    setState(() => _fcBusy = false);
                    await _refresh();
                    if (!mounted) return;
                    final msg = (r['message_ar'] ?? r['message'] ?? r['status'])
                        .toString();
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(msg)),
                    );
                  },
          ),
        ),
        Card(
          child: ListTile(
            enabled: authorized,
            title: const Text(
              'اختيار التطبيقات للدرع',
              textAlign: TextAlign.right,
            ),
            subtitle: Text(
              authorized
                  ? (tokenCount > 0
                      ? 'مختار حالياً: $appCount تطبيق، $catCount فئة، $webCount نطاق — اضغطي لتعديل القائمة'
                      : 'ضروري: منتقي آبل يختار ApplicationToken (أسماء حزم أندرويد لا تكفي)')
                  : 'فعّلي FamilyControls أولاً',
              textAlign: TextAlign.right,
            ),
            trailing: Icon(
              tokenCount > 0 ? Icons.check_circle : Icons.apps,
              color: tokenCount > 0 ? Colors.green : null,
            ),
            onTap: !authorized
                ? null
                : () async {
                    final ok =
                        await EnforcementChannel.presentFamilyActivityPicker();
                    if (!mounted) return;
                    await _refresh();
                    if (!mounted) return;
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text(
                          ok
                              ? 'تم حفظ اختيار التطبيقات'
                              : 'لم يُحفظ الاختيار أو أُلغي',
                        ),
                      ),
                    );
                  },
          ),
        ),
        Card(
          child: ListTile(
            enabled: authorized,
            title: const Text('تشغيل المراقبة / الدرع', textAlign: TextAlign.right),
            subtitle: Text(
              enforcement
                  ? 'جاهز ✓ — اضغطي لبدء المراقبة ومزامنة السياسة'
                  : 'يحتاج إذناً + تطبيقات مختارة',
              textAlign: TextAlign.right,
            ),
            trailing: Icon(
              enforcement ? Icons.play_circle : Icons.play_disabled,
              color: enforcement ? Colors.green : null,
            ),
            onTap: !authorized
                ? null
                : () async {
                    final ok =
                        await EnforcementChannel.startForegroundMonitor();
                    await EnforcementChannel.syncPolicy();
                    if (!mounted) return;
                    await _refresh();
                    if (!mounted) return;
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text(
                          ok
                              ? 'المراقبة نشطة — تم تطبيق السياسة إن وُجدت'
                              : 'تعذّر البدء — تحققي من الإذن واختيار التطبيقات',
                        ),
                      ),
                    );
                  },
          ),
        ),
          Card(
            child: ListTile(
              enabled: authorized,
              title: const Text('مزامنة السياسة الآن', textAlign: TextAlign.right),
              subtitle: const Text(
                'تسحب الحظر من السيرفر وتطبّق/تلغي درع ManagedSettings للتطبيقات المختارة',
                textAlign: TextAlign.right,
              ),
              trailing: const Icon(Icons.sync),
              onTap: !authorized
                  ? null
                  : () async {
                      setState(() => _fcBusy = true);
                      final ok = await EnforcementChannel.syncPolicy();
                      if (!mounted) return;
                      setState(() => _fcBusy = false);
                      await _refresh();
                      if (!mounted) return;
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            ok
                                ? 'تمت مزامنة السياسة وتطبيق الدرع إن وُجدت تطبيقات مختارة'
                                : 'فشلت المزامنة أو لا توجد تطبيقات مختارة للدرع',
                          ),
                        ),
                      );
                    },
            ),
          ),
          Card(
            child: ListTile(
              title: const Text('وقت الشاشة / إعدادات التطبيق', textAlign: TextAlign.right),
              subtitle: const Text(
                'إدارة يدوية من إعدادات النظام إن رغبتِ',
                textAlign: TextAlign.right,
              ),
              trailing: const Icon(Icons.settings),
              onTap: () async {
                await EnforcementChannel.openScreenTimeSettings();
              },
            ),
          ),
        Card(
          child: ListTile(
            title: const Text('البطارية', textAlign: TextAlign.right),
            subtitle: Text(
              'المقروءة حالياً: $batteryLabel',
              textAlign: TextAlign.right,
            ),
            trailing: Icon(
              _battery ? Icons.battery_full : Icons.battery_unknown,
              color: _battery ? Colors.green : null,
            ),
          ),
        ),
        const SizedBox(height: 12),
        const Text(
          'ملاحظة صادقة: أسماء حزم أندرويد من السيرفر لا تُحوَّل تلقائياً إلى تطبيقات iOS. '
          'السياسة تُفعّل/تلغي درع التطبيقات التي اخترتهاِ من منتقي آبل. '
          'امتداد DeviceActivityMonitor (حدود الوقت اليومية) يحتاج إضافته من Xcode على Mac — انظر README.',
          textAlign: TextAlign.right,
          style: TextStyle(color: Colors.black54, fontSize: 12),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('الصلاحيات')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          StatusBanner(message: _note),
          if (EnforcementChannel.isAndroid) ...[
            const Text(
              'للرقابة الفعلية يحتاج تطبيق الطفل صلاحيات أندرويد. '
              'اضغطي على كل صف لفتح إعدادات النظام أو طلب الإذن. '
              'صلاحية الكاميرا والميكروفون اختيارية وتُبلَّغ لولي الأمر كحالة جاهزية — '
              'وليست تصويراً أو تنصتًا صامتاً.',
              textAlign: TextAlign.right,
            ),
            const SizedBox(height: 12),
            Card(
              child: ListTile(
                onTap: () async {
                  await EnforcementChannel.openUsageAccessSettings();
                },
                title: const Text('Usage Access', textAlign: TextAlign.right),
                subtitle: Text(
                  _usage
                      ? 'مفعّل ✓ — يُستخدم لمعرفة التطبيق في المقدمة ووقت الاستخدام'
                      : 'غير مفعّل — اضغطي لفتح الإعدادات وفعّلي MYRana Flutter',
                  textAlign: TextAlign.right,
                ),
                trailing: Icon(
                  _usage ? Icons.check_circle : Icons.settings,
                  color: _usage ? Colors.green : null,
                ),
              ),
            ),
            Card(
              child: ListTile(
                onTap: () async {
                  await EnforcementChannel.openAccessibilitySettings();
                },
                title: const Text(
                  'Accessibility Service',
                  textAlign: TextAlign.right,
                ),
                subtitle: Text(
                  _a11y
                      ? 'مفعّل ✓ — يُستخدم لحظر التطبيقات وفلترة المواقع/يوتيوب'
                      : 'غير مفعّل — اضغطي وفعّلي خدمة MYRana Flutter',
                  textAlign: TextAlign.right,
                ),
                trailing: Icon(
                  _a11y ? Icons.check_circle : Icons.settings,
                  color: _a11y ? Colors.green : null,
                ),
              ),
            ),
            Card(
              child: ListTile(
                onTap: () async {
                  await EnforcementChannel.openBatteryOptimizationSettings();
                },
                title: const Text(
                  'تجاهل تحسين البطارية',
                  textAlign: TextAlign.right,
                ),
                subtitle: Text(
                  _battery
                      ? 'مفعّل ✓ — المراقبة تستمر في الخلفية'
                      : 'موصى به — اضغطي للسماح بتشغيل التطبيق دون تقييد البطارية',
                  textAlign: TextAlign.right,
                ),
                trailing: Icon(
                  _battery ? Icons.check_circle : Icons.battery_saver,
                  color: _battery ? Colors.green : null,
                ),
              ),
            ),
            Card(
              child: ListTile(
                onTap: () async {
                  await EnforcementChannel.requestCameraPermission();
                  if (!mounted) return;
                  await _refresh();
                },
                title: const Text(
                  'صلاحية الكاميرا',
                  textAlign: TextAlign.right,
                ),
                subtitle: Text(
                  _camera
                      ? 'ممنوحة ✓ — جاهزية التطبيق (ليست تصويراً صامتاً في الخلفية)'
                      : 'اختيارية — اضغطي لطلب الإذن من النظام؛ تُبلَّغ الحالة لولي الأمر',
                  textAlign: TextAlign.right,
                ),
                trailing: Icon(
                  _camera ? Icons.check_circle : Icons.photo_camera_outlined,
                  color: _camera ? Colors.green : null,
                ),
              ),
            ),
            Card(
              child: ListTile(
                onTap: () async {
                  await EnforcementChannel.requestMicrophonePermission();
                  if (!mounted) return;
                  await _refresh();
                },
                title: const Text(
                  'صلاحية الميكروفون',
                  textAlign: TextAlign.right,
                ),
                subtitle: Text(
                  _microphone
                      ? 'ممنوحة ✓ — جاهزية التطبيق (ليست تنصتًا صامتاً في الخلفية)'
                      : 'اختيارية — اضغطي لطلب الإذن من النظام؛ تُبلَّغ الحالة لولي الأمر',
                  textAlign: TextAlign.right,
                ),
                trailing: Icon(
                  _microphone ? Icons.check_circle : Icons.mic_none,
                  color: _microphone ? Colors.green : null,
                ),
              ),
            ),
            Card(
              child: ListTile(
                onTap: () async {
                  final ok = await EnforcementChannel.startForegroundMonitor();
                  if (!mounted) return;
                  setState(() => _monitor = ok);
                  await _refresh();
                },
                title: const Text(
                  'Foreground Service',
                  textAlign: TextAlign.right,
                ),
                subtitle: Text(
                  _monitor
                      ? 'نشطة ✓ — مزامنة السياسة والحظر كل ثوانٍ'
                      : 'غير نشطة — اضغطي لتشغيل المراقبة في الخلفية',
                  textAlign: TextAlign.right,
                ),
                trailing: Icon(
                  _monitor ? Icons.check_circle : Icons.play_arrow,
                  color: _monitor ? Colors.green : null,
                ),
              ),
            ),
            const SizedBox(height: 8),
            ElevatedButton.icon(
              onPressed: () async {
                final ok = await EnforcementChannel.startForegroundMonitor();
                if (!mounted) return;
                setState(() => _monitor = ok);
                await _refresh();
              },
              icon: const Icon(Icons.shield),
              label: const Text('تشغيل المراقبة'),
            ),
          ] else if (EnforcementChannel.isIOS) ...[
            _iosBody(),
          ] else ...[
            const Text(
              'هذه المنصة لا تدعم الإنفاذ الأصلي لجهاز الطفل.',
              textAlign: TextAlign.right,
            ),
          ],
          const SizedBox(height: 12),
          OutlinedButton(
            onPressed: _refresh,
            child: const Text('تحديث الحالة'),
          ),
        ],
      ),
    );
  }
}
