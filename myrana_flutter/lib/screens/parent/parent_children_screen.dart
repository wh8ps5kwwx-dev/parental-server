import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../data/api/guardian_api.dart';
import '../../data/models/api_models.dart';
import '../../session/app_session.dart';
import '../../widgets/common_widgets.dart';

class ParentChildrenScreen extends StatefulWidget {
  const ParentChildrenScreen({super.key});

  @override
  State<ParentChildrenScreen> createState() => _ParentChildrenScreenState();
}

class _ParentChildrenScreenState extends State<ParentChildrenScreen> {
  List<LinkedChild> _children = [];
  String _status = '';
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    final email = context.read<AppSession>().parentEmail;
    setState(() {
      _loading = true;
      _status = '';
    });
    final r = await context.read<GuardianApi>().fetchLinkedChildren(email);
    if (!mounted) return;
    setState(() {
      _loading = false;
      if (r is ApiChildrenList) {
        _children = r.children;
        _status = _children.isEmpty ? 'لا يوجد أطفال مرتبطون بعد' : '';
      } else if (r is ApiError) {
        _status = r.message;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final session = context.watch<AppSession>();
    return Scaffold(
      appBar: AppBar(
        title: const Text('الأطفال'),
        actions: [
          IconButton(onPressed: _load, icon: const Icon(Icons.refresh)),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                StatusBanner(message: _status, isError: _status.isNotEmpty && _children.isEmpty),
                ..._children.map(
                  (c) => Card(
                    child: ListTile(
                      title: Text(c.name, textAlign: TextAlign.right),
                      subtitle: Text(
                        '${c.childCode}\n${c.device}${c.online ? ' • متصل' : ''}',
                        textAlign: TextAlign.right,
                      ),
                      isThreeLine: true,
                      trailing: session.childCode == c.childCode
                          ? const Icon(Icons.check_circle, color: Colors.green)
                          : TextButton(
                              child: const Text('تفعيل'),
                              onPressed: () async {
                                final messenger = ScaffoldMessenger.of(context);
                                await session.setActiveChild(
                                  code: c.childCode,
                                  name: c.name,
                                );
                                if (!mounted) return;
                                messenger.showSnackBar(
                                  SnackBar(content: Text('تم تفعيل ${c.name}')),
                                );
                                setState(() {});
                              },
                            ),
                    ),
                  ),
                ),
              ],
            ),
    );
  }
}
