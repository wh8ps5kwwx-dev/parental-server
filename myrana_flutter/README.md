# MYRana Flutter

Full Flutter/Dart port of the MYRana parental control app with **Android product flavors** (same idea as Kotlin):

| Flavor | applicationId | App name | APK |
|--------|---------------|----------|-----|
| `parent` | `com.example.myrana.parent` | حماية الأطفال | `releases/app-parent-release.apk` |
| `child` | `com.example.myrana.child` | أكاديمية العباقرة | `releases/app-child-release.apk` |

See **[README_AR.md](README_AR.md)** for the Arabic setup guide.

## Quick start

```bash
cd myrana_flutter
flutter pub get
flutter run --flavor parent -t lib/main.dart
# or
flutter run --flavor child -t lib/main.dart
```

## Build both release APKs

```bash
flutter build apk --flavor parent --release -t lib/main.dart
flutter build apk --flavor child --release -t lib/main.dart
```

## Supported deployment

**Parent on iPhone or Android + child on Android = full monitoring** via REST + native Android enforcement (child flavor only: Accessibility / Usage Stats / Foreground / Boot).

**Child on iPhone:** full UI + link + Academy + real Screen Time path (`FamilyControls` / `ManagedSettings`).

- **GPS:** not implemented (intentional)
- Original Kotlin project: `../MYRana/` (unchanged)

Server: `https://parental-server-4mms.onrender.com/` with `X-API-KEY: graduation-secret-key`
