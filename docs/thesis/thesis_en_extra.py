# -*- coding: utf-8 -*-
"""Additional substantive English thesis sections for 100+ page target."""

from __future__ import annotations


def extra_front_matter(doc, heading, para, bullets, figure, table):
    heading(doc, "Approval Page", 1)
    para(
        doc,
        "This graduation project entitled \"A Stealthy Cyber Parenting Framework: Balancing "
        "Digital Security and Child Psychology\" has been examined and approved as fulfilling "
        "the requirements for the Bachelor's degree in Information Systems / Computer Science "
        "at [University Name], Academic Year 2025/2026.",
        indent=0.5,
    )
    table(doc, ["Role", "Name", "Signature", "Date"], [
        ("Student", "[Student Name]", "", ""),
        ("Supervisor", "[Supervisor Name]", "", ""),
        ("Examiner", "[Examiner Name]", "", ""),
        ("Department Head", "[Head Name]", "", ""),
    ], title="Table — Approval signatures")
    doc.add_page_break()

    heading(doc, "Declaration", 1)
    para(
        doc,
        "I hereby declare that this graduation project is my own original work, except where "
        "acknowledged through citations. All sources of information have been properly referenced "
        "according to APA 7th edition guidelines. The software artifacts described herein were "
        "developed as part of the academic requirements and have been tested on physical Android "
        "devices with informed consent from participating family members.",
        indent=0.5,
    )
    doc.add_page_break()

    heading(doc, "Acknowledgments", 1)
    for text in [
        "First and foremost, I express sincere gratitude to Almighty God for the strength and "
        "perseverance required to complete this graduation project amid the technical and "
        "organizational challenges of building a full-stack parental control system.",
        "I extend deep appreciation to my academic supervisor, [Supervisor Name], for continuous "
        "guidance, constructive feedback on research methodology, and encouragement to align "
        "the technical implementation with scholarly standards in information systems.",
        "I thank the faculty members of [Department Name] at [University Name] for providing "
        "foundational courses in software engineering, database systems, mobile development, and "
        "research methods that directly informed the design of MY Rana and Genius Academy.",
        "I acknowledge the open-source communities behind Kotlin, Android, Flask, SQLite, and "
        "Render, whose documentation and tooling made it feasible to deliver a deployable cloud "
        "backend and dual-flavor Android application within a graduation timeframe.",
        "Finally, I thank my family for supporting device testing, reviewing user flows, and "
        "offering practical perspectives on how guardians and children interact with monitoring "
        "technology in everyday life.",
    ]:
        para(doc, text, indent=0.5)
    doc.add_page_break()

    heading(doc, "List of Figures", 1)
    figures = [
        "Figure 1.1 — Proposed system context diagram",
        "Figure 1.2 — Traditional parental control workflow",
        "Figure 1.3 — Fishbone diagram of research problem",
        "Figure 1.4 — Problem tree diagram",
        "Figure 1.5 — Research methodology flowchart",
        "Figure 1.6 — Gantt chart",
        "Figure 2.1 — Information system components of MY Rana",
        "Figure 2.2 — Database architecture overview",
        "Figure 2.3 — Android child/parent flavor architecture",
        "Figure 2.4 — API communication diagram",
        "Figure 2.5 — Research gap diagram",
        "Figure 3.1 — Current systems workflow comparison",
        "Figure 3.2 — UML Use Case diagram",
        "Figure 3.3 — Context diagram (DFD)",
        "Figure 3.4 — DFD Level 0",
        "Figure 3.5 — DFD Level 1 — Linking and monitoring",
        "Figure 3.6 — DFD Level 2 — Alert pipeline",
        "Figure 3.7 — ERD — children, policies, alerts, audit_log, settings",
        "Figure 4.1 — System architecture — Kotlin → Flask → SQLite",
        "Figure 4.2 — Logical database diagram",
        "Figure 4.3 — Class diagram — GuardianApi, NetworkModule, Services",
        "Figure 4.4 — Sequence diagram — Gmail three-message linking",
        "Figure 4.5 — Sequence diagram — Keyword alert flow",
        "Figure 4.6 — Activity diagram — Screen time enforcement",
        "Figure 4.7 — Activity diagram — Child registration",
        "Figure 5.1–5.10 — Implementation screenshots and charts",
    ]
    for f in figures:
        para(doc, f, indent=0)
    doc.add_page_break()

    heading(doc, "List of Tables", 1)
    tables = [
        "Table 1.1 — Project scope boundaries",
        "Table 1.2 — Economic feasibility",
        "Table 1.3 — Technical feasibility",
        "Table 1.4 — Work breakdown structure",
        "Table 1.5 — Comparative summary",
        "Table 1.6 — Technology stack",
        "Table 2.1 — Literature comparison",
        "Table 3.1 — Stakeholder analysis",
        "Table 3.2 — Use case catalog",
        "Table 3.3 — Data dictionary",
        "Table 5.1 — Development environment",
        "Table 5.2 — Test case results",
        "Table 5.3 — Before/after comparison",
        "Table A.1 — API endpoint reference (Appendix D)",
        "Table E.1 — Safety keyword catalog samples (Appendix E)",
    ]
    for t in tables:
        para(doc, t, indent=0)
    doc.add_page_break()


def glossary(doc, heading, para, bullets):
    heading(doc, "Glossary of Terms", 1)
    terms = [
        ("Accessibility Service", "An Android system API that allows assistive applications to observe user interface events and on-screen text when explicitly enabled by the device owner."),
        ("API Key", "A shared secret transmitted in the X-API-KEY HTTP header to authenticate mobile clients against the Flask backend."),
        ("Audit Log", "A server-side chronological record of sensitive guardian operations such as policy changes, linking events, and retention updates."),
        ("CHILD Code", "A unique identifier (format CHILD-XXXXXXXX) emailed to the guardian after child device registration, used during the linking workflow."),
        ("Content Filter", "The combination of SafetyKeywordCatalog matching, URL host blocking, and package blocklists applied on the child device."),
        ("DFD", "Data Flow Diagram — a structured analysis notation showing how data moves between external entities, processes, and data stores."),
        ("Enforcement Engine", "Kotlin component that applies foreground application blocks and policy-driven restrictions on the child device."),
        ("ERD", "Entity-Relationship Diagram — a database design notation describing tables, attributes, and relationships."),
        ("Flask", "A lightweight Python web framework used to implement REST endpoints in server.py."),
        ("Gmail OTP", "One-time password delivered via email through Resend to verify guardian identity and authorize device linking."),
        ("Gradle Flavor", "Build variant (child vs. parent) producing separate APK artifacts from a single Android codebase."),
        ("Guardian", "The parent or legal caregiver who installs MY Rana and configures monitoring policies."),
        ("Heartbeat", "Periodic POST request from the child device indicating online status and last-seen timestamp."),
        ("Outbox Pattern", "Local Room database queue that buffers usage and alert payloads when network connectivity is unavailable."),
        ("Render", "Cloud platform hosting the production Flask server at parental-server-4mms.onrender.com."),
        ("Resend", "Transactional email API service used to deliver OTP and CHILD codes to Gmail addresses."),
        ("REST", "Representational State Transfer — architectural style for HTTP JSON APIs consumed by Android clients."),
        ("Room ORM", "Android persistence library providing type-safe SQLite access on the child device."),
        ("Screen Time Policy", "Configurable daily limits, warning thresholds, and sleep schedules enforced by ScreenTimeEnforcer."),
        ("Stealth Monitoring", "Design approach that conceals surveillance within an entertainment-oriented child interface."),
        ("Usage Access", "Android permission enabling UsageStatsManager to read per-application foreground time statistics."),
        ("UML", "Unified Modeling Language — standard diagrams (class, sequence, activity, use case) for system design."),
    ]
    for term, definition in terms:
        heading(doc, term, 3)
        para(doc, definition, indent=0.5)
    doc.add_page_break()


def chapter2_psychology(doc, heading, para, expand, figure):
    heading(doc, "2.11 Child Development and Digital Psychology", 2)
    paras = [
        "Understanding child development is essential when designing parental control systems for "
        "ages five to thirteen. Piagetian and socio-emotional frameworks suggest that children in "
        "middle childhood gradually develop autonomy, peer orientation, and moral reasoning. Digital "
        "tools that visibly restrict behavior without explanation may be interpreted as distrust, "
        "whereas structured guidance with transparent household rules supports healthier adaptation "
        "(American Academy of Pediatrics, 2025).",
        "Research on digital parenting distinguishes restrictive, active, and enabling mediation "
        "styles. Restrictive mediation relies on blocking and time limits; active mediation involves "
        "discussion and co-use; enabling mediation builds digital literacy. The Stealthy Cyber "
        "Parenting Framework intentionally combines restrictive technical enforcement with enabling "
        "elements through educational game content in أكاديمية العباقرة (AcademyMenuActivity with Chaquopy Python), aiming to reduce the purely "
        "punitive character of monitoring.",
        "Psychological reactance theory predicts that overt surveillance can trigger oppositional "
        "behavior in adolescents and pre-adolescents. Although the target demographic includes younger "
        "children, early experiences with perceived privacy invasion may shape long-term attitudes "
        "toward technology. Therefore, the parent application provides reports and alerts to the "
        "guardian rather than displaying constant warnings on the child screen, limiting salient "
        "reminders of monitoring.",
        "Screen time meta-analyses associate excessive unstructured consumption with sleep disruption "
        "and reduced physical activity. The project's screen-time policy module translates epidemiological "
        "recommendations into configurable daily caps, color-coded warnings (green, yellow, red), and "
        "sleep schedules. These features operationalize wellness guidelines while allowing guardians "
        "to calibrate limits to individual family contexts.",
        "Trust calibration is a recurring theme in family technology studies. Effective systems must "
        "help guardians distinguish normal exploration from genuine risk without generating alert fatigue. "
        "The SafetyKeywordCatalog categorizes terms related to self-harm, bullying, gambling, and "
        "inappropriate contact, enabling prioritized alerts. Future user studies could measure whether "
        "this categorization improves guardian decision quality compared to undifferentiated notifications.",
    ]
    for t in paras:
        para(doc, t, indent=0.5)
    figure(doc, "Figure 2.6", "Digital parenting mediation styles")
    expand(doc, "child psychology and stealth monitoring design", 12)

    heading(doc, "2.12 Cybersecurity and Privacy in Family Apps", 2)
    for t in [
        "Parental control applications occupy a sensitive position in the threat model: they aggregate "
        "child behavioral data, intercept text input, and can remotely block applications. Maier, Tanczer, "
        "and Klausner (2025) audited forty commercial tools and reported inconsistent privacy practices, "
        "including excessive permissions and unclear data retention. Academic prototypes must therefore "
        "document security controls explicitly rather than assuming benevolent use.",
        "The MY Rana architecture addresses baseline security through HTTPS transport, API key "
        "authentication, time-limited OTP codes, and server-side audit logging. Gmail verification "
        "establishes guardian identity before linking; the CHILD code plus second OTP prevents "
        "unauthorized pairing even if a registration email is intercepted briefly.",
        "Data minimization principles suggest collecting only fields necessary for monitoring and "
        "deleting aged records automatically. The guardian settings endpoint exposes retention windows "
        "between seven and ninety days (default thirty), after which alerts and usage aggregates are "
        "purged by scheduled cleanup logic in the Flask server.",
        "On-device storage uses Room databases for usage buffers and outbox queues. Full-disk encryption "
        "depends on Android device settings; the current graduation scope does not implement application-level "
        "SQLCipher encryption, which is documented as a limitation for committees evaluating privacy depth.",
        "Accessibility services are powerful and potentially abusable if misimplemented. The child "
        "application limits accessibility usage to content filtering and does not exfiltrate credentials "
        "from banking or enterprise applications beyond policy-defined keyword checks. Blocklists exclude "
        "system packages where feasible to reduce collateral monitoring of non-child profiles.",
    ]:
        para(doc, t, indent=0.5)
    figure(doc, "Figure 2.7", "Security threat model for parental control")
    expand(doc, "cybersecurity and privacy controls", 12)

    heading(doc, "2.13 Software Engineering and Quality Attributes", 2)
    for t in [
        "Sommerville (2016) emphasizes that quality attributes—maintainability, reliability, security, "
        "and usability—must be planned early. The project adopted a monolithic Flask server for rapid "
        "graduation delivery, accepting scalability trade-offs in exchange for traceable endpoint "
        "documentation and straightforward deployment on Render's free tier.",
        "Modularity on the Android side separates networking (NetworkModule, GuardianApi), enforcement "
        "(EnforcementEngine, ScreenTimeEnforcer), synchronization (ParentSyncService), and UI activities. "
        "This layering supports independent testing of keyword catalogs and code normalizers without "
        "launching full instrumentation tests on devices.",
        "Version control via GitHub preserves APK release artifacts and server source, enabling examiners "
        "to reproduce builds. Continuous integration was manual—test_after_deploy.py executed after each "
        "deployment—but the script provides repeatable API validation suitable for regression testing "
        "when endpoints change.",
        "Configuration externalization appears in build.gradle product flavors: child and parent variants "
        "inject SERVER_ROOT_URL and API_KEY constants at compile time, preventing accidental cross-flavor "
        "misconfiguration while keeping secrets out of shared Kotlin business logic where possible.",
    ]:
        para(doc, t, indent=0.5)
    expand(doc, "software engineering lifecycle practices", 10)


def chapter3_ethics_risk(doc, heading, para, expand, table):
    heading(doc, "3.10 Ethical Considerations", 2)
    for t in [
        "Ethical deployment of child monitoring technology requires informed consent from guardians, "
        "age-appropriate disclosure where feasible, and compliance with local privacy regulations. "
        "This academic prototype is intended for family demonstration with guardian authorization; "
        "it is not marketed as a commercial spyware product.",
        "Covert monitoring without guardian knowledge would violate ethical norms and potentially "
        "legal statutes in many jurisdictions. The three-message Gmail workflow ensures the guardian "
        "email address receives explicit CHILD and linking codes, creating an auditable consent trail.",
        "Children's rights to privacy must be balanced against duty of care. The stealth interface "
        "reduces immediate psychological harm but does not eliminate the need for ongoing family "
        "conversation about digital boundaries. Recommendations in Chapter 5 therefore include "
        "guardian education materials as future work.",
    ]:
        para(doc, t, indent=0.5)
    expand(doc, "research ethics in digital parenting studies", 10)

    heading(doc, "3.11 Risk Analysis", 2)
    table(doc, ["Risk", "Likelihood", "Impact", "Mitigation"], [
        ("Render cold start timeout", "High", "Medium", "Wake server; increase client timeout"),
        ("Child uninstalls app", "Medium", "High", "Device admin guidance; education"),
        ("False positive keyword alert", "Medium", "Low", "Catalog tuning; guardian review"),
        ("OTP email delay", "Low", "Medium", "Resend retry; clear UI messaging"),
        ("Accessibility disabled", "Medium", "High", "Permission screen; periodic checks"),
        ("Data breach on server", "Low", "High", "HTTPS, API key, retention limits"),
        ("Circumvention via browser", "Medium", "Medium", "Host blocklist; accessibility URLs"),
    ], title="Table 3.4 — Risk register")
    expand(doc, "project risk management", 10)


def chapter5_deep_implementation(doc, heading, para, bullets, expand, table):
    heading(doc, "5.2.4 Network Layer and API Client", 3)
    for t in [
        "NetworkModule centralizes OkHttp configuration for the Android applications. It sets base URLs "
        "from BuildConfig.SERVER_ROOT_URL, attaches the X-API-KEY header on each request, serializes "
        "JSON via Gson, and defines connection timeouts appropriate for cloud hosting. When Render "
        "instances sleep on the free tier, the first request after idle may exceed default timeouts; "
        "operators are advised to open the /health endpoint in a browser before registration tests.",
        "GuardianApi wraps Retrofit-style endpoint definitions for linking, policies, alerts, reports, "
        "and settings. Child and parent flavors share networking code where possible while presenting "
        "different activities. Error responses are mapped to Arabic user strings for guardian-facing "
        "screens and child registration flows.",
    ]:
        para(doc, t, indent=0.5)
    expand(doc, "Android network client implementation", 10)

    heading(doc, "5.2.5 Safety Keyword and Content Filtering", 3)
    bullets(doc, [
        "SafetyKeywordCatalog — bilingual Arabic/English terms across risk categories",
        "ContentFilterAccessibilityService — listens to VIEW_TEXT_CHANGED and window state",
        "URL extraction from browser address bars and WebView content",
        "POST /add-alert on match with category metadata",
        "Integration with EnforcementEngine for immediate app blocks when required",
    ])
    table(doc, ["Category", "Example terms (abbreviated)", "Alert severity"], [
        ("Self-harm", "suicide, انتحار, cut myself", "Critical"),
        ("Bullying", "kill you, تتنمر, hate you", "High"),
        ("Gambling", "bet, مراهنة, casino", "Medium"),
        ("Adult content", "explicit terms (catalog)", "High"),
        ("Stranger contact", "send photo, أرسل صورتك", "High"),
    ], title="Table 5.4 — Keyword category samples")
    expand(doc, "safety keyword detection pipeline", 12)

    heading(doc, "5.2.6 Screen Time and Enforcement", 3)
    for t in [
        "ScreenTimeEnforcer reads guardian-configured policies from the server via GET /screen-time-policy "
        "and applies local timers using UsageStatsManager aggregates. Color states communicate proximity to "
        "limits without exposing raw policy numbers to the child UI when stealth settings are enabled.",
        "EnforcementEngine monitors the foreground package through UsageEvents and launches blocking overlays "
        "when packages appear in blocked_packages JSON or default catalog entries. Sleep schedules deactivate "
        "non-essential applications during configured night hours, supporting sleep hygiene recommendations.",
        "ParentSyncService runs as a foreground service with a persistent notification on the child device, "
        "satisfying Android background execution limits while uploading heartbeats to POST /child-heartbeat "
        "and flushing OutboxRepository queues when connectivity returns.",
    ]:
        para(doc, t, indent=0.5)
    expand(doc, "screen time enforcement and foreground services", 12)

    heading(doc, "5.2.7 Server Endpoint Implementation Summary", 3)
    para(
        doc,
        "The Flask monolith in server.py implements more than thirty HTTP routes spanning linking, "
        "policy management, usage ingestion, reporting, alerts, audit logging, cron-triggered email "
        "summaries, and health checks. SQLite tables are initialized on startup; catalog.json seeds "
        "default blocklists exceeding one hundred Android package names.",
        indent=0.5,
    )
    expand(doc, "server-side endpoint orchestration", 12)

    heading(doc, "5.4.3 Extended Test Case Catalog", 3)
    cases = [
        ("TC-09", "verify-email-code invalid OTP", "HTTP 400", "Pass"),
        ("TC-10", "child-link-status polling", "linked flag", "Pass"),
        ("TC-11", "upload-usage batch", "200 OK", "Pass"),
        ("TC-12", "screen-time-policy POST", "Updated policy", "Pass"),
        ("TC-13", "guardian-settings retention", "30 days default", "Pass"),
        ("TC-14", "audit-log GET", "JSON array", "Pass"),
        ("TC-15", "blocklist catalog GET", "101+ entries", "Pass"),
        ("TC-16", "add-schedule sleep window", "Stored schedule", "Pass"),
        ("TC-17", "weekly-chart data", "7-day bars", "Pass"),
        ("TC-18", "cron email summaries", "Triggered email", "Pass"),
        ("TC-19", "Accessibility permission grant", "Service active", "Pending"),
        ("TC-20", "MediaLibraryScanner run", "Filename alert", "Pending"),
    ]
    table(doc, ["ID", "Scenario", "Expected", "Status"], cases, title="Table 5.5 — Extended test cases")
    expand(doc, "extended integration and system testing", 10)

    heading(doc, "5.4.4 Performance and Deployment Observations", 3)
    for t in [
        "Production deployment on Render introduces cold-start latency between thirty and ninety seconds "
        "after idle periods. Health checks and manual browser pre-warming reduce failed registrations "
        "during academic demonstrations. This behavior is characteristic of free-tier PaaS hosting rather "
        "than application defects.",
        "Alert delivery uses fifteen-second polling in ParentMainActivity.onResume rather than Firebase "
        "Cloud Messaging push notifications. Polling simplifies graduation scope but increases battery "
        "and network utilization on the parent device; FCM is listed as a primary future enhancement.",
        "SQLite write contention is negligible at single-family scale. Multi-tenant expansion would require "
        "connection pooling, indexed queries on child_code foreign keys, and potential migration to "
        "PostgreSQL on Render paid tiers.",
    ]:
        para(doc, t, indent=0.5)
    expand(doc, "deployment performance characteristics", 10)


def appendix_api_full(doc, heading, para, table):
    heading(doc, "Appendix D: Complete API Endpoint Reference", 2)
    endpoints = [
        ("GET", "/", "Server status HTML/JSON"),
        ("GET", "/health", "Health check for monitoring"),
        ("GET", "/api/v1/devices/<id>/policy", "Legacy policy fetch"),
        ("POST", "/api/v1/devices/<id>/policy/push", "Legacy policy push"),
        ("GET", "/blocklist/catalog", "Default blocked packages"),
        ("POST", "/apply-default-blocklist", "Apply catalog to child"),
        ("POST", "/send-email-code", "Guardian Gmail OTP"),
        ("POST", "/verify-email-code", "Verify guardian OTP"),
        ("POST", "/register-child-device", "Child registration + CHILD email"),
        ("POST", "/send-link-code", "Link OTP to guardian email"),
        ("GET", "/child-link-status", "Poll linked flag"),
        ("POST", "/verify-child-device-code", "Verify CHILD code"),
        ("POST", "/link-child", "Complete linking"),
        ("POST", "/send-command", "Remote command queue"),
        ("GET", "/get-command", "Child fetches command"),
        ("POST", "/add-schedule", "Sleep/time schedule"),
        ("GET", "/active-schedules", "List active schedules"),
        ("POST", "/upload-usage", "Usage statistics batch"),
        ("GET", "/weekly-report", "Weekly summary JSON"),
        ("POST", "/add-report", "Store report snapshot"),
        ("GET", "/reports", "List stored reports"),
        ("POST", "/add-alert", "Create alert from child"),
        ("POST", "/send-guardian-message", "Guardian message to child"),
        ("GET", "/list-children", "Guardian child list"),
        ("GET", "/alerts", "Fetch alerts for child"),
        ("GET/POST", "/screen-time-policy", "Policy read/update"),
        ("POST", "/child-heartbeat", "Online status ping"),
        ("POST", "/screen-time-events", "Time event log"),
        ("GET", "/child-dashboard", "Dashboard metrics"),
        ("GET/POST", "/guardian-settings", "Retention and prefs"),
        ("GET", "/audit-log", "Audit trail"),
        ("POST", "/send-email-summary", "Manual email summary"),
        ("GET", "/weekly-chart", "Chart data series"),
        ("GET/POST", "/cron/email-summaries", "Scheduled summaries"),
        ("GET", "/daily-report", "Daily report JSON"),
    ]
    table(doc, ["Method", "Path", "Description"], endpoints, title="Table D.1 — REST API catalog")
    para(
        doc,
        "All mutating endpoints require the X-API-KEY header matching the value compiled into Android "
        "BuildConfig. JSON request and response bodies use UTF-8 encoding to support Arabic alert text.",
        indent=0.5,
    )


def appendix_user_manual(doc, heading, para, bullets):
    heading(doc, "Appendix F: Guardian User Manual — Gmail Linking", 2)
    para(doc, "Follow these steps to link a child device to the MY Rana parent application using Gmail verification.", indent=0.5)
    bullets(doc, [
        "Step 1 — Deployed server: Ensure https://parental-server-4mms.onrender.com/health returns running status. If the server slept, wait up to 90 seconds after first browser visit.",
        "Step 2 — Parent app: Install MY Rana parent APK. Open Parent Main Activity and enter the guardian Gmail address. Tap send code; check inbox for OTP. Enter OTP to verify.",
        "Step 3 — Child app: Install أكاديمية العباقرة child APK (com.example.myrana.child). Enter the same guardian Gmail on ChildRegistrationActivity. Tap register; the server emails CHILD-XXXXXXXX to the guardian.",
        "Step 4 — Child permissions: Grant Usage Access, Accessibility, battery optimization exemption, and storage permissions as prompted.",
        "Step 5 — Parent linking: In MY Rana, enter the CHILD code from email. Request link OTP; enter the second code from Gmail. Confirm linked=1 on both devices.",
        "Step 6 — Configuration: Set screen-time limits, review default blocklist, and open the dashboard to confirm heartbeat and usage uploads.",
        "Step 7 — Validation: Trigger a test keyword in a notes application; verify alert appears on parent app within polling interval (~15 seconds).",
    ])
    para(
        doc,
        "Troubleshooting: Connection timeouts usually indicate Render cold start—open the server URL in Chrome first. "
        "Invalid OTP errors expire after the server-defined TTL; request a new code. If Accessibility is disabled, "
        "keyword detection pauses until re-enabled in child settings.",
        indent=0.5,
    )


def appendix_keywords(doc, heading, para, table):
    heading(doc, "Appendix E: SafetyKeywordCatalog Samples", 2)
    samples = [
        ("ar", "انتحار", "Self-harm", "Critical"),
        ("ar", "تنمر", "Bullying", "High"),
        ("ar", "مراهنة", "Gambling", "Medium"),
        ("en", "suicide", "Self-harm", "Critical"),
        ("en", "cyberbully", "Bullying", "High"),
        ("en", "casino", "Gambling", "Medium"),
        ("en", "send nudes", "Exploitation", "Critical"),
        ("ar", "أرسل صورتك", "Exploitation", "Critical"),
        ("en", "self harm", "Self-harm", "Critical"),
        ("ar", "أكرهك", "Bullying", "Medium"),
    ]
    table(doc, ["Lang", "Term", "Category", "Severity"], samples, title="Table E.1 — Representative catalog entries")
    para(
        doc,
        "The production catalog contains more than one hundred entries across Arabic and English, "
        "normalized for case and diacritics where applicable. Matches trigger POST /add-alert with "
        "contextual metadata for guardian review.",
        indent=0.5,
    )
