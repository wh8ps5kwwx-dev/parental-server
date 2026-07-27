import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/guardian_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';

class ParentAppsScreen extends StatefulWidget {
  const ParentAppsScreen({super.key});

  @override
  State<ParentAppsScreen> createState() => _ParentAppsScreenState();
}

class _ParentAppsScreenState extends State<ParentAppsScreen> {
  List<InstalledAppItem> _apps = [];
  String _status = '';
  bool _loading = false;
  String _filter = '';

  Future<void> _load() async {
    final code = context.read<AppSession>().childCode;
    if (code.isEmpty) {
      setState(() => _status = 'اختاري طفلاً نشطاً أولاً');
      return;
    }
    setState(() {
      _loading = true;
      _status = '';
    });
    final r = await context.read<GuardianApi>().fetchInstalledApps(code);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (r is ApiInstalledApps) {
        _apps = r.items;
        _status = 'عدد التطبيقات: ${r.count}';
      } else if (r is ApiError) {
        _status = r.message;
      }
    });
  }

  Future<void> _requestSync() async {
    final session = context.read<AppSession>();
    if (session.childCode.isEmpty) return;
    setState(() => _status = 'جاري طلب المزامنة من جهاز الطفل…');
    final r = await context.read<GuardianApi>().requestInstalledAppsSync(
          session.childCode,
          session.parentEmail,
        );
    if (!mounted) return;
    setState(() {
      _status = r is ApiOk ? r.message : (r is ApiError ? r.message : 'تم الطلب');
    });
  }

  Future<void> _block(InstalledAppItem app) async {
    final session = context.read<AppSession>();
    final r = await context.read<GuardianApi>().sendCommand(
          action: 'block_app',
          value: app.packageName,
          childCode: session.childCode,
          guardianEmail: session.parentEmail,
        );
    if (!mounted) return;
    final msg = r is ApiOk
        ? 'تم إرسال حظر: ${app.appLabel}'
        : (r is ApiError ? r.message : 'فشل');
    setState(() => _status = msg);
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  @override
  Widget build(BuildContext context) {
    final filtered = _filter.trim().isEmpty
        ? _apps
        : _apps
            .where((a) =>
                a.appLabel.toLowerCase().contains(_filter.toLowerCase()) ||
                a.packageName.toLowerCase().contains(_filter.toLowerCase()))
            .toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('التطبيقات'),
        actions: [
          IconButton(onPressed: _requestSync, icon: const Icon(Icons.sync)),
          IconButton(onPressed: _load, icon: const Icon(Icons.refresh)),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 12, 12, 0),
            child: StatusBanner(message: _status),
          ),
          Padding(
            padding: const EdgeInsets.all(12),
            child: TextField(
              decoration: const InputDecoration(
                labelText: 'بحث',
                prefixIcon: Icon(Icons.search),
              ),
              onChanged: (v) => setState(() => _filter = v),
            ),
          ),
          if (_loading) const LinearProgressIndicator(),
          Expanded(
            child: ListView.builder(
              itemCount: filtered.length,
              itemBuilder: (_, i) {
                final a = filtered[i];
                return Card(
                  margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                  child: ListTile(
                    title: Text(a.appLabel, textAlign: TextAlign.right),
                    subtitle: Text(a.packageName, textAlign: TextAlign.right),
                    trailing: IconButton(
                      tooltip: 'حظر',
                      icon: const Icon(Icons.block, color: Colors.red),
                      onPressed: () => _block(a),
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
