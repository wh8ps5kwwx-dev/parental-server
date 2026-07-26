# MYRana Flutter

Full Flutter/Dart port of the MYRana parental control app. See **[README_AR.md](README_AR.md)** for the Arabic setup guide (including Family Controls on Mac/Xcode).

## Quick start

```bash
cd myrana_flutter
flutter pub get
flutter run
```

## Supported deployment

**Parent on iPhone or Android + child on Android = full monitoring** via REST + native Android enforcement.

**Child on iPhone:** full UI + link + Academy + real Screen Time path (`FamilyControls` / `ManagedSettings`). Requires Apple Developer Family Controls capability, device authorization, and `FamilyActivityPicker` selection. `DeviceActivityMonitor` extension source is complete (App Group + shield re-apply + daily threshold); the Xcode extension target must still be added on a Mac.

| Role / platform | Status |
|-----------------|--------|
| Parent (Android / iOS) | Full UI + REST control |
| Child (Android) | Full UI + Usage Stats + Accessibility blocking |
| Child (iOS) | Full UI + FamilyControls/ManagedSettings when entitled & authorized |

- **Android child:** Usage Stats, Accessibility, Foreground Service
- **iOS child:** `AuthorizationCenter.requestAuthorization(for: .individual)`, `ManagedSettingsStore` shields, policy sync from server
- **Web/Desktop:** not supported for child monitoring
- **GPS:** not implemented (intentional)

Original Kotlin project: `../MYRana/` (unchanged).

Server: `https://parental-server-4mms.onrender.com/` with `X-API-KEY: graduation-secret-key`
