import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/guardian_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';

/// التحقق من موقع — هل محظور في سياسة الطفل / الكتالوج؟
class ParentUrlCheckScreen extends StatefulWidget {
  const ParentUrlCheckScreen({super.key});

  @override
  State<ParentUrlCheckScreen> createState() => _ParentUrlCheckScreenState();
}

class _ParentUrlCheckScreenState extends State<ParentUrlCheckScreen> {
  final _url = TextEditingController();
  String _status = '';
  bool _error = false;
  bool _busy = false;
  ApiUrlCheck? _result;

  @override
  void dispose() {
    _url.dispose();
    super.dispose();
  }

  Future<void> _check() async {
    final session = context.read<AppSession>();
    final input = _url.text.trim();
    if (input.isEmpty) {
      setState(() {
        _error = true;
        _status = 'أدخلي رابطاً أو نطاقاً (مثل example.com)';
        _result = null;
      });
      return;
    }
    setState(() {
      _busy = true;
      _error = false;
      _status = '';
      _result = null;
    });
    final r = await context.read<GuardianApi>().checkUrl(
          url: input,
          childCode: session.childCode,
        );
    if (!mounted) return;
    setState(() {
      _busy = false;
      if (r is ApiUrlCheck) {
        _result = r;
        _status = r.explanation;
        _error = false;
      } else if (r is ApiError) {
        _error = true;
        _status = r.message;
        _result = null;
      }
    });
  }

  Widget _flagRow({
    required String label,
    required bool yes,
    String? detail,
  }) {
    final color = yes ? Colors.red.shade700 : Colors.teal.shade700;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(
            yes ? Icons.block : Icons.check_circle_outline,
            color: color,
            size: 22,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '$label: ${yes ? 'نعم' : 'لا'}',
                  style: TextStyle(
                    fontWeight: FontWeight.w600,
                    color: color,
                  ),
                  textAlign: TextAlign.right,
                ),
                if (detail != null && detail.isNotEmpty)
                  Text(
                    detail,
                    style: const TextStyle(fontSize: 12, color: Colors.black54),
                    textAlign: TextAlign.right,
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final session = context.watch<AppSession>();

    return Scaffold(
      appBar: AppBar(title: const Text('التحقق من موقع')),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(
            session.childCode.isEmpty
                ? 'لا يوجد طفل نشط — سيُفحص الكتالوج فقط.'
                : 'الطفل النشط: ${session.childName.isEmpty ? session.childCode : session.childName}',
            textAlign: TextAlign.right,
            style: const TextStyle(fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 8),
          const Text(
            'أدخلي رابطاً أو نطاقاً للتحقق هل هو محظور في سياسة الطفل '
            'أو موجود في كتالوج الحظر الافتراضي.',
            textAlign: TextAlign.right,
            style: TextStyle(color: Colors.black54, fontSize: 13),
          ),
          const SizedBox(height: 16),
          StatusBanner(message: _status, isError: _error),
          TextField(
            controller: _url,
            keyboardType: TextInputType.url,
            textInputAction: TextInputAction.search,
            onSubmitted: (_) => _busy ? null : _check(),
            decoration: const InputDecoration(
              labelText: 'الرابط أو النطاق',
              hintText: 'https://example.com أو example.com',
              prefixIcon: Icon(Icons.language),
            ),
          ),
          const SizedBox(height: 16),
          ElevatedButton.icon(
            onPressed: _busy ? null : _check,
            icon: _busy
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.search),
            label: Text(_busy ? 'جاري التحقق…' : 'تحقق'),
          ),
          if (_result != null) ...[
            const SizedBox(height: 20),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      'النطاق: ${_result!.host}',
                      style: const TextStyle(fontWeight: FontWeight.bold),
                      textAlign: TextAlign.right,
                    ),
                    const Divider(height: 20),
                    _flagRow(
                      label: 'هل محظور؟',
                      yes: _result!.blocked,
                      detail: _result!.blocked
                          ? 'سيُمنع على جهاز الطفل حسب السياسة الحالية'
                          : 'غير محظور حالياً على جهاز الطفل',
                    ),
                    _flagRow(
                      label: 'هل في السياسة؟',
                      yes: _result!.inPolicy,
                      detail: _result!.policyMatch,
                    ),
                    _flagRow(
                      label: 'هل في الكتالوج؟',
                      yes: _result!.inCatalog,
                      detail: _result!.catalogMatch,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      _result!.explanation,
                      textAlign: TextAlign.right,
                      style: const TextStyle(height: 1.4),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
