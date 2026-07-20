import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/guardian_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../util/child_code_normalizer.dart';
import '../../widgets/common_widgets.dart';

/// ربط الطفل — send-link-code + add-child + restore-link.
class ParentLinkScreen extends StatefulWidget {
  const ParentLinkScreen({super.key});

  @override
  State<ParentLinkScreen> createState() => _ParentLinkScreenState();
}

class _ParentLinkScreenState extends State<ParentLinkScreen> {
  final _code = TextEditingController();
  final _otp = TextEditingController();
  final _name = TextEditingController(text: 'طفل');
  final _age = TextEditingController(text: '10');
  final _restoreToken = TextEditingController();
  String _status = '';
  bool _error = false;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final s = context.read<AppSession>();
      if (s.restoreToken.isNotEmpty) {
        _restoreToken.text = s.restoreToken;
      }
    });
  }

  @override
  void dispose() {
    _code.dispose();
    _otp.dispose();
    _name.dispose();
    _age.dispose();
    _restoreToken.dispose();
    super.dispose();
  }

  Future<void> _sendLink() async {
    final session = context.read<AppSession>();
    setState(() {
      _busy = true;
      _error = false;
      _status = '';
    });
    final r = await context.read<GuardianApi>().sendLinkCode(
          session.parentEmail,
          _code.text,
        );
    if (!mounted) return;
    setState(() {
      _busy = false;
      if (r is ApiEmailCodeSent) {
        _status = r.message;
        if (r.verificationCode != null) _otp.text = r.verificationCode!;
      } else if (r is ApiError) {
        _error = true;
        _status = r.message;
      }
    });
  }

  Future<void> _link() async {
    final session = context.read<AppSession>();
    setState(() {
      _busy = true;
      _error = false;
      _status = '';
    });
    final r = await context.read<GuardianApi>().addChild(
          name: _name.text,
          age: int.tryParse(_age.text) ?? 10,
          childCode: _code.text,
          deviceVerifyCode: _otp.text,
          guardianEmail: session.parentEmail,
          guardianRole: session.guardianRole,
        );
    if (!mounted) return;
    await _handleLinkResult(r);
  }

  Future<void> _restore() async {
    final session = context.read<AppSession>();
    setState(() {
      _busy = true;
      _error = false;
      _status = '';
    });
    final r = await context.read<GuardianApi>().restoreLink(
          guardianEmail: session.parentEmail,
          childCode: _code.text,
          restoreToken: _restoreToken.text,
          name: _name.text,
          age: int.tryParse(_age.text) ?? 10,
          guardianRole: session.guardianRole,
        );
    if (!mounted) return;
    await _handleLinkResult(r);
  }

  Future<void> _handleLinkResult(ApiResult r) async {
    final session = context.read<AppSession>();
    if (r is ApiLinkSuccess || r is ApiOk) {
      final code = r is ApiLinkSuccess && (r.childCode?.isNotEmpty ?? false)
          ? ChildCodeNormalizer.forApi(r.childCode!)
          : ChildCodeNormalizer.forApi(_code.text);
      await session.setActiveChild(code: code, name: _name.text.trim());
      if (r is ApiLinkSuccess && (r.restoreToken?.isNotEmpty ?? false)) {
        await session.setRestoreToken(r.restoreToken!);
      }
      setState(() {
        _busy = false;
        _status = r is ApiLinkSuccess
            ? r.message
            : (r as ApiOk).message;
        if (r is ApiLinkSuccess && (r.restoreToken?.isNotEmpty ?? false)) {
          _restoreToken.text = r.restoreToken!;
          _status = '$_status\nرمز الاستعادة محفوظ أدناه.';
        }
      });
    } else {
      setState(() {
        _busy = false;
        _error = true;
        _status = r is ApiError ? r.message : 'فشل الربط';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('ربط طفل')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          const Text(
            'أدخلي كود جهاز الطفل الظاهر في تطبيق الطفل بعد التسجيل.',
            textAlign: TextAlign.right,
          ),
          const SizedBox(height: 12),
          StatusBanner(message: _status, isError: _error),
          TextField(
            controller: _code,
            decoration: const InputDecoration(labelText: 'كود الطفل'),
            textCapitalization: TextCapitalization.characters,
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _name,
            decoration: const InputDecoration(labelText: 'اسم الطفل'),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _age,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'العمر'),
          ),
          const SizedBox(height: 12),
          ElevatedButton(
            onPressed: _busy ? null : _sendLink,
            child: const Text('إرسال رمز الربط للبريد'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _otp,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'رمز الربط من البريد'),
          ),
          const SizedBox(height: 12),
          ElevatedButton(
            onPressed: _busy ? null : _link,
            child: Text(_busy ? 'جاري الربط…' : 'إتمام الربط'),
          ),
          const Divider(height: 32),
          const Text(
            'استعادة الربط (بعد إعادة تشغيل السيرفر)',
            style: TextStyle(fontWeight: FontWeight.bold),
            textAlign: TextAlign.right,
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _restoreToken,
            decoration: const InputDecoration(labelText: 'رمز الاستعادة'),
          ),
          const SizedBox(height: 8),
          OutlinedButton(
            onPressed: _busy ? null : _restore,
            child: const Text('استعادة الربط'),
          ),
        ],
      ),
    );
  }
}
