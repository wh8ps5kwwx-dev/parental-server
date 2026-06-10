package com.example.myrana.data.remote

import com.example.myrana.data.remote.dto.UsageAppItem
import com.example.myrana.screentime.ScreenTimePolicy
import com.example.myrana.util.ChildCodeNormalizer
import java.net.URLEncoder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * مسارات تطبيق الأم (mother-app في Python) — نفس `server.py`.
 * تُستخدم من نكهة `parent` فقط؛ الكود مشترك في المشروع.
 */
object GuardianApi {

    private val gson = Gson()

    /** إرسال رمز تحقق لبريد ولي الأمر. */
    fun sendEmailCode(email: String): ApiResult {
        return try {
            val response = NetworkModule.postRoot("send-email-code", mapOf("email" to email.trim()))
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(response, mapType)
            if (json["status"]?.toString() == "success") {
                val devFallback = json["dev_fallback"] == true
                val emailSent = json["email_sent"] == true
                val code = if (devFallback) {
                    json["verification_code"]?.toString()?.trim().orEmpty()
                } else {
                    ""
                }
                val baseMsg = json["message"]?.toString() ?: "تم"
                val display = when {
                    emailSent -> "$baseMsg\n\nتحققي من صندوق البريد وأدخلي الرمز."
                    code.isNotEmpty() -> "$baseMsg\n\n${"—".repeat(8)}\nرمز التحقق (تطوير): $code\n${"—".repeat(8)}"
                    else -> baseMsg
                }
                ApiResult.EmailCodeSent(
                    message = display,
                    verificationCode = code.ifEmpty { null },
                    devFallback = devFallback,
                )
            } else {
                ApiResult.Error(json["message"]?.toString() ?: response)
            }
        } catch (e: Exception) {
            ApiResult.Error(e.message ?: "خطأ شبكة")
        }
    }

    /** إرسال رمز الربط لبريد ولي الأمر — مرة واحدة أثناء الربط. */
    fun sendLinkCode(guardianEmail: String, childCode: String): ApiResult {
        return try {
            val response = NetworkModule.postRoot(
                "send-link-code",
                mapOf(
                    "guardian_email" to guardianEmail.trim(),
                    "child_code" to ChildCodeNormalizer.normalize(childCode),
                )
            )
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(response, mapType)
            if (json["status"]?.toString() == "success") {
                val devFallback = json["dev_fallback"] == true
                val emailSent = json["email_sent"] == true
                val code = if (devFallback) {
                    json["verification_code"]?.toString()?.trim().orEmpty()
                } else {
                    ""
                }
                val baseMsg = json["message"]?.toString() ?: "تم"
                val display = when {
                    emailSent -> "$baseMsg\n\nتحققي من بريدك وأدخلي رمز الربط."
                    code.isNotEmpty() -> "$baseMsg\n\nرمز الربط (تطوير): $code"
                    else -> baseMsg
                }
                ApiResult.EmailCodeSent(
                    message = display,
                    verificationCode = code.ifEmpty { null },
                    devFallback = devFallback,
                )
            } else {
                ApiResult.Error(translateServerMessage(json["message"]?.toString() ?: response))
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
                    "child_code" to ChildCodeNormalizer.normalize(childCode),
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
        guardianRole: String
    ): ApiResult {
        val email = guardianEmail.trim()
        val code = ChildCodeNormalizer.normalize(childCode)
        val verify = deviceVerifyCode.trim()
        val displayName = name.trim().ifBlank { "طفل" }
        return post(
            "add-child",
            mapOf(
                "name" to displayName,
                "child_name" to displayName,
                "age" to age,
                "child_email" to childEmail.trim(),
                "device" to device.trim(),
                "android_version" to androidVersion.trim(),
                "child_code" to code,
                "device_verify_code" to verify,
                "verification_code" to verify,
                "otp" to verify,
                "guardian_email" to email,
                "parent_email" to email,
                "email" to email,
                "guardian_role" to guardianRole.trim()
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
                "child_code" to childCode.trim(),
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
            ApiResult.ChildDashboard(
                ChildDashboardData(
                    childCode = json["child_code"]?.toString().orEmpty(),
                    childName = json["child_name"]?.toString().orEmpty(),
                    deviceName = json["device_name"]?.toString().orEmpty(),
                    online = json["online"] == true,
                    lastSeenMs = (json["last_seen_ms"] as? Number)?.toLong() ?: 0L,
                    todaySeconds = (json["today_seconds"] as? Number)?.toLong() ?: 0L,
                    appsOpened = (json["apps_opened"] as? Number)?.toInt() ?: 0,
                    policy = policy,
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
                "$base/daily-report?child_code=${URLEncoder.encode(ChildCodeNormalizer.normalize(childCode), "UTF-8")}"
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
                mapOf("child_code" to childCode.trim(), "merge" to merge)
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
            val code = ChildCodeNormalizer.normalize(childCode)
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
                "child_code" to childCode.trim(),
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
                "child_code" to childCode.trim(),
                "guardian_email" to guardianEmail.trim()
            )
        )
    }

    private fun post(path: String, body: Map<String, Any?>): ApiResult {
        return try {
            val response = NetworkModule.postRoot(path, body)
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(response, mapType)
            val status = json["status"]?.toString()
            if (status == "success") {
                ApiResult.Ok(json["message"]?.toString() ?: "تم")
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
                "لم يُعثر على جهاز الطفل — سجّلي من جوال الطفل أولاً واستخدمي CHILD-..."
            message.contains("Device already linked", ignoreCase = true) ->
                "الجهاز مربوط مسبقاً — امسحي بيانات التطبيقين وأعيدي المحاولة"
            message.contains("Invalid verification code", ignoreCase = true) ||
                message.contains("invalid_verification_code", ignoreCase = true) ||
                message.contains("كود التحقق غير صحيح", ignoreCase = true) ->
                "رمز الربط غير صحيح — أرسلي رمز الربط من جديد واستخدمي آخر رمز من Gmail (ليس رمز البريد الأول)"
            message.contains("expired_code", ignoreCase = true) ||
                message.contains("منتهي الصلاحية", ignoreCase = true) ->
                "كود التحقق منتهي الصلاحية — أرسلي رمزاً جديداً"
            message.contains("child_not_found", ignoreCase = true) ||
                message.contains("الطفل غير موجود", ignoreCase = true) ->
                "لم يُعثر على جهاز الطفل — سجّلي من جوال الطفل أولاً"
            else -> message
        }
    }

    private fun friendlyError(e: Exception): String {
        val raw = e.message.orEmpty()
        val jsonStart = raw.indexOf('{')
        if (jsonStart >= 0) {
            try {
                val mapType = object : TypeToken<Map<String, Any?>>() {}.type
                val json: Map<String, Any?> = gson.fromJson(raw.substring(jsonStart), mapType)
                val server = json["message"]?.toString()
                if (!server.isNullOrBlank()) {
                    return translateServerMessage(server)
                }
            } catch (_: Exception) {
            }
        }
        return translateServerMessage(raw).ifBlank { "خطأ في الاتصال — حاولي مرة أخرى" }
    }

    sealed class ApiResult {
        data class Ok(val message: String) : ApiResult()
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
        data class ReportText(val text: String) : ApiResult()
    }

    data class ChildDashboardData(
        val childCode: String,
        val childName: String,
        val deviceName: String,
        val online: Boolean,
        val lastSeenMs: Long,
        val todaySeconds: Long,
        val appsOpened: Int,
        val policy: ScreenTimePolicy,
    )
}
