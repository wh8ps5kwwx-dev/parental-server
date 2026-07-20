import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/child_api.dart';
import '../../data/models/api_models.dart';
import '../../native/enforcement_channel.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';
import '../role_select_screen.dart';
import 'child_academy_screen.dart';
import 'child_permissions_screen.dart';
import 'child_register_screen.dart';
import 'child_waiting_screen.dart';

class ChildHomeScreen extends StatefulWidget {
  const ChildHomeScreen({super.key});

  @override
  State<ChildHomeScreen> createState() => _ChildHomeScreenState();
}

class _ChildHomeScreenState extends State<ChildHomeScreen> {
  Timer? _timer;
  String _cmdStatus = '';
  String _lastCommand = '';
  bool _monitorStarted = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final s = context.read<AppSession>();
      if (s.childCode.isNotEmpty && s.childLinked) {
        _bootstrap();
        _timer = Timer.periodic(const Duration(seconds: 20), (_) => _tick());
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _bootstrap() async {
    if (EnforcementChannel.isAndroid) {
      final session = context.read<AppSession>();
      if (session.childCode.isNotEmpty) {
        await EnforcementChannel.setChildContext(childCode: session.childCode);
      }
      final ok = await EnforcementChannel.startForegroundMonitor();
      await EnforcementChannel.syncPolicy();
      if (mounted) setState(() => _monitorStarted = ok);
    }
    await _tick();
  }

  Future<void> _tick() async {
    final session = context.read<AppSession>();
    if (session.childCode.isEmpty || !session.childLinked) return;
    final api = context.read<ChildApi>();
    final permsOk = await EnforcementChannel.permissionsReady();
    await api.heartbeat(
      childCode: session.childCode,
      permissionsOk: permsOk,
    );
    final cmd = await api.pollCommand(session.childCode);
    if (!mounted) return;
    if (cmd is ApiOk) {
      await _handleCommand(cmd.data ?? {});
    }
  }

  Future<void> _handleCommand(Map<String, dynamic> data) async {
    final action = (data['action'] ?? data['command'] ?? '').toString().trim();
    final value = (data['value'] ?? '').toString().trim();
    if (action.isEmpty || action == 'none') return;

    setState(() {
      _lastCommand = value.isEmpty ? action : '$action → $value';
    });

    final session = context.read<AppSession>();
    final api = context.read<ChildApi>();

    switch (action) {
      case 'block_app':
      case 'freeze_app':
        if (value.isNotEmpty) {
          await EnforcementChannel.addBlockedPackage(value);
          await EnforcementChannel.enforceNow();
          setState(() => _cmdStatus = 'تم حظر: $value');
        }
        break;
      case 'unblock_app':
        if (value.isNotEmpty) {
          await EnforcementChannel.removeBlockedPackage(value);
          setState(() => _cmdStatus = 'تم إلغاء حظر: $value');
        }
        break;
      case 'block_site':
        if (value.isNotEmpty) {
          await EnforcementChannel.addBlockedHost(value);
          setState(() => _cmdStatus = 'تم حظر موقع: $value');
        }
        break;
      case 'unblock_site':
        if (value.isNotEmpty) {
          await EnforcementChannel.removeBlockedHost(value);
          setState(() => _cmdStatus = 'تم إلغاء حظر موقع: $value');
        }
        break;
      case 'allow':
        await EnforcementChannel.clearBlockedPackages();
        setState(() => _cmdStatus = 'تم السماح — أُفرغت قائمة الحظر المحلية');
        break;
      case 'request_usage':
        final usage = await EnforcementChannel.queryUsageToday();
        if (usage.isNotEmpty) {
          final r = await api.uploadUsage(
            childCode: session.childCode,
            secondsByPackage: usage,
          );
          setState(() {
            _cmdStatus = r is ApiOk
                ? 'تم رفع الاستخدام (${usage.length} تطبيق)'
                : (r is ApiError ? r.message : 'فشل رفع الاستخدام');
          });
        } else {
          setState(() => _cmdStatus = 'لا بيانات استخدام — فعّلي Usage Access');
        }
        break;
      case 'sync_installed_apps':
        final apps = await EnforcementChannel.getInstalledApps();
        if (apps.isEmpty) {
          setState(() => _cmdStatus = 'تعذّر جمع التطبيقات من الجهاز');
          break;
        }
        final r = await api.syncInstalledApps(
          childCode: session.childCode,
          apps: apps,
        );
        setState(() {
          _cmdStatus = r is ApiOk
              ? 'تمت مزامنة ${apps.length} تطبيق'
              : (r is ApiError ? r.message : 'فشل المزامنة');
        });
        break;
      case 'guardian_message':
        if (value.isNotEmpty && mounted) {
          setState(() => _cmdStatus = 'رسالة من ولي الأمر');
          await showDialog<void>(
            context: context,
            builder: (ctx) => AlertDialog(
              title: const Text('رسالة من ولي الأمر'),
              content: Text(value, textAlign: TextAlign.right),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: const Text('حسناً'),
                ),
              ],
            ),
          );
        }
        break;
      default:
        // أوامر غير معروفة محلياً — نسحب السياسة من السيرفر احتياطاً
        await EnforcementChannel.syncPolicy();
        setState(() => _cmdStatus = 'مزامنة سياسة بعد أمر: $action');
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = context.watch<AppSession>();

    if (session.childCode.isEmpty) {
      return const ChildRegisterScreen();
    }
    if (!session.childLinked) {
      return const ChildWaitingScreen();
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('جهاز الطفل'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () async {
              _timer?.cancel();
              if (EnforcementChannel.isAndroid) {
                await EnforcementChannel.stopForegroundMonitor();
              }
              await session.clearChild();
              if (!context.mounted) return;
              Navigator.of(context).pushAndRemoveUntil(
                MaterialPageRoute(builder: (_) => const RoleSelectScreen()),
                (_) => false,
              );
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          StatusBanner(
            message: _monitorStarted
                ? 'المراقبة بالخلفية نشطة — الكود: ${session.childCode}'
                : 'الجهاز مربوط — الكود: ${session.childCode}',
          ),
          if (_cmdStatus.isNotEmpty) StatusBanner(message: _cmdStatus),
          if (_lastCommand.isNotEmpty)
            Card(
              child: ListTile(
                leading: const Icon(Icons.mail),
                title: const Text('آخر أمر', textAlign: TextAlign.right),
                subtitle: Text(_lastCommand, textAlign: TextAlign.right),
              ),
            ),
          HubTile(
            icon: Icons.security,
            title: 'الصلاحيات المطلوبة',
            subtitle: 'Usage Access / Accessibility / الخدمة الأمامية',
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const ChildPermissionsScreen()),
              );
            },
          ),
          HubTile(
            icon: Icons.school,
            title: 'الأكاديمية',
            subtitle: 'تحديات + مدينة التعلم',
            onTap: () {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const ChildAcademyScreen()),
              );
            },
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: () async {
              await EnforcementChannel.syncPolicy();
              await _tick();
            },
            icon: const Icon(Icons.sync),
            label: const Text('مزامنة الآن مع السيرفر'),
          ),
          if (!EnforcementChannel.isAndroid) ...[
            const SizedBox(height: 8),
            const Text(
              'ملاحظة: الرقابة الصلبة (حظر التطبيقات) متاحة على أندرويد فقط. '
              'iOS يدعم الواجهة والـ API مع قيود Screen Time.',
              textAlign: TextAlign.right,
              style: TextStyle(color: Colors.black54, fontSize: 12),
            ),
          ],
        ],
      ),
    );
  }
}
