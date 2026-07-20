import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/guardian_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';

/// حظر + قائمة افتراضية + جدولة تجميد — مطابق لـ ParentBlockActivity.
class ParentBlockScreen extends StatefulWidget {
  const ParentBlockScreen({super.key});

  @override
  State<ParentBlockScreen> createState() => _ParentBlockScreenState();
}

class _ParentBlockScreenState extends State<ParentBlockScreen> {
  final _pkg = TextEditingController();
  final _start = TextEditingController(text: '21:00');
  final _end = TextEditingController(text: '07:00');
  String _status = '';
  bool _error = false;
  bool _busy = false;
  List<String> _blockedHosts = [];
  List<String> _blockedPackages = [];
  bool _loadingPolicy = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadPolicy());
  }

  Future<void> _loadPolicy() async {
    final session = context.read<AppSession>();
    if (session.childCode.isEmpty) {
      setState(() => _status = 'لا يوجد طفل نشط');
      return;
    }
    setState(() {
      _loadingPolicy = true;
      _error = false;
      _status = '';
    });
    final r = await context.read<GuardianApi>().fetchBlockedPolicy(session.childCode);
    if (!mounted) return;
    setState(() {
      _loadingPolicy = false;
      if (r is ApiDevicePolicy) {
        _blockedHosts = r.blockedHosts;
        _blockedPackages = r.blockedPackages;
        _status = 'تم تحميل سياسة الحظر';
      } else if (r is ApiError) {
        _error = true;
        _status = r.message;
      }
    });
  }

  @override
  void dispose() {
    _pkg.dispose();
    _start.dispose();
    _end.dispose();
    super.dispose();
  }

  Future<void> _block() async {
    final session = context.read<AppSession>();
    if (session.childCode.isEmpty) {
      setState(() {
        _error = true;
        _status = 'لا يوجد طفل نشط';
      });
      return;
    }
    setState(() {
      _busy = true;
      _error = false;
    });
    final r = await context.read<GuardianApi>().sendCommand(
          action: 'block_app',
          value: _pkg.text.trim(),
          childCode: session.childCode,
          guardianEmail: session.parentEmail,
        );
    if (!mounted) return;
    setState(() {
      _busy = false;
      if (r is ApiOk) {
        _status = r.message;
      } else if (r is ApiError) {
        _error = true;
        _status = r.message;
      }
    });
  }

  Future<void> _unblock() async {
    final session = context.read<AppSession>();
    setState(() => _busy = true);
    final r = await context.read<GuardianApi>().sendCommand(
          action: 'unblock_app',
          value: _pkg.text.trim(),
          childCode: session.childCode,
          guardianEmail: session.parentEmail,
        );
    if (!mounted) return;
    setState(() {
      _busy = false;
      _status = r is ApiOk ? r.message : (r is ApiError ? r.message : '');
      _error = r is ApiError;
    });
  }

  Future<void> _defaults() async {
    final session = context.read<AppSession>();
    setState(() => _busy = true);
    final r = await context
        .read<GuardianApi>()
        .applyDefaultBlocklist(session.childCode);
    if (!mounted) return;
    setState(() {
      _busy = false;
      _status = r is ApiOk ? r.message : (r is ApiError ? r.message : '');
      _error = r is ApiError;
    });
  }

  Future<void> _scheduleFreeze() async {
    final session = context.read<AppSession>();
    if (session.childCode.isEmpty || _pkg.text.trim().isEmpty) {
      setState(() {
        _error = true;
        _status = 'أدخلي اسم الحزمة واختاري طفلاً نشطاً';
      });
      return;
    }
    setState(() {
      _busy = true;
      _error = false;
    });
    final r = await context.read<GuardianApi>().addSchedule(
          childCode: session.childCode,
          action: 'freeze_app',
          value: _pkg.text.trim(),
          startTime: _start.text.trim(),
          endTime: _end.text.trim(),
        );
    if (!mounted) return;
    setState(() {
      _busy = false;
      _status = r is ApiOk ? r.message : (r is ApiError ? r.message : '');
      _error = r is ApiError;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('الحظر')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          StatusBanner(message: _status, isError: _error),
          if (_loadingPolicy) const LinearProgressIndicator(),
          if (_blockedPackages.isNotEmpty || _blockedHosts.isNotEmpty) ...[
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      'تطبيقات محظورة: ${_blockedPackages.length}',
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    ..._blockedPackages.take(10).map((p) => Text('• $p')),
                    if (_blockedPackages.length > 10) const Text('… وأكثر'),
                    const Divider(height: 24),
                    Text(
                      'مواقع محظورة: ${_blockedHosts.length}',
                      style: const TextStyle(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    ..._blockedHosts.take(10).map((h) => Text('• $h')),
                    if (_blockedHosts.length > 10) const Text('… وأكثر'),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
          ],
          TextField(
            controller: _pkg,
            decoration: const InputDecoration(
              labelText: 'اسم الحزمة (package name)',
              hintText: 'com.example.game',
            ),
          ),
          const SizedBox(height: 12),
          ElevatedButton(
            onPressed: _busy ? null : _block,
            child: const Text('حظر التطبيق'),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: _busy ? null : _unblock,
            child: const Text('إلغاء الحظر'),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: _busy ? null : _defaults,
            child: const Text('تطبيق قائمة الحظر الافتراضية'),
          ),
          const Divider(height: 32),
          const Text(
            'جدولة تجميد تطبيق',
            style: TextStyle(fontWeight: FontWeight.bold),
            textAlign: TextAlign.right,
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _start,
            decoration: const InputDecoration(labelText: 'من (HH:MM)'),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _end,
            decoration: const InputDecoration(labelText: 'إلى (HH:MM)'),
          ),
          const SizedBox(height: 12),
          ElevatedButton(
            onPressed: _busy ? null : _scheduleFreeze,
            child: const Text('جدولة التجميد'),
          ),
          const SizedBox(height: 16),
          const Text(
            'ملاحظة: بعد الحظر، جهاز الطفل يسحب السياسة خلال ~60 ثانية عبر الخدمة الأمامية، '
            'أو فوراً عند استلام أمر get-command. تأكدي أن الصلاحيات مفعّلة على جهاز الطفل.',
            textAlign: TextAlign.right,
            style: TextStyle(color: Colors.black54, fontSize: 13),
          ),
        ],
      ),
    );
  }
}
