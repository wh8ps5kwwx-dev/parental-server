# Child–Parent Linking Guide

## Flow

```
Child phone                    Server (Render)                 Parent phone
     |                                |                              |
     | POST /register-child-device    |                              |
     | child_code=46C0097C            |                              |
     |------------------------------->| INSERT child_devices         |
     |<-------------------------------| 200 CHILD-46C0097C           |
     |                                |                              |
     | GET /child-link-status         |                              |
     |------------------------------->| linked=false                 |
     |                                |                              |
     |                                | GET /child-link-status       |
     |                                |<-----------------------------|
     |                                | 404 = not registered         |
     |                                | 200 linked=false = ready     |
     |                                |                              |
     |                                | POST /send-link-code         |
     |                                | POST /add-child + OTP        |
     |                                | linked=true                  |
     | GET /child-link-status         |                              |
     |<-------------------------------| linked=true → permissions    |
```

## Code format

| Layer | Format | Example |
|-------|--------|---------|
| Display / Gmail | `CHILD-XXXXXXXX` | `CHILD-46C0097C` |
| Database | bare suffix | `46C0097C` |
| API JSON body | bare suffix | `46C0097C` |

Both apps normalize automatically — **prefix mismatch is not the usual bug**.

## "Child not registered on server"

HTTP **404** on `GET /child-link-status` means **no row** in `child_devices`.

### Common causes

1. Child did not complete **Register Device** (no success on server)
2. Render **ephemeral DB** wiped after redeploy — re-register from child phone
3. Parent uses **old code** from previous attempt
4. Parent checks **before** child registers

### Verify manually

```bash
curl -s "https://parental-server-4mms.onrender.com/health"
curl -s -H "X-API-KEY: graduation-secret-key" \
  "https://parental-server-4mms.onrender.com/child-link-status?child_code=46C0097C"
```

### Render logs (search)

- `[register-child-device] OK stored=`
- `[child-link-status] NOT FOUND`
- `[child-link-status] FOUND`

## Test checklist

1. Child: install APK → enter parent Gmail → **Register Device**
2. Confirm code on screen (e.g. `CHILD-46C0097C`)
3. Parent: verify email → paste **exact** child code → check server
4. Parent: send link code → enter OTP from Gmail → link
5. Child: **Check link now** → permissions → academy
