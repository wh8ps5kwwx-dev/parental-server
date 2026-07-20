import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/guardian_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';

class ParentReportsScreen extends StatefulWidget {
  const ParentReportsScreen({super.key});

  @override
  State<ParentReportsScreen> createState() => _ParentReportsScreenState();
}

class _ParentReportsScreenState extends State<ParentReportsScreen> {
  String _daily = '';
  WeeklyChartData? _chart;
  List<UsageAppItem> _usage = [];
  String _status = '';
  bool _loading = false;

  Future<void> _load() async {
    final code = context.read<AppSession>().childCode;
    if (code.isEmpty) {
      setState(() => _status = 'لا يوجد طفل نشط');
      return;
    }
    setState(() {
      _loading = true;
      _status = '';
    });
    final api = context.read<GuardianApi>();
    final daily = await api.fetchDailyReport(code);
    final chart = await api.fetchWeeklyChart(code, days: 7);
    final usage = await api.fetchWeeklyUsage(code, days: 7);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (daily is ApiReportText) _daily = daily.text;
      if (daily is ApiError) _status = daily.message;
      if (chart is ApiWeeklyChart) _chart = chart.data;
      if (usage is ApiUsageList) _usage = usage.apps;
    });
  }

  Future<void> _requestUsage() async {
    final session = context.read<AppSession>();
    if (session.childCode.isEmpty) return;
    setState(() => _status = 'جاري طلب رفع الاستخدام من جهاز الطفل…');
    final r = await context.read<GuardianApi>().requestUsageFromChild(
          session.childCode,
          session.parentEmail,
        );
    if (!mounted) return;
    setState(() {
      _status = r is ApiOk ? r.message : (r is ApiError ? r.message : 'تم الطلب');
    });
    await Future<void>.delayed(const Duration(seconds: 2));
    if (mounted) await _load();
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  @override
  Widget build(BuildContext context) {
    final c = _chart;
    return Scaffold(
      appBar: AppBar(
        title: const Text('التقارير'),
        actions: [
          IconButton(
            tooltip: 'طلب رفع من الطفل',
            onPressed: _requestUsage,
            icon: const Icon(Icons.cloud_download),
          ),
          IconButton(onPressed: _load, icon: const Icon(Icons.refresh)),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          StatusBanner(message: _status),
          if (_loading) const LinearProgressIndicator(),
          OutlinedButton.icon(
            onPressed: _requestUsage,
            icon: const Icon(Icons.sync),
            label: const Text('طلب تحديث الاستخدام من جهاز الطفل'),
          ),
          const SizedBox(height: 8),
          if (_daily.isNotEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Text(_daily, textAlign: TextAlign.right),
              ),
            ),
          if (c != null) ...[
            const SizedBox(height: 8),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text('متوسط يومي: ${c.avgDailyScreenSeconds ~/ 60} د'),
                    Text('تنبيهات الأسبوع: ${c.alertsWeek}'),
                    const SizedBox(height: 8),
                    const Text('أفضل التطبيقات:', textAlign: TextAlign.right),
                    ...c.topApps.take(8).map((row) {
                      final pkg = (row['package_name'] ?? row['app_label'] ?? '?').toString();
                      final sec = (row['total_seconds'] as num?)?.toInt() ?? 0;
                      return Text('• $pkg — ${sec ~/ 60} د', textAlign: TextAlign.right);
                    }),
                  ],
                ),
              ),
            ),
          ],
          if (_usage.isNotEmpty) ...[
            const SizedBox(height: 8),
            const Text('استخدام أسبوعي', style: TextStyle(fontWeight: FontWeight.bold)),
            ..._usage.take(20).map(
                  (u) => ListTile(
                    title: Text(
                      u.appLabel.isEmpty ? u.packageName : u.appLabel,
                      textAlign: TextAlign.right,
                    ),
                    trailing: Text('${u.totalSeconds ~/ 60} د'),
                  ),
                ),
          ],
        ],
      ),
    );
  }
}
