# MYRana — Smart Parental Control System

[![Live Server](https://img.shields.io/badge/server-live-success)](https://parental-server-4mms.onrender.com/health)
[![Android](https://img.shields.io/badge/platform-Android-green)](MYRana/)
[![Python](https://img.shields.io/badge/backend-Flask-blue)](server.py)
[![License](https://img.shields.io/badge/project-graduation-lightgrey)](#)

**MYRana** is a graduation project: a parental control platform with a **disguised child experience** (Academy game) and a **guardian dashboard** for monitoring, blocking apps, screen time, and alerts — across multiple child devices from one parent phone.

> **Live demo:** [parental-server-4mms.onrender.com](https://parental-server-4mms.onrender.com)  
> **Author portfolio project** — Android + Flask + SQLite + Render

---

## Project idea

Children use an educational game (**Academy of Geniuses**). In the background, the system monitors app usage, enforces blocks, and sends reports to the guardian — without exposing a typical “parental control” UI to the child.

**Guardian** controls from a separate APK: link children, block apps (e.g. Granny), schedule sleep time, view usage charts, and send messages to multiple devices.

---

## Features

| Area | Capabilities |
|------|----------------|
| **Linking** | Gmail OTP + `CHILD-XXXXXXXX` device code + 6-digit link code |
| **Multi-child** | One parent account → many child devices |
| **App blocking** | Block/freeze by package name or app name (Granny, TikTok…) |
| **Background monitoring** | Works when academy is closed (Usage Access + Accessibility) |
| **Screen time** | Daily limits, sleep schedule, usage reports |
| **Alerts** | Content filter, usage alerts, guardian messages |
| **Bulk commands** | Apply block/freeze to all linked children at once |

---

## Technologies used

| Layer | Stack |
|-------|--------|
| **Mobile (child + parent)** | Kotlin, Android SDK, Gradle flavors, Chaquopy (Python academy) |
| **Backend** | Python 3, Flask, SQLite |
| **Deployment** | [Render](https://render.com), Gunicorn |
| **Email** | Gmail SMTP / Resend API |
| **Networking** | OkHttp, Retrofit, REST JSON |
| **Optional** | Kivy Python parent app, blocklist catalog JSON |

---

## Repository structure

```
parental-server/
├── server.py              # Flask API (Render entry point)
├── requirements.txt
├── Procfile
├── MYRana/releases/       # APK downloads (latest — use these links)
│   ├── app-child-debug.apk
│   └── app-parent-debug.apk
├── MYRana/                # Android project (child + parent flavors)
├── blocklists/            # App/site block catalog
├── mother-app/            # Python Kivy guardian app (optional)
├── child-academy/         # Python game logic
├── common/                # Shared Python helpers
└── docs/
    └── LINKING.md         # Linking troubleshooting
```

---

## Installation & run

### 1. Clone

```bash
git clone https://github.com/wh8ps5kwwx-dev/parental-server.git
cd parental-server
```

### 2. Server (local)

```bash
pip install -r requirements.txt
cp .env.example .env   # edit secrets locally — never commit .env
python server.py
# http://127.0.0.1:5000/health
```

### 3. Android APK (download)

| App | Link |
|-----|------|
| **Child** | [app-child-debug.apk](https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/MYRana/releases/app-child-debug.apk) |
| **Parent** | [app-parent-debug.apk](https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/MYRana/releases/app-parent-debug.apk) |

> Use `github.com/.../raw/...` links — **not** `media.githubusercontent.com`.

### 4. Build from source

```powershell
cd MYRana
.\gradlew assembleChildDebug assembleParentDebug
```

Output: `MYRana/app/build/outputs/apk/`

---

## API endpoints (main)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Server + DB stats |
| POST | `/send-email-code` | OTP to guardian Gmail |
| POST | `/verify-email-code` | Verify guardian email |
| POST | `/register-child-device` | Register child → `CHILD-XXX` |
| GET | `/child-link-status?child_code=` | Check if child exists / linked |
| POST | `/send-link-code` | Send 6-digit link OTP |
| POST | `/add-child` | Link child to guardian |
| GET | `/list-children?parent_email=` | All linked children |
| POST | `/send-command` | Block/freeze/allow app |
| GET | `/get-command?child_code=` | Poll commands (child) |
| POST | `/child-heartbeat` | Online status + permissions |

Full details: [MYRana/LINKING_API.md](MYRana/LINKING_API.md) · [docs/LINKING.md](docs/LINKING.md)

**Auth header:** `X-API-KEY: <your API_KEY env var>`

---

## Linking flow (2 phones)

1. **Child phone:** Install child APK → enter parent Gmail → **Register Device** → copy `CHILD-XXXXXXXX`
2. **Parent phone:** Install parent APK → verify Gmail → paste child code → **Send link code**
3. **Gmail:** Enter 6-digit link code → complete link → enter child name
4. **Child phone:** Tap **Check link** → grant **Usage Access** + **Accessibility** → Academy opens
5. **Test:** Parent blocks `Granny` → open Granny on child device → blocked

See [MYRana/تشغيل_جوالين.md](MYRana/%D8%AA%D8%B4%D8%BA%D9%8A%D9%84_%D8%AC%D9%88%D8%A7%D9%84%D9%8A%D9%86.md) (Arabic step-by-step).

---

## Screenshots

> Add screenshots to `docs/screenshots/` for portfolio display.

| Screen | Description |
|--------|-------------|
| Child — registration | `CHILD-XXXXXXXX` wait screen |
| Child — permissions | Usage + Accessibility setup |
| Child — academy | Academy of Geniuses menu |
| Parent — dashboard | Linked children + block controls |
| Parent — block test | Granny block + alert |

<!-- Example after adding images:
![Academy](docs/screenshots/academy.png)
![Parent dashboard](docs/screenshots/parent-dashboard.png)
-->

---

## Project status

| Item | Status |
|------|--------|
| Child + Parent APK | ✅ Working |
| Render deployment | ✅ Live |
| Multi-child linking | ✅ Supported |
| Background monitoring | ✅ Usage + Accessibility |
| Email on Render | ⚠️ Configure Resend/SMTP |
| Persistent DB on Render | ⚠️ Enable disk `/var/data` |
| iOS client | ❌ Not in scope |

---

## Future improvements

- [ ] Firebase Cloud Messaging (instant commands)
- [ ] Encrypted SQLite at rest
- [ ] Parent web dashboard
- [ ] PDF weekly reports
- [ ] Geofencing alerts
- [ ] Play Store release (signed APK)

---

## Security note

- **Do not commit** `.env`, database files, or real API keys.
- Default demo key `graduation-secret-key` is for development only — set `API_KEY` on Render.
- Configure `RESEND_API_KEY` or `SMTP_*` via Render environment variables.

---

## Links

- **Server:** https://parental-server-4mms.onrender.com
- **Health:** https://parental-server-4mms.onrender.com/health
- **Quick links:** [MYRana/LINKS.md](MYRana/LINKS.md)
- **Email setup:** [EMAIL_SETUP.md](EMAIL_SETUP.md)
- **Graduation setup:** [GRADUATION_SETUP.md](GRADUATION_SETUP.md)

---

## License

Graduation project — educational use.
