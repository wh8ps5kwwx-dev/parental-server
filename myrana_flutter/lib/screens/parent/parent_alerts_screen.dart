import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/guardian_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';

class ParentAlertsScreen extends StatefulWidget {
  const ParentAlertsScreen({super.key});

  @override
  State<ParentAlertsScreen> createState() => _ParentAlertsScreenState();
}

class _ParentAlertsScreenState extends State<ParentAlertsScreen> {
  List<String> _lines = [];
  String _status = '';
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    final code = context.read<AppSession>().childCode;
    if (code.isEmpty) {
      setState(() {
        _loading = false;
        _status = 'لا يوجد طفل نشط';
      });
      return;
    }
    final r = await context.read<GuardianApi>().fetchAlerts(code);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (r is ApiAlerts) {
        _lines = r.lines;
        _status = r.error ?? (_lines.isEmpty ? 'لا تنبيهات' : '');
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('التنبيهات'),
        actions: [IconButton(onPressed: _load, icon: const Icon(Icons.refresh))],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                StatusBanner(message: _status),
                ..._lines.map(
                  (l) => Card(
                    child: ListTile(
                      leading: const Icon(Icons.warning_amber),
                      title: Text(l, textAlign: TextAlign.right),
                    ),
                  ),
                ),
              ],
            ),
    );
  }
}
