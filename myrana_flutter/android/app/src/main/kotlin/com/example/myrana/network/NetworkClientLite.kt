package com.example.myrana.network

import com.example.myrana.core.ChildCodeNormalizer
import com.example.myrana.enforcement.ScreenTimePolicy
import com.example.myrana.enforcement.ScreenTimePolicyDefaults
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.myrana_flutter.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object NetworkClientLite {

    private val gson = Gson()
    private val client: OkHttpClient = OkHttpClient()

    private fun rootUrl(): String =
        BuildConfig.SERVER_ROOT_URL.trimEnd('/')

    private fun apiKey(): String = BuildConfig.API_KEY

    data class DevicePolicy(
        val blockedHosts: List<String> = emptyList(),
        val blockedPackages: List<String> = emptyList(),
        val videoKeywords: List<String> = emptyList(),
    )

    data class ActiveSchedules(val packages: List<String> = emptyList())

    private fun normalizeChildCode(raw: String): String {
        val cleaned = ChildCodeNormalizer.forApi(raw)
        return cleaned
    }

    fun fetchDevicePolicy(childCode: String): DevicePolicy {
        val code = normalizeChildCode(childCode)
        if (code.isBlank()) return DevicePolicy()
        val url = "${rootUrl()}/api/v1/devices/$code/policy"
        val req = Request.Builder()
            .url(url)
            .header("X-API-KEY", apiKey())
            .get()
            .build()

        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return DevicePolicy()
            val body = res.body?.string().orEmpty()
            if (body.isBlank()) return DevicePolicy()

            val map: Map<String, Any?> = gson.fromJson(
                body,
                object : TypeToken<Map<String, Any?>>() {}.type
            )
            val hosts = ((map["blockedHosts"] as? List<*>) ?: emptyList<Any>()).mapNotNull { it?.toString() }
            val pkgs = ((map["blockedPackages"] as? List<*>) ?: emptyList<Any>()).mapNotNull { it?.toString() }
            val keywords = ((map["videoKeywords"] as? List<*>) ?: emptyList<Any>()).mapNotNull { it?.toString() }
            return DevicePolicy(
                blockedHosts = hosts,
                blockedPackages = pkgs,
                videoKeywords = keywords,
            )
        }
    }

    fun fetchActiveSchedules(childCode: String): ActiveSchedules {
        val code = normalizeChildCode(childCode)
        if (code.isBlank()) return ActiveSchedules()
        val url = "${rootUrl()}/active-schedules?child_code=$code"
        val req = Request.Builder()
            .url(url)
            .header("X-API-KEY", apiKey())
            .get()
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return ActiveSchedules()
            val body = res.body?.string().orEmpty()
            if (body.isBlank()) return ActiveSchedules()
            val map: Map<String, Any?> = gson.fromJson(
                body,
                object : TypeToken<Map<String, Any?>>() {}.type
            )
            val packages = ((map["packages"] as? List<*>) ?: emptyList<Any>()).mapNotNull { it?.toString() }
            return ActiveSchedules(packages = packages)
        }
    }

    fun fetchScreenTimePolicy(childCode: String): ScreenTimePolicy {
        val code = normalizeChildCode(childCode)
        if (code.isBlank()) return ScreenTimePolicyDefaults.defaultPolicy()
        val url = "${rootUrl()}/screen-time-policy?child_code=$code"
        val req = Request.Builder()
            .url(url)
            .header("X-API-KEY", apiKey())
            .get()
            .build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return ScreenTimePolicyDefaults.defaultPolicy()
            val body = res.body?.string().orEmpty()
            if (body.isBlank()) return ScreenTimePolicyDefaults.defaultPolicy()
            // server يرجع: { success, child_code, policy: { ... } }
            val map: Map<String, Any?> = gson.fromJson(
                body,
                object : TypeToken<Map<String, Any?>>() {}.type
            )
            val policyMap = map["policy"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            // نفك/نركّب عبر Gson للحصول على keys الأصلية
            val policyJson = gson.toJson(policyMap)
            val policy = gson.fromJson(policyJson, ScreenTimePolicy::class.java)
            return policy ?: ScreenTimePolicyDefaults.defaultPolicy()
        }
    }

    /**
     * رفع أحداث الاستخدام (delta) إلى السيرفر.
     */
    fun uploadUsageDelta(
        childCode: String,
        entries: List<UsageEntry>,
    ): Boolean {
        if (entries.isEmpty()) return true
        val code = normalizeChildCode(childCode)
        if (code.isBlank()) return false

        val payload = mapOf(
            "child_code" to code,
            "entries" to entries.map { e ->
                mapOf(
                    "day" to e.day,
                    "package" to e.packageName,
                    "seconds" to e.seconds,
                )
            }
        )
        val json = gson.toJson(payload)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val url = "${rootUrl()}/upload-usage"
        val req = Request.Builder()
            .url(url)
            .header("X-API-KEY", apiKey())
            .post(body)
            .build()
        return try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    fun addAlert(childCode: String, message: String): Boolean {
        val code = normalizeChildCode(childCode)
        if (code.isBlank() || message.isBlank()) return false
        val payload = mapOf("child_code" to code, "message" to message)
        val json = gson.toJson(payload)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val url = "${rootUrl()}/add-alert"
        val req = Request.Builder()
            .url(url)
            .header("X-API-KEY", apiKey())
            .post(body)
            .build()
        return try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    data class UsageEntry(
        val day: String,
        val packageName: String,
        val seconds: Long,
    )
}

