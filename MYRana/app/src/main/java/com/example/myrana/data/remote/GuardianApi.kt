package com.example.myrana.data.remote

import android.content.Context
import com.example.myrana.data.remote.dto.UsageAppItem
import com.example.myrana.screentime.ScreenTimePolicy
import com.example.myrana.util.ChildCodeNormalizer
import com.example.myrana.util.ServerConnectionHelper
import java.net.URLEncoder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * مسارات تطبيق الأم (mother-app في Python) — نفس `server.py`.
 * تُستخدم من نكهة `parent` فقط؛ الكود مشترك في المشروع.
 */
object GuardianApi {

    private val gson = Gson()

    /** فحص السيرفر قبل الربط — إنترنت + إيقاظ Render + 3 محاولات. */
    fun checkServerConnection(context: Context): ApiResult {
        val result = ServerConnectionHelper.checkConnectivity(context)
        return if (result.ok) ApiResult.Ok(result.message) else ApiResult.Error(result.message)
    }

    /** إرسال رمز تحقق لبريد ولي الأمر. */
    fun sendEmailCode(email: String): ApiResult {
        return try {
            val response = NetworkModule.postRoot("send-email-code", mapOf("email" to email.trim()))
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(response, mapType)
            if (json["status"]?.toString() == "success" && json["email_sent"] == true) {
                val baseMsg = json["message"]?.toString() ?: "تم"
                ApiResult.EmailCodeSent(
                    message = "$baseMsg\n\nتحققي من صندوق البريد وأدخلي الرمز يدوياً.",
                    verificationCode = null,
                    devFallback = false,
                )
            } else if (json["status"]?.toString() == "success" && json["dev_fallback"] == true) {
                ApiResult.Error(
                    json["message"]?.toString()
                        ?: "وضع التطوير فقط — فعّلي البريد على السيرفر للربط الحقيقي",
                )
            } else {
                ApiResult.Error(translateServerMessage(json["message"]?.toString() ?: response))
            }
        } catch (e: Exception) {
            ApiResult.Error(friendlyError(e))
        }
    }

    /** إرسال رمز الربط لبريد ولي الأمر — مرة واحدة أثناء الربط. */
    fun sendLinkCode(guardianEmail: String, childCode: String): ApiResult {
        return try {
            val response = NetworkModule.postRoot(
                "send-link-code",
                mapOf(
                    "guardian_email" to guardianEmail.trim(),
                    "child_code" to ChildCodeNormalizer.forApi(childCode),
                )
            )
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(response, mapType)
            if (json["status"]?.toString() == "success" && json["email_sent"] == true) {
                val baseMsg = json["message"]?.toString() ?: "تم"
                ApiResult.EmailCodeSent(
                    message = "$baseMsg\n\nافتحي Gmail وأدخلي رمز الربط في الحقل أدناه.",
                    verificationCode = null,
                    devFallback = false,
                )
            } else if (json["status"]?.toString() == "success" && json["dev_fallback"] == true) {
                ApiResult.Error(
                    json["message"]?.toString()
                        ?: "وضع التطوير فقط — فعّلي البريد على السيرفر للربط الحقيقي",
                )
            } else {
                val extra = json["child_code_clean"]?.toString()?.trim().orEmpty()
                val msg = translateServerMessage(json["message"]?.toString() ?: response)
                ApiResult.Error(
                    if (extra.isNotEmpty()) "$msg\n(كود منظّف: $extra)" else msg
                )
            }
        } catch (e: Exception) {
            ApiResult.Error(friendlyError(e))
        }
    }

    /** التحقق من رمز البريد. */
    fun verifyEmailCode(email: String, code: String): ApiResult {
        return post(
            "verify-email-code",
            mapOf("email" to email.trim(), "code" to code.trim())
        )
    }

    /**
     * التحقق الإلكتروني من رمز جهاز الطفل — نفس آلية [verifyEmailCode].
     * يستخدمه تطبيق الطفل وولي الأمر قبل الربط.
     */
    fun verifyChildDeviceCode(childCode: String, code: String): ApiResult {
        return try {
            val response = NetworkModule.postRoot(
                "verify-child-device-code",
                mapOf(
                    "child_code" to ChildCodeNormalizer.forApi(childCode),
                    "code" to code.trim(),
                )
            )
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(response, mapType)
            if (json["status"]?.toString() == "success") {
                ApiResult.DeviceVerified(
                    message = json["message"]?.toString() ?: "تم التحقق",
                    childCode = ChildCodeNormalizer.normalize(
                        json["child_code"]?.toString()?.trim().orEmpty()
                    ),
                    childEmail = json["child_email"]?.toString()?.trim().orEmpty(),
                    deviceName = json["device_name"]?.toString()?.trim().orEmpty(),
                    androidVersion = json["android_version"]?.toString()?.trim().orEmpty(),
                )
            } else {
                ApiResult.Error(translateServerMessage(json["message"]?.toString() ?: response))
            }
        } catch (e: Exception) {
            ApiResult.Error(friendlyError(e))
        }
    }

    /**
     * ربط الطفل بعد تسجيل جهازه من تطبيق الطفل.
     * الحقول مطابقة لـ `POST /add-child` في server.py.
     */
    fun addChild(
        name: String,
        age: Int,
        childEmail: String,
        device: String,
        androidVersion: String,
        childCode: String,
        deviceVerifyCode: String,
        guardianEmail: String,
        guardianRole: String,
    ): ApiResult {
        val email = guardianEmail.trim()
        val code = ChildCodeNormalizer.forApi(childCode)
        val verify = deviceVerifyCode.trim()
        return post(
            "add-child",
            mapOf(
                "parent_email" to email,
                "child_code" to code,
                "verification_code" to verify,
                "name" to name.trim().ifBlank { "طفل" },
                "child_name" to name.trim().ifBlank { "طفل" },
                "age" to age,
                "child_email" to childEmail.trim().ifBlank { email },
                "device" to device.trim(),
                "android_version" to androidVersion.trim(),
                "device_verify_code" to verify,
                "otp" to verify,
                "guardian_email" to email,
                "email" to email,
                "guardian_role" to guardianRole.trim(),
            )
        )
    }

    /** جدولة حظر/تجميد بين ساعتين (HH:MM). */
    fun addSchedule(
        childCode: String,
        action: String,
        value: String,
        startTime: String,
        endTime: String
    ): ApiResult {
        return post(
            "add-schedule",
            mapOf(
                "child_code" to ChildCodeNormalizer.forApi(childCode),
                "action" to action.trim(),
                "value" to value.trim(),
                "start_time" to startTime.trim(),
                "end_time" to endTime.trim()
            )
        )
    }

    /** طلب رفع فوري من جهاز الطفل ثم جلب التقرير. */
    fun requestUsageFromChild(childCode: String, guardianEmail: String): ApiResult {
        return sendCommand("request_usage", "", childCode, guardianEmail)
    }

    fun fetchScreenTimePolicy(childCode: String): ApiResult {
        return try {
            val policy = NetworkModule.fetchScreenTimePolicy(childCode)
                ?: return ApiResult.Error("تعذّر جلب إعدادات وقت الاستخدام")
            ApiResult.ScreenTimePolicyLoaded(policy)
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "خطأ شبكة")
        }
    }

    fun saveScreenTimePolicy(childCode: String, policy: ScreenTimePolicy): ApiResult {
        return try {
            val ok = NetworkModule.saveScreenTimePolicy(childCode, policy)
            if (ok) ApiResult.Ok("تم حفظ إعدادات وقت الاستخدام")
            else ApiResult.Error("فشل الحفظ على السيرفر")
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "خطأ شبكة")
        }
    }

    fun fetchChildDashboard(childCode: String): ApiResult {
        return try {
            val json = NetworkModule.fetchChildDashboard(childCode)
                ?: return ApiResult.Error("تعذّر تحميل لوحة المؤشرات")
            val policyMap = json["policy"] as? Map<*, *>
            val policy = if (policyMap != null) {
                gson.fromJson(gson.toJson(policyMap), ScreenTimePolicy::class.java)
            } else {
                ScreenTimePolicy()
            }
            @Suppress("UNCHECKED_CAST")
            val topApps = (json["top_apps_today"] as? List<Map<String, Any?>>).orEmpty()
            ApiResult.ChildDashboard(
                ChildDashboardData(
                    childCode = json["child_code"]?.toString().orEmpty(),
                    childName = json["child_name"]?.toString().orEmpty(),
                    deviceName = json["device_name"]?.toString().orEmpty(),
                    online = json["online"] == true,
                    lastSeenMs = (json["last_seen_ms"] as? Number)?.toLong() ?: 0L,
                    todaySeconds = (json["today_seconds"] as? Number)?.toLong() ?: 0L,
                    appsOpened = (json["apps_opened"] as? Number)?.toInt() ?: 0,
                    educationalSeconds = (json["educational_seconds"] as? Number)?.toLong() ?: 0L,
                    monitoredSeconds = (json["monitored_seconds"] as? Number)?.toLong() ?: 0L,
                    alertsToday = (json["alerts_today"] as? Number)?.toInt() ?: 0,
                    alertsWeek = (json["alerts_week"] as? Number)?.toInt() ?: 0,
                    topAppsToday = topApps,
                    policy = policy,
                    permissionsOk = json["permissions_ok"] == true,
                    permissions = (json["permissions"] as? Map<String, Any?>) ?: emptyMap(),
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "خطأ شبكة")
        }
    }

    fun fetchDailyReport(childCode: String): ApiResult {
        return try {
            val base = com.example.myrana.BuildConfig.SERVER_ROOT_URL
            val url = java.net.URL(
                "$base/daily-report?child_code=${URLEncoder.encode(ChildCodeNormalizer.forApi(childCode), "UTF-8")}"
            )
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("X-API-KEY", com.example.myrana.BuildConfig.API_KEY)
            conn.connectTimeout = 20_000
            conn.readTimeout = 20_000
            val body = conn.inputStream.bufferedReader().readText()
            if (conn.responseCode != 200) {
                return ApiResult.Error(conn.responseMessage ?: "خطأ")
            }
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(body, mapType)
            @Suppress("UNCHECKED_CAST")
            val apps = json["apps"] as? List<Map<String, Any?>> ?: emptyList()
            if (apps.isEmpty()) {
                return ApiResult.ReportText("لا توجد بيانات لليوم بعد.")
            }
            val day = json["day"]?.toString().orEmpty()
            val lines = apps.mapIndexed { i, row ->
                val pkg = row["package_name"]?.toString() ?: "?"
                val sec = (row["total_seconds"] as? Number)?.toLong() ?: 0L
                "${i + 1}. $pkg — ${sec / 60} د"
            }
            ApiResult.ReportText("تقرير اليوم ($day):\n" + lines.joinToString("\n"))
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "خطأ شبكة")
        }
    }

    /** بيانات الرسوم البيانية — استخدام يومي + أفضل التطبيقات. */
    fun fetchWeeklyChart(childCode: String): ApiResult {
        return try {
            val code = ChildCodeNormalizer.forApi(childCode)
            val base = com.example.myrana.BuildConfig.SERVER_ROOT_URL
            val url = java.net.URL(
                "$base/weekly-chart?child_code=${URLEncoder.encode(code, "UTF-8")}"
            )
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("X-API-KEY", com.example.myrana.BuildConfig.API_KEY)
            conn.connectTimeout = 20_000
            conn.readTimeout = 20_000
            val body = conn.inputStream.bufferedReader().readText()
            if (conn.responseCode != 200) {
                return ApiResult.Error(conn.responseMessage ?: "خطأ")
            }
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(body, mapType)
            if (json["success"] != true) {
                return ApiResult.Error(json["message"]?.toString() ?: "فشل جلب البيانات")
            }
            @Suppress("UNCHECKED_CAST")
            val usageByDay = (json["usage_by_day"] as? List<Map<String, Any?>>).orEmpty()
            @Suppress("UNCHECKED_CAST")
            val topApps = (json["top_apps"] as? List<Map<String, Any?>>).orEmpty()
            @Suppress("UNCHECKED_CAST")
            val educational = (json["educational_apps"] as? List<Map<String, Any?>>).orEmpty()
            ApiResult.WeeklyChart(
                WeeklyChartData(
                    usageByDay = usageByDay,
                    topApps = topApps,
                    educationalApps = educational,
                    alertsToday = (json["alerts_today"] as? Number)?.toInt() ?: 0,
                    alertsWeek = (json["alerts_week"] as? Number)?.toInt() ?: 0,
                    sleepViolationsWeek = (json["sleep_violations_week"] as? Number)?.toInt() ?: 0,
                )
            )
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "خطأ شبكة")
        }
    }

    /** قائمة استخدام التطبيقات (آخر 7 أيام على السيرفر). */
    fun fetchWeeklyUsage(childCode: String): ApiResult {
        return try {
            ApiResult.UsageList(NetworkModule.fetchWeeklyUsageList(childCode))
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "خطأ شبكة")
        }
    }

    /** تطبيق قائمة الحظر الافتراضية من السيرفر على جهاز الطفل. */
    fun applyDefaultBlocklist(childCode: String, merge: Boolean = true): ApiResult {
        return try {
            val response = NetworkModule.postRoot(
                "apply-default-blocklist",
                mapOf("child_code" to ChildCodeNormalizer.forApi(childCode), "merge" to merge)
            )
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(response, mapType)
            if (json["status"]?.toString() == "success") {
                @Suppress("UNCHECKED_CAST")
                val counts = json["counts"] as? Map<String, Any?>
                val pkgs = (counts?.get("packages") as? Number)?.toInt() ?: 0
                val sites = (counts?.get("sites") as? Number)?.toInt() ?: 0
                val kw = (counts?.get("video_keywords") as? Number)?.toInt() ?: 0
                ApiResult.Ok("تم: $pkgs تطبيق، $sites موقع، $kw كلمة فيديو")
            } else {
                ApiResult.Error(json["message"]?.toString() ?: response)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "خطأ شبكة")
        }
    }

    /** جلب تنبيهات محاولات الحظر. */
    fun fetchAlerts(childCode: String): ApiResult.Alerts {
        return try {
            val code = ChildCodeNormalizer.forApi(childCode)
            val base = com.example.myrana.BuildConfig.SERVER_ROOT_URL
            val url = java.net.URL("$base/alerts?child_code=${URLEncoder.encode(code, "UTF-8")}")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("X-API-KEY", com.example.myrana.BuildConfig.API_KEY)
            conn.connectTimeout = 20_000
            conn.readTimeout = 20_000
            val body = conn.inputStream.bufferedReader().readText()
            if (conn.responseCode != 200) {
                return ApiResult.Alerts(emptyList(), conn.responseMessage ?: "خطأ")
            }
            val listType = object : TypeToken<List<Map<String, Any?>>>() {}.type
            val rows: List<Map<String, Any?>> = gson.fromJson(body, listType)
            val lines = rows.mapNotNull { row ->
                val msg = row["message"]?.toString()?.trim().orEmpty()
                val time = row["time"]?.toString()?.trim().orEmpty()
                if (msg.isEmpty()) null else if (time.isEmpty()) msg else "$time — $msg"
            }
            ApiResult.Alerts(lines)
        } catch (e: Exception) {
            ApiResult.Alerts(emptyList(), e.message ?: "خطأ شبكة")
        }
    }

    /** رسالة من الأم/ولي الأمر — تُحفظ في التنبيهات لكل الأجهزة المرتبطة. */
    fun sendGuardianMessage(childCode: String, guardianRole: String, message: String): ApiResult {
        return post(
            "send-guardian-message",
            mapOf(
                "child_code" to ChildCodeNormalizer.forApi(childCode),
                "guardian_role" to guardianRole.trim().ifBlank { "ولي الأمر" },
                "message" to message.trim()
            )
        )
    }

    /** إرسال أمر حظر أو سماح لجهاز الطفل. */
    fun sendCommand(
        action: String,
        value: String,
        childCode: String,
        guardianEmail: String
    ): ApiResult {
        return post(
            "send-command",
            mapOf(
                "action" to action,
                "value" to value.trim(),
                "child_code" to ChildCodeNormalizer.forApi(childCode),
                "guardian_email" to guardianEmail.trim()
            )
        )
    }

    fun fetchGuardianSettings(parentEmail: String): ApiResult {
        return try {
            val email = parentEmail.trim()
            val base = com.example.myrana.BuildConfig.SERVER_ROOT_URL
            val url = "$base/guardian-settings?parent_email=${URLEncoder.encode(email, "UTF-8")}"
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("X-API-KEY", com.example.myrana.BuildConfig.API_KEY)
            conn.connectTimeout = 20_000
            conn.readTimeout = 20_000
            val body = conn.inputStream.bufferedReader().readText()
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(body, mapType)
            if (json["success"] == true) {
                @Suppress("UNCHECKED_CAST")
                val settings = (json["settings"] as? Map<String, Any?>) ?: emptyMap()
                ApiResult.GuardianSettingsLoaded(settings)
            } else {
                ApiResult.Error(json["message"]?.toString() ?: body)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "خطأ شبكة")
        }
    }

    fun saveGuardianSettings(parentEmail: String, settings: Map<String, Any?>): ApiResult {
        return post(
            "guardian-settings",
            mapOf(
                "parent_email" to parentEmail.trim(),
                "email" to parentEmail.trim(),
                "settings" to settings,
            )
        )
    }

    fun fetchAuditLog(parentEmail: String, childCode: String?): ApiResult {
        return try {
            val email = parentEmail.trim()
            val base = com.example.myrana.BuildConfig.SERVER_ROOT_URL
            var url = "$base/audit-log?parent_email=${URLEncoder.encode(email, "UTF-8")}"
            if (!childCode.isNullOrBlank()) {
                url += "&child_code=${URLEncoder.encode(ChildCodeNormalizer.forApi(childCode), "UTF-8")}"
            }
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("X-API-KEY", com.example.myrana.BuildConfig.API_KEY)
            val body = conn.inputStream.bufferedReader().readText()
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(body, mapType)
            if (json["success"] == true) {
                @Suppress("UNCHECKED_CAST")
                val entries = (json["entries"] as? List<Map<String, Any?>>).orEmpty()
                val lines = entries.map { row ->
                    val time = row["created_at"]?.toString().orEmpty()
                    val action = row["action"]?.toString().orEmpty()
                    val detail = row["detail"]?.toString().orEmpty()
                    val code = row["child_code"]?.toString().orEmpty()
                    "$time — $action${if (code.isNotBlank()) " ($code)" else ""}\n$detail"
                }
                ApiResult.AuditLog(lines)
            } else {
                ApiResult.Error(json["message"]?.toString() ?: body)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "خطأ شبكة")
        }
    }

    fun sendEmailSummary(parentEmail: String, childCode: String, period: String): ApiResult {
        return post(
            "send-email-summary",
            mapOf(
                "parent_email" to parentEmail.trim(),
                "child_code" to ChildCodeNormalizer.forApi(childCode),
                "period" to period,
            )
        )
    }

    /** قائمة الأطفال المرتبطين بولي الأمر — دعم تعدد الأطفال. */
    fun fetchLinkedChildren(parentEmail: String): ApiResult {
        return try {
            val email = parentEmail.trim()
            val base = com.example.myrana.BuildConfig.SERVER_ROOT_URL
            val url = "$base/list-children?parent_email=${URLEncoder.encode(email, "UTF-8")}" +
                "&email=${URLEncoder.encode(email, "UTF-8")}"
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("X-API-KEY", com.example.myrana.BuildConfig.API_KEY)
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            val text = conn.inputStream.bufferedReader().readText()
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(text, mapType)
            if (json["success"] == true || json["status"]?.toString() == "success") {
                @Suppress("UNCHECKED_CAST")
                val list = (json["children"] as? List<Map<String, Any?>>).orEmpty()
                ApiResult.ChildrenList(list)
            } else {
                ApiResult.Error(translateServerMessage(json["message"]?.toString() ?: text))
            }
        } catch (e: Exception) {
            ApiResult.Error(friendlyError(e))
        }
    }

    private fun post(path: String, body: Map<String, Any?>): ApiResult {
        return try {
            val response = NetworkModule.postRoot(path, body)
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(response, mapType)
            val ok = json["success"] == true || json["status"]?.toString() == "success"
            if (ok) {
                val parentId = (json["parent_id"] as? Number)?.toInt()
                val childId = (json["child_id"] as? Number)?.toInt()
                val linkedCode = json["child_code"]?.toString()?.trim()
                if (path.contains("add-child") || path.contains("link-child")) {
                    ApiResult.LinkSuccess(
                        message = json["message"]?.toString() ?: "تم",
                        parentId = parentId,
                        childId = childId,
                        childCode = linkedCode,
                    )
                } else {
                    ApiResult.Ok(json["message"]?.toString() ?: "تم")
                }
            } else {
                ApiResult.Error(translateServerMessage(json["message"]?.toString() ?: response))
            }
        } catch (e: Exception) {
            ApiResult.Error(friendlyError(e))
        }
    }

    private fun translateServerMessage(message: String): String {
        return when {
            message.contains("Child device not found", ignoreCase = true) ->
                "لم يُعثر على جهاز الطفل — سجّلي من تطبيق الطفل أولاً ثم استخدمي CHILD-... من Gmail"
            message.contains("Device already linked", ignoreCase = true) ->
                "الجهاز مربوط مسبقاً — امسحي بيانات التطبيقين وأعيدي المحاولة"
            message.contains("Invalid verification code", ignoreCase = true) ||
                message.contains("Invalid or expired verification code", ignoreCase = true) ||
                message.contains("invalid_verification_code", ignoreCase = true) ||
                message.contains("كود التحقق غير صحيح", ignoreCase = true) ->
                "رمز الربط غير صحيح — أرسلي رمز الربط من جديد واستخدمي آخر رمز من Gmail (ليس رمز البريد الأول)"
            message.contains("expired_code", ignoreCase = true) ||
                message.contains("منتهي الصلاحية", ignoreCase = true) ->
                "كود التحقق منتهي الصلاحية — أرسلي رمزاً جديداً"
            message.contains("Child not found", ignoreCase = true) ||
                message.contains("child_not_found", ignoreCase = true) ||
                message.contains("الطفل غير موجود", ignoreCase = true) ||
                message.contains("لم يُعثر على جهاز الطفل", ignoreCase = true) ->
                "الطفل غير مسجّل على السيرفر.\n\n" +
                    "① جوال الطفل: «تسجيل الجهاز»\n" +
                    "② افتحي Gmail وانسخي كود CHILD-...\n" +
                    "③ جوال الأم: الصقي الكود → «ربط تلقائي»"
            else -> message
        }
    }

    private fun friendlyError(e: Exception): String {
        val raw = e.message.orEmpty()
        if (raw.contains("لا يوجد إنترنت") ||
            raw.contains("تعذّر الوصول للسيرفر") ||
            raw.contains("السيرفر بطيء")
        ) {
            return raw
        }
        val kind = ServerConnectionHelper.classify(e)
        if (kind != ServerConnectionHelper.ErrorKind.OTHER) {
            return ServerConnectionHelper.messageFor(kind, e)
        }
        val jsonStart = raw.indexOf('{')
        if (jsonStart >= 0) {
            try {
                val mapType = object : TypeToken<Map<String, Any?>>() {}.type
                val json: Map<String, Any?> = gson.fromJson(raw.substring(jsonStart), mapType)
                val server = json["message"]?.toString()
                val detailAr = json["detail_ar"]?.toString()?.trim().orEmpty()
                val cleaned = json["child_code_clean"]?.toString()?.trim().orEmpty()
                if (!server.isNullOrBlank()) {
                    val msg = translateServerMessage(server)
                    val hint = detailAr.ifBlank {
                        if (cleaned.isNotEmpty()) "كود منظّف: $cleaned" else ""
                    }
                    return if (hint.isNotEmpty()) "$msg\n$hint" else msg
                }
            } catch (_: Exception) {
            }
        }
        return ServerConnectionHelper.friendlyMessage(e)
    }

    sealed class ApiResult {
        data class Ok(val message: String) : ApiResult()
        /** نجاح ربط الطفل — يتضمن parent_id و child_id من Flask. */
        data class LinkSuccess(
            val message: String,
            val parentId: Int? = null,
            val childId: Int? = null,
            val childCode: String? = null,
        ) : ApiResult()
        data class ChildrenList(val children: List<Map<String, Any?>>) : ApiResult()
        data class EmailCodeSent(
            val message: String,
            val verificationCode: String?,
            val devFallback: Boolean = false,
        ) : ApiResult()
        data class DeviceVerified(
            val message: String,
            val childCode: String,
            val childEmail: String,
            val deviceName: String,
            val androidVersion: String,
        ) : ApiResult()
        data class Error(val message: String) : ApiResult()
        data class UsageList(val items: List<UsageAppItem>) : ApiResult()
        data class Alerts(val lines: List<String>, val error: String? = null) : ApiResult()
        data class ScreenTimePolicyLoaded(val policy: ScreenTimePolicy) : ApiResult()
        data class ChildDashboard(val data: ChildDashboardData) : ApiResult()
        data class WeeklyChart(val data: WeeklyChartData) : ApiResult()
        data class ReportText(val text: String) : ApiResult()
        data class GuardianSettingsLoaded(val settings: Map<String, Any?>) : ApiResult()
        data class AuditLog(val lines: List<String>) : ApiResult()
    }

    data class ChildDashboardData(
        val childCode: String,
        val childName: String,
        val deviceName: String,
        val online: Boolean,
        val lastSeenMs: Long,
        val todaySeconds: Long,
        val appsOpened: Int,
        val educationalSeconds: Long = 0L,
        val monitoredSeconds: Long = 0L,
        val alertsToday: Int = 0,
        val alertsWeek: Int = 0,
        val topAppsToday: List<Map<String, Any?>> = emptyList(),
        val policy: ScreenTimePolicy,
        val permissionsOk: Boolean = false,
        val permissions: Map<String, Any?> = emptyMap(),
    )

    data class WeeklyChartData(
        val usageByDay: List<Map<String, Any?>>,
        val topApps: List<Map<String, Any?>>,
        val educationalApps: List<Map<String, Any?>>,
        val alertsToday: Int,
        val alertsWeek: Int,
        val sleepViolationsWeek: Int,
    )
}
