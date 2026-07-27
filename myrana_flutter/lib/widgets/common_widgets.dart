import 'package:flutter/material.dart';

import '../theme/app_theme.dart';

class StatusBanner extends StatelessWidget {
  const StatusBanner({super.key, required this.message, this.isError = false});
  final String message;
  final bool isError;

  @override
  Widget build(BuildContext context) {
    if (message.isEmpty) return const SizedBox.shrink();
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isError ? AppTheme.danger.withOpacity(0.12) : AppTheme.primary.withOpacity(0.1),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(
          color: isError ? AppTheme.danger : AppTheme.primary.withOpacity(0.4),
        ),
      ),
      child: Text(
        message,
        textAlign: TextAlign.right,
        style: TextStyle(color: isError ? AppTheme.danger : AppTheme.primary),
      ),
    );
  }
}

class HubTile extends StatelessWidget {
  const HubTile({
    super.key,
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: AppTheme.primary.withOpacity(0.12),
          child: Icon(icon, color: AppTheme.primary),
        ),
        title: Text(title, textAlign: TextAlign.right),
        subtitle: Text(subtitle, textAlign: TextAlign.right),
        trailing: const Icon(Icons.chevron_left),
        onTap: onTap,
      ),
    );
  }
}

