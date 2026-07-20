import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/guardian_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';
import 'parent_alerts_screen.dart';
import 'parent_apps_screen.dart';
import 'parent_block_screen.dart';
import 'parent_children_screen.dart';
import 'parent_link_screen.dart';
import 'parent_message_screen.dart';
import 'parent_reports_screen.dart';
import 'parent_screen_time_screen.dart';
import 'parent_settings_screen.dart';
import '../role_select_screen.dart';

/// Hub — مطابق لـ ParentMainActivity / ParentHubUi.
class ParentHomeScreen extends StatefulWidget {
  const ParentHomeScreen({super.key});

  @override
  State<ParentHomeScreen> createState() => _ParentHomeScreenState();
}

class _ParentHomeScreenState extends State<ParentHomeScreen> {
  ChildDashboardData? _dash;
  String _status = '';
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
  }

  Future<void> _refresh() async {
    final session = context.read<AppSession>();
    if (session.childCode.isEmpty) {
      setState(() => _status = 'اربطي طفلاً أولاً من «ربط طفل» أو «الأطفال»');
      return;
    }
    setState(() {
      _loading = true;
      _status = '';
    });
    final r = await context.read<GuardianApi>().fetchChildDashboard(session.childCode);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (r is ApiChildDashboard) {
        _dash = r.data;
        _status = r.data.online ? 'الطفل متصل' : 'الطفل غير متصل حالياً';
      } else if (r is ApiError) {
        _status = r.message;
      }
    });
  }

  void _open(Widget page) {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => page));
  }

  @override
  Widget build(BuildContext context) {
    final session = context.watch<AppSession>();
    final d = _dash;

    return Scaffold(
      appBar: AppBar(
        title: const Text('لوحة ولي الأمر'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loading ? null : _refresh,
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () async {
              await session.logoutParent();
              if (!context.mounted) return;
              Navigator.of(context).pushAndRemoveUntil(
                MaterialPageRoute(builder: (_) => const RoleSelectScreen()),
                (_) => false,
              );
            },
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Text('مرحباً، ${session.parentEmail}', textAlign: TextAlign.right),
            const SizedBox(height: 4),
            Text(
              session.childCode.isEmpty
                  ? 'لا يوجد طفل نشط'
                  : 'الطفل النشط: ${session.childName.isEmpty ? session.childCode : session.childName}',
              style: const TextStyle(fontWeight: FontWeight.w600),
              textAlign: TextAlign.right,
            ),
            const SizedBox(height: 8),
            StatusBanner(message: _status),
            if (_loading) const LinearProgressIndicator(),
            if (d != null) ...[
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text('وقت اليوم: ${d.todaySeconds ~/ 60} دقيقة'),
                      Text('تنبيهات اليوم: ${d.alertsToday}'),
                      Text('الصلاحيات: ${d.permissionsOk ? 'مفعّلة' : 'غير مفعّلة'}'),
                      Text('البطارية: ${d.batteryPct >= 0 ? '${d.batteryPct}%' : '—'}'),
                      Text('الجهاز: ${d.deviceName.isEmpty ? '—' : d.deviceName}'),
                    ],
                  ),
                ),
              ),
            ],
            const SizedBox(height: 8),
            HubTile(
              icon: Icons.link,
              title: 'ربط طفل',
              subtitle: 'إرسال رمز الربط وإتمام الإضافة',
              onTap: () => _open(const ParentLinkScreen()),
            ),
            HubTile(
              icon: Icons.people,
              title: 'الأطفال',
              subtitle: 'قائمة الأطفال المرتبطة',
              onTap: () => _open(const ParentChildrenScreen()),
            ),
            HubTile(
              icon: Icons.apps,
              title: 'التطبيقات',
              subtitle: 'التطبيقات المثبتة على جهاز الطفل',
              onTap: () => _open(const ParentAppsScreen()),
            ),
            HubTile(
              icon: Icons.block,
              title: 'الحظر',
              subtitle: 'حظر تطبيق أو تطبيق القائمة الافتراضية',
              onTap: () => _open(const ParentBlockScreen()),
            ),
            HubTile(
              icon: Icons.timer,
              title: 'وقت الشاشة',
              subtitle: 'حدود يومية ووقت نوم',
              onTap: () => _open(const ParentScreenTimeScreen()),
            ),
            HubTile(
              icon: Icons.notifications_active,
              title: 'التنبيهات',
              subtitle: 'محاولات فتح تطبيقات محظورة',
              onTap: () => _open(const ParentAlertsScreen()),
            ),
            HubTile(
              icon: Icons.bar_chart,
              title: 'التقارير',
              subtitle: 'تقرير يومي ورسم أسبوعي',
              onTap: () => _open(const ParentReportsScreen()),
            ),
            HubTile(
              icon: Icons.message,
              title: 'رسالة للطفل',
              subtitle: 'إرسال رسالة تظهر كتنبيه',
              onTap: () => _open(const ParentMessageScreen()),
            ),
            HubTile(
              icon: Icons.settings,
              title: 'الإعدادات',
              subtitle: 'إعدادات ولي الأمر والخادم',
              onTap: () => _open(const ParentSettingsScreen()),
            ),
            const SizedBox(height: 16),
            const Text(
              'السيرفر: parental-server-4mms.onrender.com',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.black45, fontSize: 12),
            ),
          ],
        ),
      ),
    );
  }
}
