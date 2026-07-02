# -*- coding: utf-8 -*-
"""Code-accurate thesis sections aligned with parent_monitor_project/MYRana (2025/2026)."""

from __future__ import annotations


def stack_clarification(doc, heading, para, table):
    """Explicitly document the ACTUAL stack — not Flutter/XAMPP from older templates."""
    heading(doc, "1.14 Implementation Stack (Current Codebase)", 2)
    para(
        doc,
        "This graduation thesis documents the system as implemented in the repository "
        "parent_monitor_project/MYRana and deployed via parental-server-deploy on Render. "
        "The implementation does not use Flutter, Dart, XAMPP, PHP, or MySQL. Those technologies "
        "appeared only in an early academic template and were replaced during development by a "
        "native Android dual-flavor application (Kotlin), a Python Flask REST API, and SQLite "
        "persistence on both server and device.",
        indent=0.5,
    )
    table(doc, ["Layer", "Actual technology", "Repository path"], [
        ("Child APK", "Kotlin + Chaquopy Python 3.8 (academy_game.py)", "MYRana/app/src/child"),
        ("Parent APK", "Kotlin (parent flavor)", "MYRana/app/src/parent"),
        ("Shared logic", "Kotlin main source set", "MYRana/app/src/main/java/com/example/myrana"),
        ("REST API", "Flask + Gunicorn", "parental-server-deploy/server.py"),
        ("Server DB", "SQLite (parent_control.db)", "Render persistent disk / local"),
        ("Device DB", "Room ORM v3 (myrana_policy.db)", "data/local/AppDatabase.kt"),
        ("Email OTP", "Resend API (production) or Gmail SMTP", "EMAIL_SETUP.md"),
        ("Cloud host", "Render.com", "parental-server-4mms.onrender.com"),
        ("Build", "Gradle productFlavors: child | parent", "app/build.gradle"),
    ], title="Table 1.7 — Verified technology stack (current code)")
    para(
        doc,
        "Build configuration constants are compiled into each APK via build.gradle: "
        "compileSdk 33, minSdk 21, targetSdk 33, SERVER_ROOT_URL=https://parental-server-4mms.onrender.com/, "
        "SERVER_BASE_URL=.../api/, API_KEY=graduation-secret-key. Child applicationId is "
        "com.example.myrana.child (display name أكاديمية العباقرة); parent is "
        "com.example.myrana.parent (MY Rana - ولي الأمر).",
        indent=0.5,
    )


def updated_functional_requirements(doc, heading, para, bullets):
    heading(doc, "3.3.1 Functional Requirements (Code-Traced)", 3)
    reqs = [
        ("FR-01", "Guardian Gmail OTP via POST /send-email-code and POST /verify-email-code (ParentMainActivity)"),
        ("FR-02", "Child registration POST /register-child-device; CHILD-XXXXXXXX emailed via Resend (ChildRegistrationActivity)"),
        ("FR-03", "Linking: POST /send-link-code, POST /verify-child-device-code, POST /link-child; child polls GET /child-link-status every 3s"),
        ("FR-04", "Guardian role selection (mother/father/guardian) stored with child record"),
        ("FR-05", "Parent dashboard: GET /child-dashboard, online dot from POST /child-heartbeat"),
        ("FR-06", "Screen-time policy GET/POST /screen-time-policy; enforced by ScreenTimeEnforcer + ScreenTimeTracker"),
        ("FR-07", "Color-coded warnings: ScreenTimeWarningActivity (yellow/red), ScreenTimeLimitActivity (block)"),
        ("FR-08", "Remote commands: POST /send-command, child pulls GET /get-command (block_site, block_app, freeze_app, allow, apply_default_blocklist, request_usage)"),
        ("FR-09", "SafetyKeywordCatalog: 11 Arabic categories + bilingual terms; Accessibility monitors ALL user apps via MonitoredAppRegistry"),
        ("FR-10", "Alerts POST /add-alert; parent polls GET /alerts every 15 seconds in ParentMainActivity.onResume"),
        ("FR-11", "Default blocklist GET /blocklist/catalog (catalog.json); POST /apply-default-blocklist"),
        ("FR-12", "MediaLibraryScanner: images/video/audio filenames and metadata every 6h (max 8 alerts, 14-day lookback)"),
        ("FR-13", "Usage upload POST /upload-usage via UsageUploadHelper and OutboxRepository (Room pending_outbox)"),
        ("FR-14", "Weekly charts GET /weekly-chart rendered by SimpleBarChartView in ParentScreenTimeActivity"),
        ("FR-15", "Guardian settings GET/POST /guardian-settings; retention 7–90 days; email summary toggles"),
        ("FR-16", "Audit log GET /audit-log displayed in ParentSettingsActivity"),
        ("FR-17", "Permission status POST via PermissionStatusReporter → child_status.permissions_json on server"),
        ("FR-18", "Academy disguise: AcademyMenuActivity, Chaquopy academy_game.py, AcademyPythonBridge"),
        ("FR-19", "Background resilience: ParentSyncService (foreground), MonitoringWorker, BackgroundLoopWorker, BootReceiver"),
        ("FR-20", "Policy sync legacy API GET/POST /api/v1/devices/{device_id}/policy via PolicyRepository"),
    ]
    for fid, desc in reqs:
        para(doc, f"{fid}: {desc}.", indent=0.5)


def server_database_full(doc, heading, para, table):
    heading(doc, "4.2.1 Server SQLite Schema (server.py init_db)", 3)
    tables = [
        ("email_codes", "Guardian email OTP storage and verification flags"),
        ("child_devices", "CHILD code, device metadata, device_verify_code, linked flag"),
        ("guardians", "Registered guardian email addresses"),
        ("children", "Child profile: name, age, child_code, guardian_email, guardian_role, linked_at"),
        ("commands", "Command queue: block_site, block_app, freeze_app, allow, etc."),
        ("reports", "Child event reports from /add-report"),
        ("alerts", "Safety and block alerts from /add-alert"),
        ("device_policies", "blocked_hosts, blocked_packages, video_keywords JSON per device_id"),
        ("usage_daily", "Per-day per-package total_seconds aggregates"),
        ("schedules", "Scheduled freeze windows (add-schedule / active-schedules)"),
        ("screen_time_policies", "policy_json per child_code"),
        ("child_status", "last_seen_ms, permissions_json, permissions_ok for parent dashboard"),
        ("screen_time_events", "Warning/block events from child device"),
        ("audit_log", "Guardian actions with detail text"),
        ("guardian_settings", "settings_json: retention, email prefs, alert sound"),
        ("email_summary_sent", "Dedup keys for cron daily/weekly email summaries"),
    ]
    table(doc, ["Table", "Purpose"], tables, title="Table 4.2 — Server database tables (parent_control.db)")


def room_database_full(doc, heading, para, table):
    heading(doc, "4.2.2 Android Room Schema (AppDatabase v3)", 3)
    para(doc, "Local database file: myrana_policy.db on the child device.", indent=0.5)
    table(doc, ["Entity", "Table", "Purpose"], [
        ("BlockedSiteEntity", "blocked_sites", "Locally blocked hosts pending sync"),
        ("BlockedAppEntity", "blocked_apps", "Locally blocked packages pending sync"),
        ("SyncStateEntity", "sync_state", "last_pull_ms, last_push_ms, last_known_revision"),
        ("PendingOutboxEntity", "pending_outbox", "Offline queue for usage/alert payloads"),
        ("DailyAppUsageEntity", "daily_app_usage", "day + package_name + seconds_used"),
        ("ScreenTimeEventEntity", "screen_time_events", "Local screen-time warning/block log"),
    ], title="Table 4.3 — Room entities on child device")


def child_implementation_classes(doc, heading, para, bullets, table):
    heading(doc, "5.2.2 Child Application (Current Kotlin Modules)", 3)
    bullets(doc, [
        "Launcher: PermissionsLauncherActivity → ChildUiRouter routes to registration, permissions, or AcademyMenuActivity",
        "ChildRegistrationActivity — generates CHILD-{8 hex}, POST /register-child-device, polls /child-link-status",
        "ChildPermissionsActivity — ChildPermissionEvaluator gate for Usage Stats, Accessibility, notifications, battery, storage",
        "ParentSyncService — foreground dataSync; policy pull ~60s, EnforcementEngine tick ~2s, MediaLibraryScanner.scanIfDue",
        "ContentFilterAccessibilityService — MonitoredAppRegistry.shouldMonitorAccessibilityText for all user apps",
        "EnforcementEngine + ForegroundAppDetector — kill/freeze blocked packages, BlockWarningActivity overlay",
        "SafetyKeywordCatalog — 11 categories (self-harm, bullying, violence, drugs, gambling, alcohol, fraud, extremism, privacy, dark web, cybercrime)",
        "MediaLibraryScanner — MediaStore scan with StorageAccessHelper; keyword match → NetworkModule.addAlert",
        "AppUsageAlertHelper — excessive usage notifications integrated with EnforcementEngine",
        "ScreenTimeTracker / ScreenTimeEnforcer / ScreenTimeRepository — policy from ScreenTimePolicyStore",
        "AcademyMenuActivity, AcademyChallengeActivity, AcademyCityActivity, AcademyRewardsActivity, GameActivity — stealth UI",
        "AcademyPythonBridge + academy_game.py (Chaquopy) — Python 3.8 educational game engine (child flavor only)",
        "MonitoringWorker (15 min) + BackgroundLoopWorker (3 min) + BootReceiver — monitoring persistence",
        "PolicyRepository + BlocklistCatalogLoader + PolicyFilterCache — policy and catalog sync",
        "OutboxRepository + UsageUploadHelper — offline usage buffering",
        "PermissionStatusReporter — reports permission grants to server child_status table",
    ])
    table(doc, ["Category", "Count", "Source file"], [
        ("Keyword categories", "11", "SafetyKeywordCatalog.kt"),
        ("Bilingual keywords", "100+", "SafetyKeywordCatalog.kt + catalog.json"),
        ("Messaging apps tracked", "18 packages", "MonitoredAppRegistry.kt"),
        ("Browser packages", "10+", "MonitoredAppRegistry.kt"),
        ("Media scan interval", "6 hours", "MediaLibraryScanner.kt"),
        ("Link status poll", "3 seconds", "ChildRegistrationActivity.kt"),
    ], title="Table 5.6 — Child monitoring parameters (from source code)")


def parent_implementation_classes(doc, heading, para, bullets):
    heading(doc, "5.2.3 Parent Application (Current Kotlin Modules)", 3)
    bullets(doc, [
        "ParentMainActivity — 4-step wizard: email+role → OTP verify → child name/age → CHILD code + link OTP; btnAutoLink one-tap flow",
        "Control panel: block site/app, freeze, allow, schedule freeze, usage report, apply default blocklist, guardian message",
        "Alert polling every 15s via GuardianApi.fetchAlerts in onResume",
        "ParentScreenTimeActivity — SimpleBarChartView 7-day chart (GET /weekly-chart), progress bars, screen-time policy form",
        "ParentSettingsActivity — retention 7–90 days, daily/weekly email toggles, audit log viewer, manual send-email-summary",
        "ParentPermissionsFormatter — displays child_status.permissions_json summary on dashboard",
        "ParentSession — SharedPreferences for verified guardian email and active child_code",
        "GuardianApi + NetworkModule — all REST calls with X-API-KEY header",
    ])


def keyword_categories_table(doc, heading, table, para):
    heading(doc, "Appendix E: SafetyKeywordCatalog — Eleven Categories", 2)
    table(doc, ["#", "Category (AR)", "Risk domain", "Sample EN terms"], [
        ("1", "إيذاء النفس", "Self-harm", "suicide, self harm, kill myself"),
        ("2", "التنمر", "Bullying", "bullying, harassment"),
        ("3", "العنف", "Violence", "murder, weapon"),
        ("4", "المخدرات", "Drugs", "drugs, cocaine, heroin"),
        ("5", "المقامرة", "Gambling", "gambling, casino, betting"),
        ("6", "الكحول والتبغ", "Substances", "alcohol, vape, smoking"),
        ("7", "الاحتيال الإلكتروني", "Fraud", "phishing, hacking, malware"),
        ("8", "التطرف", "Extremism", "terrorism, extremism"),
        ("9", "الخصوصية الرقمية", "Privacy", "stranger chat, share password"),
        ("10", "دارك ويب", "Dark web", "dark web, tor browser, .onion"),
        ("11", "الجرائم الإلكترونية", "Cybercrime", "hacker, keylogger, botnet"),
    ], title="Table E.1 — SafetyKeywordCatalog categories (SafetyKeywordCatalog.kt)")
    para(
        doc,
        "Matching uses normalize() to strip Arabic diacritics and map leetspeak (0→o, 3→e, @→a). "
        "ContentFilterAccessibilityService calls matchIn() on accessibility text events from any "
        "non-system package approved by MonitoredAppRegistry.shouldMonitorAccessibilityText().",
        indent=0.5,
    )


def limitations_current_code(doc, heading, para, table):
    heading(doc, "5.7.1 Limitations Verified Against Source Code", 3)
    table(doc, ["Limitation", "Evidence in codebase"], [
        ("No Device Admin", "No DevicePolicyManager in manifest or Kotlin sources"),
        ("No NotificationListener", "Not declared in AndroidManifest.xml"),
        ("No GPS/geofencing", "No location permissions requested"),
        ("Accessibility UI fragility", "Documented in ContentFilterAccessibilityService comments"),
        ("No uninstall lock", "EnforcementEngine uses kill/home only, not device owner"),
        ("Render cold start", "NetworkModule timeouts; free-tier sleep documented in testing"),
        ("Media scan optional", "StorageAccessHelper gate; 6h interval in MediaLibraryScanner"),
        ("No SQLite encryption", "Room uses default SQLite without SQLCipher"),
        ("No FCM push", "Parent uses 15s HTTP polling, not FirebaseMessaging"),
        ("Single ParentSession child", "Multi-child on server; one active child in parent prefs"),
    ], title="Table 5.7 — Documented limitations (current build)")
