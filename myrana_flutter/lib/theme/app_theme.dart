import 'package:flutter/material.dart';

class AppTheme {
  static const Color primary = Color(0xFF1B6B5A);
  static const Color accent = Color(0xFFE8A838);
  static const Color bg = Color(0xFFF4F7F6);
  static const Color card = Colors.white;
  static const Color danger = Color(0xFFC0392B);

  static ThemeData light() {
    final base = ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: primary,
        primary: primary,
        secondary: accent,
        surface: bg,
      ),
      scaffoldBackgroundColor: bg,
      fontFamily: 'Segoe UI',
    );
    return base.copyWith(
      appBarTheme: const AppBarTheme(
        backgroundColor: primary,
        foregroundColor: Colors.white,
        elevation: 0,
        centerTitle: true,
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: primary,
          foregroundColor: Colors.white,
          minimumSize: const Size.fromHeight(48),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
      ),
      cardTheme: CardTheme(
        color: card,
        elevation: 1,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
      ),
    );
  }
}

