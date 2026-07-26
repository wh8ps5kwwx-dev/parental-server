import 'package:flutter/material.dart';

import '../config/app_flavor.dart';
import '../screens/child/child_home_screen.dart';
import '../screens/parent/parent_login_screen.dart';
import '../screens/role_select_screen.dart';

/// بعد تسجيل الخروج: نكهة مقفلة → شاشة الدور؛ وإلا اختيار الدور.
Widget logoutDestination() {
  if (AppFlavor.isParent) return const ParentLoginScreen();
  if (AppFlavor.isChild) return const ChildHomeScreen();
  return const RoleSelectScreen();
}

void goAfterLogout(BuildContext context) {
  Navigator.of(context).pushAndRemoveUntil(
    MaterialPageRoute(builder: (_) => logoutDestination()),
    (_) => false,
  );
}
