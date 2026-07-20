import 'package:flutter/material.dart';

import '../../native/enforcement_channel.dart';
import '../../widgets/common_widgets.dart';

/// شرح الصلاحيات الأصلية — Android كامل، iOS محدود.
class ChildPermissionsScreen extends StatefulWidget {
  const ChildPermissionsScreen({super.key});

  @override
  State<ChildPermissionsScreen> createState() => _ChildPermissionsScreenState();
}

class _ChildPermissionsScreenState extends State<ChildPermissionsScreen>
    with WidgetsBindingObserver {
  bool _usage = false;
  bool _a11y = false;
  bool _monitor = false;
  String _note = '';

  Future<void> _refresh() async {
    if (!EnforcementChannel.isAndroid) {
      setState(() {
        _note =
            'على iOS: الرقابة الصلبة غير متاحة مثل أندرويد. '
            'استخدمي تطبيق ولي الأمر لإدارة السياسات عبر السيرفر فقط.';
      });
      return;
    }
    final usage = await EnforcementChannel.hasUsageAccess();
    final a11y = await EnforcementChannel.isAccessibilityEnabled();
    setState(() {
      _usage = usage;
      _a11y = a11y;
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
              'اضغطي على كل صف لفتح إعدادات النظام.',
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
          ] else ...[
            const Text(
              'iOS لا يوفّر Usage Stats أو Accessibility لحظر التطبيقات من طرف ثالث '
              'بنفس طريقة أندرويد. واجهة ولي الأمر والتقارير تعمل عبر REST.',
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
