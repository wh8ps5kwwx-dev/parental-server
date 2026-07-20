import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'data/api/api_client.dart';
import 'data/api/child_api.dart';
import 'data/api/guardian_api.dart';
import 'screens/child/child_home_screen.dart';
import 'screens/parent/parent_home_screen.dart';
import 'screens/parent/parent_login_screen.dart';
import 'screens/role_select_screen.dart';
import 'session/app_session.dart';
import 'theme/app_theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final session = AppSession();
  await session.load();
  final client = ApiClient();
  runApp(MyranaApp(
    session: session,
    guardianApi: GuardianApi(client),
    childApi: ChildApi(client),
  ));
}

class MyranaApp extends StatelessWidget {
  const MyranaApp({
    super.key,
    required this.session,
    required this.guardianApi,
    required this.childApi,
  });

  final AppSession session;
  final GuardianApi guardianApi;
  final ChildApi childApi;

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider.value(value: session),
        Provider.value(value: guardianApi),
        Provider.value(value: childApi),
      ],
      child: MaterialApp(
        title: 'MYRana Flutter',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.light(),
        locale: const Locale('ar'),
        builder: (context, child) => Directionality(
          textDirection: TextDirection.rtl,
          child: child ?? const SizedBox.shrink(),
        ),
        home: const _RootRouter(),
      ),
    );
  }
}

class _RootRouter extends StatelessWidget {
  const _RootRouter();

  @override
  Widget build(BuildContext context) {
    final session = context.watch<AppSession>();
    switch (session.role) {
      case 'parent':
        // ولي أمر بدون تحقق بريد → شاشة الدخول وليس الاختيار فقط
        if (session.parentEmailVerified) {
          return const ParentHomeScreen();
        }
        return const ParentLoginScreen();
      case 'child':
        return const ChildHomeScreen();
      default:
        return const RoleSelectScreen();
    }
  }
}
