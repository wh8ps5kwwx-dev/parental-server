import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/child_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';
import 'child_home_screen.dart';

/// انتظار اكتمال الربط من ولي الأمر — child-link-status.
class ChildWaitingScreen extends StatefulWidget {
  const ChildWaitingScreen({super.key});

  @override
  State<ChildWaitingScreen> createState() => _ChildWaitingScreenState();
}

class _ChildWaitingScreenState extends State<ChildWaitingScreen> {
  Timer? _timer;
  String _status = 'بانتظار ربط ولي الأمر…';
  bool _linked = false;

  @override
  void initState() {
    super.initState();
    _check();
    _timer = Timer.periodic(const Duration(seconds: 5), (_) => _check());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _check() async {
    final session = context.read<AppSession>();
    if (session.childCode.isEmpty) return;
    final r = await context.read<ChildApi>().linkStatus(session.childCode);
    if (!mounted) return;
    if (r is ApiOk) {
      final linked = r.data?['linked'] == true;
      setState(() {
        _linked = linked;
        _status = linked
            ? 'تم الربط بنجاح'
            : 'الكود: ${session.childCode} — بانتظار ولي الأمر';
      });
      if (linked) {
        await session.setChildLinked(true);
        _timer?.cancel();
      }
    } else if (r is ApiError) {
      setState(() => _status = r.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = context.watch<AppSession>();
    return Scaffold(
      appBar: AppBar(title: const Text('انتظار الربط')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            StatusBanner(message: _status, isError: !_linked && _status.contains('خطأ')),
            const SizedBox(height: 24),
            Text(
              session.childCode,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                    letterSpacing: 2,
                  ),
            ),
            if (session.deviceVerifyCode.isNotEmpty) ...[
              const SizedBox(height: 8),
              Text(
                'رمز الجهاز (تطوير): ${session.deviceVerifyCode}',
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.black54),
              ),
            ],
            const Spacer(),
            if (_linked)
              ElevatedButton(
                onPressed: () {
                  Navigator.of(context).pushReplacement(
                    MaterialPageRoute(builder: (_) => const ChildHomeScreen()),
                  );
                },
                child: const Text('متابعة'),
              )
            else
              OutlinedButton(
                onPressed: _check,
                child: const Text('تحقق الآن'),
              ),
          ],
        ),
      ),
    );
  }
}
