import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../data/api/child_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../util/child_code_normalizer.dart';
import '../../widgets/common_widgets.dart';
import 'child_waiting_screen.dart';

/// تسجيل جهاز الطفل — مطابق لـ register-child-device.
class ChildRegisterScreen extends StatefulWidget {
  const ChildRegisterScreen({super.key});

  @override
  State<ChildRegisterScreen> createState() => _ChildRegisterScreenState();
}

class _ChildRegisterScreenState extends State<ChildRegisterScreen> {
  late final TextEditingController _code;
  final _name = TextEditingController(text: 'هاتف الطفل');
  String _status = '';
  bool _error = false;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _code = TextEditingController(text: _generateCode());
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final existing = context.read<AppSession>().childCode;
      if (existing.isNotEmpty) {
        _code.text = ChildCodeNormalizer.forApi(existing);
      }
    });
  }

  String _generateCode() {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    final r = Random.secure();
    return List.generate(8, (_) => chars[r.nextInt(chars.length)]).join();
  }

  @override
  void dispose() {
    _code.dispose();
    _name.dispose();
    super.dispose();
  }

  Future<void> _register() async {
    setState(() {
      _busy = true;
      _error = false;
      _status = '';
    });
    final api = context.read<ChildApi>();
    final session = context.read<AppSession>();
    final r = await api.registerDevice(
      childCode: _code.text,
      deviceName: _name.text,
      androidVersion: 'Flutter',
    );
    if (!mounted) return;
    if (r is ApiOk) {
      final data = r.data ?? {};
      final stored = (data['child_code_clean'] ?? data['child_code'] ?? _code.text)
          .toString();
      final verify = (data['device_verify_code'] ?? '').toString();
      await session.setChildRegistration(
        code: ChildCodeNormalizer.forApi(stored),
        verifyCode: verify,
        linked: false,
      );
      await session.setRole('child');
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(builder: (_) => const ChildWaitingScreen()),
      );
    } else {
      setState(() {
        _busy = false;
        _error = true;
        _status = r is ApiError ? r.message : 'فشل التسجيل';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('تسجيل جهاز الطفل')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text(
            'هذا الكود يُعرض لولي الأمر لربطه من تطبيق الأم.',
            textAlign: TextAlign.right,
          ),
          const SizedBox(height: 12),
          StatusBanner(message: _status, isError: _error),
          TextField(
            controller: _code,
            textCapitalization: TextCapitalization.characters,
            decoration: InputDecoration(
              labelText: 'كود الجهاز',
              suffixIcon: IconButton(
                icon: const Icon(Icons.copy),
                onPressed: () {
                  Clipboard.setData(ClipboardData(text: _code.text));
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('تم النسخ')),
                  );
                },
              ),
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _name,
            decoration: const InputDecoration(labelText: 'اسم الجهاز'),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: _busy ? null : _register,
            child: Text(_busy ? 'جاري التسجيل…' : 'تسجيل الجهاز'),
          ),
        ],
      ),
    );
  }
}
