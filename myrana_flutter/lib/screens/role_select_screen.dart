import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../session/app_session.dart';
import '../theme/app_theme.dart';
import 'child/child_home_screen.dart';
import 'parent/parent_login_screen.dart';

/// اختيار الدور: ولي أمر / طفل — بدل نكهتي parent و child في Gradle.
class RoleSelectScreen extends StatelessWidget {
  const RoleSelectScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 32),
              Text(
                'MYRana',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.displaySmall?.copyWith(
                      color: AppTheme.primary,
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const SizedBox(height: 8),
              Text(
                'تحويل من Kotlin إلى Flutter — نفس السيرفر',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Colors.black54,
                    ),
              ),
              const Spacer(),
              ElevatedButton.icon(
                icon: const Icon(Icons.family_restroom),
                label: const Text('أنا ولي الأمر'),
                onPressed: () async {
                  await context.read<AppSession>().setRole('parent');
                  if (!context.mounted) return;
                  Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const ParentLoginScreen()),
                  );
                },
              ),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                icon: const Icon(Icons.child_care),
                label: const Text('أنا جهاز الطفل'),
                style: OutlinedButton.styleFrom(
                  minimumSize: const Size.fromHeight(48),
                  foregroundColor: AppTheme.primary,
                ),
                onPressed: () async {
                  await context.read<AppSession>().setRole('child');
                  if (!context.mounted) return;
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => const ChildHomeScreen(),
                    ),
                  );
                },
              ),
              const SizedBox(height: 40),
            ],
          ),
        ),
      ),
    );
  }
}
