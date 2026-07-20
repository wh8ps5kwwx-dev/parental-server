import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/guardian_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';

class ParentMessageScreen extends StatefulWidget {
  const ParentMessageScreen({super.key});

  @override
  State<ParentMessageScreen> createState() => _ParentMessageScreenState();
}

class _ParentMessageScreenState extends State<ParentMessageScreen> {
  final _msg = TextEditingController();
  String _status = '';
  bool _error = false;
  bool _busy = false;

  @override
  void dispose() {
    _msg.dispose();
    super.dispose();
  }

  Future<void> _send() async {
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
    final r = await context.read<GuardianApi>().sendGuardianMessage(
          childCode: session.childCode,
          message: _msg.text,
          guardianRole: session.guardianRole,
        );
    if (!mounted) return;
    setState(() {
      _busy = false;
      if (r is ApiOk) {
        _status = r.message;
        _msg.clear();
      } else if (r is ApiError) {
        _error = true;
        _status = r.message;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('رسالة للطفل')),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            StatusBanner(message: _status, isError: _error),
            TextField(
              controller: _msg,
              maxLines: 5,
              decoration: const InputDecoration(
                labelText: 'نص الرسالة',
                alignLabelWithHint: true,
              ),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _busy ? null : _send,
              child: Text(_busy ? 'جاري الإرسال…' : 'إرسال'),
            ),
          ],
        ),
      ),
    );
  }
}
