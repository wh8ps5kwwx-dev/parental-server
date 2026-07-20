import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../academy/academy_question_bank.dart';
import '../../theme/app_theme.dart';

/// تحويل AcademyMenuActivity — تحديات حقيقية من بنك الأسئلة.
class ChildAcademyScreen extends StatefulWidget {
  const ChildAcademyScreen({super.key});

  @override
  State<ChildAcademyScreen> createState() => _ChildAcademyScreenState();
}

class _ChildAcademyScreenState extends State<ChildAcademyScreen> {
  static const _kStars = 'academy_stars';
  int _stars = 0;
  final Set<String> _owned = {};

  @override
  void initState() {
    super.initState();
    _loadStars();
  }

  Future<void> _loadStars() async {
    final p = await SharedPreferences.getInstance();
    setState(() => _stars = p.getInt(_kStars) ?? 0);
  }

  Future<void> _addStars(int n) async {
    final p = await SharedPreferences.getInstance();
    _stars += n;
    await p.setInt(_kStars, _stars);
    setState(() {});
  }

  Future<void> _buy(CityBuilding b) async {
    if (_owned.contains(b.name)) return;
    if (_stars < b.starCost) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('تحتاجين ${b.starCost} نجمة')),
      );
      return;
    }
    await _addStars(-b.starCost);
    setState(() => _owned.add(b.name));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('تم بناء ${b.name}!')),
    );
  }

  void _openChallenge(ChallengeType type) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => _ChallengeScreen(
          type: type,
          onFinished: (score) async {
            final gained = score * 2;
            await _addStars(gained);
            if (!mounted) return;
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text('أحسنت! +$gained نجمة')),
            );
          },
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('الأكاديمية')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Card(
            color: AppTheme.primary.withOpacity(0.08),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  const Icon(Icons.star, color: AppTheme.accent, size: 36),
                  const SizedBox(width: 12),
                  Text(
                    'نجومك: $_stars',
                    style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 8),
          const Text('التحديات', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          ...ChallengeType.values.map(
            (t) => Card(
              child: ListTile(
                title: Text(t.title, textAlign: TextAlign.right),
                trailing: const Icon(Icons.play_arrow),
                onTap: () => _openChallenge(t),
              ),
            ),
          ),
          const SizedBox(height: 12),
          const Text('مدينة التعلم', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          ...AcademyCityCatalog.buildings.map(
            (b) => Card(
              child: ListTile(
                title: Text(b.emojiLabel, textAlign: TextAlign.right),
                subtitle: Text(' التكلفة: ${b.starCost} نجمة', textAlign: TextAlign.right),
                trailing: _owned.contains(b.name)
                    ? const Icon(Icons.check_circle, color: Colors.green)
                    : TextButton(onPressed: () => _buy(b), child: const Text('ابنِ')),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ChallengeScreen extends StatefulWidget {
  const _ChallengeScreen({required this.type, required this.onFinished});
  final ChallengeType type;
  final Future<void> Function(int score) onFinished;

  @override
  State<_ChallengeScreen> createState() => _ChallengeScreenState();
}

class _ChallengeScreenState extends State<_ChallengeScreen> {
  late final List<AcademyQuestion> _qs;
  int _index = 0;
  int _score = 0;
  String? _picked;
  bool _done = false;

  @override
  void initState() {
    super.initState();
    _qs = List.of(AcademyQuestionBank.forType(widget.type))..shuffle();
  }

  void _answer(String option) {
    if (_picked != null) return;
    setState(() => _picked = option);
    if (option == _qs[_index].answer) _score++;
  }

  Future<void> _next() async {
    if (_index + 1 >= _qs.length) {
      setState(() => _done = true);
      await widget.onFinished(_score);
      return;
    }
    setState(() {
      _index++;
      _picked = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_done) {
      return Scaffold(
        appBar: AppBar(title: Text(widget.type.title)),
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text('النتيجة: $_score / ${_qs.length}', style: const TextStyle(fontSize: 22)),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('رجوع'),
              ),
            ],
          ),
        ),
      );
    }

    final q = _qs[_index];
    return Scaffold(
      appBar: AppBar(title: Text('${widget.type.title} (${_index + 1}/${_qs.length})')),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(q.question, textAlign: TextAlign.right, style: const TextStyle(fontSize: 20)),
            const SizedBox(height: 20),
            ...q.options.map((o) {
              Color? color;
              if (_picked != null) {
                if (o == q.answer) {
                  color = Colors.green.shade100;
                } else if (o == _picked) {
                  color = Colors.red.shade100;
                }
              }
              return Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: color ?? AppTheme.primary,
                    foregroundColor: color == null ? Colors.white : Colors.black87,
                  ),
                  onPressed: _picked == null ? () => _answer(o) : null,
                  child: Text(o),
                ),
              );
            }),
            const Spacer(),
            if (_picked != null)
              ElevatedButton(
                onPressed: _next,
                child: Text(_index + 1 >= _qs.length ? 'إنهاء' : 'التالي'),
              ),
          ],
        ),
      ),
    );
  }
}
