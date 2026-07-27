import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/guardian_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';

class ParentScreenTimeScreen extends StatefulWidget {
  const ParentScreenTimeScreen({super.key});

  @override
  State<ParentScreenTimeScreen> createState() => _ParentScreenTimeScreenState();
}

class _ParentScreenTimeScreenState extends State<ParentScreenTimeScreen> {
  final _limit = TextEditingController(text: '120');
  final _bedStart = TextEditingController(text: '22:00');
  final _bedEnd = TextEditingController(text: '07:00');
  bool _enabled = true;
  String _status = '';
  bool _busy = false;
  int _todaySeconds = 0;
  int _monitoredSeconds = 0;
  int _educationalSeconds = 0;
  int _alertsToday = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  @override
  void dispose() {
    _limit.dispose();
    _bedStart.dispose();
    _bedEnd.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final code = context.read<AppSession>().childCode;
    if (code.isEmpty) {
      setState(() => _status = 'لا يوجد طفل نشط');
      return;
    }

    final dash = await context.read<GuardianApi>().fetchChildDashboard(code);
    if (!mounted) return;
    if (dash is ApiChildDashboard) {
      setState(() {
        _todaySeconds = dash.data.todaySeconds;
        _monitoredSeconds = dash.data.monitoredSeconds;
        _educationalSeconds = dash.data.educationalSeconds;
        _alertsToday = dash.data.alertsToday;
      });
    }

    final r = await context.read<GuardianApi>().fetchScreenTimePolicy(code);
    if (!mounted) return;
    if (r is ApiScreenTimePolicy) {
      setState(() {
        _limit.text = '${r.policy.dailyLimitMinutes}';
        _bedStart.text = r.policy.bedtimeStart;
        _bedEnd.text = r.policy.bedtimeEnd;
        _enabled = r.policy.enabled;
        _status = 'تم تحميل السياسة';
      });
    } else if (r is ApiError) {
      setState(() => _status = r.message);
    }
  }

  Future<void> _save() async {
    final code = context.read<AppSession>().childCode;
    setState(() => _busy = true);
    final policy = ScreenTimePolicy(
      dailyLimitMinutes: int.tryParse(_limit.text) ?? 120,
      bedtimeStart: _bedStart.text.trim(),
      bedtimeEnd: _bedEnd.text.trim(),
      enabled: _enabled,
    );
    final r = await context.read<GuardianApi>().saveScreenTimePolicy(code, policy);
    if (!mounted) return;
    setState(() {
      _busy = false;
      _status = r is ApiOk ? r.message : (r is ApiError ? r.message : '');
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('وقت الشاشة')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          StatusBanner(message: _status),
          if (_todaySeconds > 0)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text('استخدام اليوم: ${_todaySeconds ~/ 60} دقيقة'),
                    Text('المراقَب: ${_monitoredSeconds ~/ 60} دقيقة'),
                    Text('تعليمي: ${_educationalSeconds ~/ 60} دقيقة'),
                    Text('تنبيهات اليوم: $_alertsToday'),
                  ],
                ),
              ),
            ),
          SwitchListTile(
            title: const Text('تفعيل الحدود'),
            value: _enabled,
            onChanged: (v) => setState(() => _enabled = v),
          ),
          TextField(
            controller: _limit,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'الحد اليومي (دقائق)'),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _bedStart,
            decoration: const InputDecoration(labelText: 'بداية النوم HH:MM'),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _bedEnd,
            decoration: const InputDecoration(labelText: 'نهاية النوم HH:MM'),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: _busy ? null : _save,
            child: Text(_busy ? 'جاري الحفظ…' : 'حفظ السياسة'),
          ),
          const SizedBox(height: 12),
          const Text(
            'الإنفاذ المحلي (قطع الاستخدام عند تجاوز الحد) يبقى على خدمة أندرويد الأصلية.',
            textAlign: TextAlign.right,
            style: TextStyle(color: Colors.black54),
          ),
        ],
      ),
    );
  }
}
