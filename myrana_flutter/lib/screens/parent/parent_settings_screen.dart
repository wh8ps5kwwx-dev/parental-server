import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../config/server_config.dart';
import '../../data/api/guardian_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';

/// إعدادات ولي الأمر — مطابق لـ ParentSettingsActivity.
class ParentSettingsScreen extends StatefulWidget {
  const ParentSettingsScreen({super.key});

  @override
  State<ParentSettingsScreen> createState() => _ParentSettingsScreenState();
}

class _ParentSettingsScreenState extends State<ParentSettingsScreen> {
  final _role = TextEditingController();
  final _retention = TextEditingController(text: '30');
  bool _emailDaily = false;
  bool _emailWeekly = false;
  bool _alertSound = true;
  List<String> _audit = [];
  String _status = '';
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _role.text = context.read<AppSession>().guardianRole;
      _load();
    });
  }

  @override
  void dispose() {
    _role.dispose();
    _retention.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    final email = context.read<AppSession>().parentEmail;
    final r = await context.read<GuardianApi>().fetchGuardianSettings(email);
    if (!mounted) return;
    if (r is ApiGuardianSettings) {
      setState(() {
        _retention.text =
            ((r.settings['retention_days'] as num?)?.toInt() ?? 30).toString();
        _emailDaily = r.settings['email_daily_enabled'] == true;
        _emailWeekly = r.settings['email_weekly_enabled'] == true;
        _alertSound = r.settings['alert_sound_enabled'] != false;
        final role = (r.settings['guardian_role'] ?? '').toString();
        if (role.isNotEmpty) _role.text = role;
        _status = 'تم تحميل الإعدادات';
      });
    } else if (r is ApiError) {
      setState(() => _status = r.message);
    }
  }

  Future<void> _save() async {
    final session = context.read<AppSession>();
    final guardianApi = context.read<GuardianApi>();
    setState(() => _busy = true);
    await session.setGuardianRole(_role.text.trim());
    final retention = int.tryParse(_retention.text)?.clamp(7, 90) ?? 30;
    final payload = <String, dynamic>{
      'guardian_role': _role.text.trim(),
      'retention_days': retention,
      'email_daily_enabled': _emailDaily,
      'email_weekly_enabled': _emailWeekly,
      'alert_sound_enabled': _alertSound,
    };
    final r = await guardianApi.saveGuardianSettings(
      session.parentEmail,
      payload,
    );
    if (!mounted) return;
    setState(() {
      _busy = false;
      _status = r is ApiOk ? r.message : (r is ApiError ? r.message : '');
    });
  }

  Future<void> _loadAudit() async {
    final session = context.read<AppSession>();
    setState(() => _busy = true);
    final r = await context.read<GuardianApi>().fetchAuditLog(
          session.parentEmail,
          session.childCode.isEmpty ? null : session.childCode,
        );
    if (!mounted) return;
    setState(() {
      _busy = false;
      if (r is ApiAuditLog) {
        _audit = r.lines;
        _status = _audit.isEmpty ? 'سجل التغييرات فارغ' : 'تم تحميل السجل';
      } else if (r is ApiError) {
        _status = r.message;
      }
    });
  }

  Future<void> _sendSummary(String period) async {
    final session = context.read<AppSession>();
    if (session.childCode.isEmpty) {
      setState(() => _status = 'لا يوجد طفل نشط لإرسال الملخص');
      return;
    }
    setState(() => _busy = true);
    final r = await context.read<GuardianApi>().sendEmailSummary(
          session.parentEmail,
          session.childCode,
          period,
        );
    if (!mounted) return;
    setState(() {
      _busy = false;
      _status = r is ApiOk ? r.message : (r is ApiError ? r.message : '');
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('الإعدادات')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          StatusBanner(message: _status),
          TextField(
            controller: _role,
            decoration: const InputDecoration(labelText: 'دور ولي الأمر'),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _retention,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(
              labelText: 'أيام الاحتفاظ بالبيانات (7–90)',
            ),
          ),
          SwitchListTile(
            title: const Text('بريد يومي'),
            value: _emailDaily,
            onChanged: (v) => setState(() => _emailDaily = v),
          ),
          SwitchListTile(
            title: const Text('بريد أسبوعي'),
            value: _emailWeekly,
            onChanged: (v) => setState(() => _emailWeekly = v),
          ),
          SwitchListTile(
            title: const Text('صوت التنبيهات'),
            value: _alertSound,
            onChanged: (v) => setState(() => _alertSound = v),
          ),
          ElevatedButton(
            onPressed: _busy ? null : _save,
            child: const Text('حفظ الإعدادات'),
          ),
          const Divider(height: 32),
          const Text('ملخص بالبريد', style: TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: _busy ? null : () => _sendSummary('daily'),
                  child: const Text('يومي'),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: OutlinedButton(
                  onPressed: _busy ? null : () => _sendSummary('weekly'),
                  child: const Text('أسبوعي'),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          ElevatedButton(
            onPressed: _busy ? null : _loadAudit,
            child: const Text('تحميل سجل التغييرات'),
          ),
          if (_audit.isNotEmpty) ...[
            const SizedBox(height: 8),
            ..._audit.map(
              (l) => Card(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text(l, textAlign: TextAlign.right),
                ),
              ),
            ),
          ],
          const Divider(height: 32),
          const Text(
            'إعدادات الخادم (من BuildConfig)',
            style: TextStyle(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Text('ROOT: ${ServerConfig.rootUrl}'),
          Text('API: ${ServerConfig.baseApiUrl}'),
          Text('X-API-KEY: ${ServerConfig.apiKey}'),
        ],
      ),
    );
  }
}
