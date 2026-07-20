# MYRana Flutter

Full Flutter/Dart port of the MYRana parental control app. See **[README_AR.md](README_AR.md)** for Arabic setup guide.

## Quick start

```bash
cd myrana_flutter
flutter pub get
flutter run
```

- **Android:** full UI + REST + native enforcement (Usage Stats, Accessibility, Foreground Service)
- **iOS:** UI + REST only (MethodChannel stubs; no app blocking)
- **Web/Desktop:** not supported for child monitoring

Original Kotlin project: `../MYRana/` (unchanged).

Server: `https://parental-server-4mms.onrender.com/` with `X-API-KEY: graduation-secret-key`
