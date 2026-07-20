import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/guardian_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';
import '../role_select_screen.dart';
import 'parent_home_screen.dart';

/// OTP login — مطابق لتدفق send-email-code / verify-email-code.
class ParentLoginScreen extends StatefulWidget {
  const ParentLoginScreen({super.key});

  @override
  State<ParentLoginScreen> createState() => _ParentLoginScreenState();
}

class _ParentLoginScreenState extends State<ParentLoginScreen> {
  final _email = TextEditingController();
  final _otp = TextEditingController();
  String _status = '';
  bool _error = false;
  bool _busy = false;
  bool _codeSent = false;
  String? _devCode;

  @override
  void dispose() {
    _email.dispose();
    _otp.dispose();
    super.dispose();
  }

  Future<void> _sendCode() async {
    setState(() {
      _busy = true;
      _status = '';
      _error = false;
    });
    final api = context.read<GuardianApi>();
    final r = await api.sendEmailCode(_email.text);
    if (!mounted) return;
    setState(() {
      _busy = false;
      if (r is ApiEmailCodeSent) {
        _codeSent = true;
        _status = r.message;
        _devCode = r.verificationCode;
        if (_devCode != null && _devCode!.isNotEmpty) {
          _otp.text = _devCode!;
        }
      } else if (r is ApiError) {
        _error = true;
        _status = r.message;
      }
    });
  }

  Future<void> _verify() async {
    setState(() {
      _busy = true;
      _status = '';
      _error = false;
    });
    final api = context.read<GuardianApi>();
    final session = context.read<AppSession>();
    final r = await api.verifyEmailCode(_email.text, _otp.text);
    if (!mounted) return;
    if (r is ApiOk) {
      await session.setParentSession(email: _email.text.trim(), verified: true);
      await session.setRole('parent');
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const ParentHomeScreen()),
        (_) => false,
      );
    } else {
      setState(() {
        _busy = false;
        _error = true;
        _status = r is ApiError ? r.message : 'فشل التحقق';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('دخول ولي الأمر'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () async {
            await context.read<AppSession>().setRole('none');
            if (!context.mounted) return;
            Navigator.of(context).pushAndRemoveUntil(
              MaterialPageRoute(builder: (_) => const RoleSelectScreen()),
              (_) => false,
            );
          },
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          StatusBanner(message: _status, isError: _error),
          TextField(
            controller: _email,
            keyboardType: TextInputType.emailAddress,
            decoration: const InputDecoration(
              labelText: 'البريد الإلكتروني',
              hintText: 'parent@email.com',
            ),
          ),
          const SizedBox(height: 12),
          ElevatedButton(
            onPressed: _busy ? null : _sendCode,
            child: Text(_busy && !_codeSent ? 'جاري الإرسال…' : 'إرسال رمز التحقق'),
          ),
          if (_codeSent) ...[
            const SizedBox(height: 20),
            TextField(
              controller: _otp,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'رمز التحقق (OTP)',
              ),
            ),
            const SizedBox(height: 12),
            ElevatedButton(
              onPressed: _busy ? null : _verify,
              child: Text(_busy ? 'جاري التحقق…' : 'تأكيد الدخول'),
            ),
          ],
        ],
      ),
    );
  }
}
