package com.example.myrana.data.remote

import com.example.myrana.BuildConfig
import com.example.myrana.util.ChildCodeNormalizer
import com.example.myrana.data.remote.dto.ParentCommandDto
import com.example.myrana.data.remote.dto.RegisterChildRequest
import com.example.myrana.data.remote.dto.RegisterChildResponse
import com.example.myrana.data.remote.dto.UsageAppItem
import com.example.myrana.screentime.ScreenTimePolicy
import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * طبقة الشبكة الموحّدة: Retrofit للسياسة + OkHttp مباشر لمسارات السيرفر القديمة.
 *
 * عناوين من `BuildConfig`:
 * - [BuildConfig.SERVER_BASE_URL]: مسارات `/api/v1/devices/…/policy`
 * - [BuildConfig.SERVER_ROOT_URL]: `/register-child-device`, `/get-command`, `/upload-usage`
 *
 * كل الطلبات تضيف رأس `X-API-KEY` كما في mother-app.
 */
object NetworkModule {

    @Volatile
    private var api: ParentPolicyApi? = null

    @Volatile
    private var httpClient: OkHttpClient? = null

    private val gson = Gson()

    /** واجهة Retrofit للسياسة — تُبنى مرة واحدة (Singleton). */
    fun api(): ParentPolicyApi {
        val cached = api
        if (cached != null) return cached
        return synchronized(this) {
            api ?: buildApi().also { api = it }
        }
    }

    /**
     * تسجيل جهاز الطفل على السيرفر.
     * @throws IllegalStateException عند فشل HTTP أو عنوان غير صالح.
     */
    fun registerChildDevice(request: RegisterChildRequest): RegisterChildResponse {
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull()
            ?: throw IllegalStateException("عنوان السيرفر غير صالح")
        val url = base.newBuilder().addPathSegments("register-child-device").build()
        val body = gson.toJson(request).toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .post(body)
            .build()
        val response = client().newCall(httpRequest).execute()
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val hint = if (response.code == 404) {
                " — السيرفر قديم على Render (حدّثي server.py + blocklists)"
            } else ""
            throw IllegalStateException("فشل التسجيل (${response.code})$hint: $text")
        }
        return gson.fromJson(text, RegisterChildResponse::class.java)
    }

    enum class ChildRegistrationState {
        LINKED,
        WAITING,
        NOT_ON_SERVER,
        ERROR,
    }

    /** حالة تسجيل/ربط الطفل على السيرفر. */
    fun queryChildRegistrationState(childCode: String): ChildRegistrationState {
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return ChildRegistrationState.ERROR
        val code = com.example.myrana.util.ChildCodeNormalizer.forApi(childCode)
        val url = base.newBuilder()
            .addPathSegments("child-link-status")
            .addQueryParameter("child_code", code)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .get()
            .build()
        return try {
            client().newCall(request).execute().use { response ->
                if (response.code == 404) return ChildRegistrationState.NOT_ON_SERVER
                if (response.code == 401) return ChildRegistrationState.ERROR
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) return ChildRegistrationState.ERROR
                val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                val json: Map<String, Any?> = gson.fromJson(text, mapType)
                if (parseLinkedFlag(json)) ChildRegistrationState.LINKED
                else ChildRegistrationState.WAITING
            }
        } catch (_: Exception) {
            ChildRegistrationState.ERROR
        }
    }

    private fun parseLinkedFlag(json: Map<String, Any?>): Boolean {
        return when (val linked = json["linked"]) {
            is Boolean -> linked
            is Number -> linked.toInt() != 0
            is String -> linked == "1" || linked.equals("true", ignoreCase = true)
            else -> false
        }
    }

    /** هل اكتمل ربط ولي الأمر بجهاز الطفل؟ */
    fun fetchChildLinkStatus(childCode: String): Boolean =
        queryChildRegistrationState(childCode) == ChildRegistrationState.LINKED

    /** جداول زمنية نشطة الآن — تطبيقات تُحظر مؤقتاً (freeze / block_app). */
    fun fetchActiveSchedulePackages(childCode: String): Set<String> {
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return emptySet()
        val url = base.newBuilder()
            .addPathSegments("active-schedules")
            .addQueryParameter("child_code", childCode)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .get()
            .build()
        val response = client().newCall(request).execute()
        if (!response.isSuccessful) return emptySet()
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) return emptySet()
        val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
        val json: Map<String, Any?> = gson.fromJson(body, mapType)
        @Suppress("UNCHECKED_CAST")
        val list = json["packages"] as? List<*> ?: return emptySet()
        return list.mapNotNull { it?.toString()?.trim()?.lowercase() }.filter { it.isNotEmpty() }.toSet()
    }

    /** رفع استخدام — يُفضَّل UsageSyncRepository. */
    fun uploadUsageSync(childCode: String, secondsByPackage: Map<String, Long>): Boolean {
        if (secondsByPackage.isEmpty()) return true
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        val entries = secondsByPackage.map { (pkg, sec) ->
            Triple(day, pkg, sec)
        }
        return uploadUsageEntriesSync(childCode, entries)
    }

    /** رفع دفعة استخدام — كل صف بيومه (للمزامنة بعد انقطاع النت). */
    fun uploadUsageEntriesSync(
        childCode: String,
        entries: List<Triple<String, String, Long>>,
    ): Boolean {
        if (entries.isEmpty()) return true
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return false
        val payloadEntries = entries.map { (day, pkg, sec) ->
            mapOf("package" to pkg, "day" to day, "seconds" to sec)
        }
        val payload = mapOf("child_code" to childCode, "entries" to payloadEntries)
        val url = base.newBuilder().addPathSegments("upload-usage").build()
        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .post(body)
            .build()
        return try {
            client().newCall(httpRequest).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    fun uploadUsage(childCode: String, secondsByPackage: Map<String, Long>) {
        uploadUsageSync(childCode, secondsByPackage)
    }

    /** تقرير استخدام — قائمة مرتبة من الأكثر استخداماً (مع معدل يومي). */
    fun fetchWeeklyUsageList(childCode: String, days: Int = 7): List<UsageAppItem> {
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return emptyList()
        val span = days.coerceIn(1, 30)
        val url = base.newBuilder()
            .addPathSegments("weekly-report")
            .addQueryParameter("child_code", childCode)
            .addQueryParameter("days", span.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .get()
            .build()
        val response = client().newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful || body.isBlank()) return emptyList()
        val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
        val json: Map<String, Any?> = gson.fromJson(body, mapType)
        @Suppress("UNCHECKED_CAST")
        val apps = json["apps"] as? List<Map<String, Any?>> ?: return emptyList()
        return apps.mapNotNull { row ->
            val pkg = row["package_name"]?.toString()?.trim().orEmpty()
            if (pkg.isEmpty()) return@mapNotNull null
            val sec = (row["total_seconds"] as? Number)?.toLong() ?: 0L
            val avg = (row["avg_seconds_per_day"] as? Number)?.toLong()
                ?: (sec / span.coerceAtLeast(1))
            val label = row["app_label"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val icon = row["icon_b64"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            UsageAppItem(
                packageName = pkg,
                totalSeconds = sec,
                avgSecondsPerDay = avg,
                appLabel = label,
                iconBase64 = icon,
            )
        }
    }

    /**
     * تقرير استخدام أسبوعي للأم.
     * @return نص جاهز للعرض أو رسالة خطأ.
     */
    fun fetchWeeklyReportText(childCode: String): String {
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull()
            ?: return "عنوان السيرفر غير صالح"
        val url = base.newBuilder()
            .addPathSegments("weekly-report")
            .addQueryParameter("child_code", childCode)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .get()
            .build()
        val response = client().newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) return "فشل التحميل (${response.code})"
        val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
        val json: Map<String, Any?> = gson.fromJson(body, mapType)
        @Suppress("UNCHECKED_CAST")
        val apps = json["apps"] as? List<Map<String, Any?>> ?: emptyList()
        if (apps.isEmpty()) return "لا توجد بيانات استخدام لهذا الأسبوع بعد."
        val lines = apps.mapIndexed { i, row ->
            val pkg = row["package_name"]?.toString() ?: "?"
            val sec = (row["total_seconds"] as? Number)?.toLong() ?: 0L
            val min = sec / 60
            "${i + 1}. $pkg — ${min} دقيقة"
        }
        return "تقرير الأسبوع (7 أيام):\n" + lines.joinToString("\n")
    }

    /** جلب catalog.json من السيرفر — فلاتر التنبيه والمواقع. */
    fun fetchBlocklistCatalog(): com.example.myrana.enforcement.BlocklistCatalogLoader.Catalog? {
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return null
        val url = base.newBuilder().addPathSegments("blocklist/catalog").build()
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .get()
            .build()
        return try {
            client().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string().orEmpty()
                val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                val json: Map<String, Any?> = gson.fromJson(text, mapType)
                @Suppress("UNCHECKED_CAST")
                val cat = json["catalog"] as? Map<String, Any?> ?: return null
                val sites = (cat["sites"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                val keywords = (cat["video_keywords"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
                com.example.myrana.enforcement.BlocklistCatalogLoader.Catalog(sites, keywords)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** تنبيه للأم عند حظر تطبيق على جهاز الطفل. */
    fun postAlertSync(childCode: String, message: String): Boolean {
        val code = ChildCodeNormalizer.forApi(childCode)
        if (code.isBlank() || message.isBlank()) return false
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return false
        val url = base.newBuilder().addPathSegments("add-alert").build()
        val payload = mapOf("child_code" to code, "message" to message.trim())
        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .post(body)
            .build()
        return try {
            client().newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** جلب سياسة وقت الشاشة من السيرفر. */
    fun fetchScreenTimePolicy(childCode: String): ScreenTimePolicy? {
        val code = ChildCodeNormalizer.forApi(childCode)
        if (code.isBlank()) return null
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return null
        val url = base.newBuilder()
            .addPathSegments("screen-time-policy")
            .addQueryParameter("child_code", code)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .get()
            .build()
        return try {
            client().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string().orEmpty()
                val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                val json: Map<String, Any?> = gson.fromJson(text, mapType)
                @Suppress("UNCHECKED_CAST")
                val policyMap = json["policy"] as? Map<String, Any?> ?: return null
                gson.fromJson(gson.toJson(policyMap), ScreenTimePolicy::class.java)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** حفظ سياسة وقت الشاشة (تطبيق الأم). */
    fun saveScreenTimePolicy(childCode: String, policy: ScreenTimePolicy): Boolean {
        val code = ChildCodeNormalizer.forApi(childCode)
        if (code.isBlank()) return false
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return false
        val url = base.newBuilder().addPathSegments("screen-time-policy").build()
        val payload = mapOf("child_code" to code, "policy" to policy)
        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .post(body)
            .build()
        return try {
            client().newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** رفع قائمة تطبيقات الطفل (اسم + أيقونة) للأم. */
    fun postSyncChildApps(childCode: String, apps: List<Map<String, String?>>): Boolean {
        val code = ChildCodeNormalizer.forApi(childCode)
        if (code.isBlank() || apps.isEmpty()) return true
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return false
        val url = base.newBuilder().addPathSegments("sync-child-apps").build()
        val payload = mapOf("child_code" to code, "apps" to apps)
        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .post(body)
            .build()
        return try {
            client().newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** نبضة اتصال من جهاز الطفل — تتضمن حالة الصلاحيات لولي الأمر. */
    fun postChildHeartbeat(childCode: String, permissions: Map<String, Any?>? = null): Boolean {
        val code = ChildCodeNormalizer.forApi(childCode)
        if (code.isBlank()) return false
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return false
        val url = base.newBuilder().addPathSegments("child-heartbeat").build()
        val payload = mutableMapOf<String, Any?>(
            "child_code" to code,
            "ts_ms" to System.currentTimeMillis(),
        )
        if (!permissions.isNullOrEmpty()) {
            payload["permissions"] = permissions
        }
        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .post(body)
            .build()
        return try {
            client().newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** رفع أحداث وقت الشاشة. */
    fun postScreenTimeEvents(childCode: String, events: List<Map<String, Any?>>? = null): Boolean {
        val code = ChildCodeNormalizer.forApi(childCode)
        if (code.isBlank()) return false
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return false
        val url = base.newBuilder().addPathSegments("screen-time-events").build()
        val payload = if (events != null) {
            mapOf("child_code" to code, "events" to events)
        } else {
            mapOf("child_code" to code, "events" to emptyList<Any>())
        }
        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .post(body)
            .build()
        return try {
            client().newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** لوحة مؤشرات الطفل لولي الأمر. */
    fun fetchChildDashboard(childCode: String): Map<String, Any?>? {
        val code = ChildCodeNormalizer.forApi(childCode)
        if (code.isBlank()) return null
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return null
        val url = base.newBuilder()
            .addPathSegments("child-dashboard")
            .addQueryParameter("child_code", code)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .get()
            .build()
        return try {
            client().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string().orEmpty()
                val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                gson.fromJson(text, mapType)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun pollParentCommand(childCode: String): ParentCommandDto? {
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return null
        val url = base.newBuilder()
            .addPathSegments("get-command")
            .addQueryParameter("child_code", childCode)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .get()
            .build()
        val response = client().newCall(request).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) return null
        val cmd = gson.fromJson(body, ParentCommandDto::class.java)
        if (cmd.action.isNullOrBlank() || cmd.action == "none") return null
        return cmd
    }

    /** عميل OkHttp مشترك مع مهلة 30 ثانية ورأس API. */
    private fun client(): OkHttpClient {
        val cached = httpClient
        if (cached != null) return cached
        return synchronized(this) {
            httpClient ?: buildHttpClient().also { httpClient = it }
        }
    }

    private fun buildHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("X-API-KEY", BuildConfig.API_KEY)
                        .build()
                )
            }
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun buildApi(): ParentPolicyApi {
        val client = client()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.SERVER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ParentPolicyApi::class.java)
    }

    /**
     * طلب POST عام على جذر السيرفر (مسارات الأم في Python).
     * @return نص JSON للاستجابة.
     */
    fun postRoot(path: String, payload: Map<String, Any?>): String {
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull()
            ?: throw IllegalStateException("عنوان السيرفر غير صالح")
        val url = base.newBuilder().addPathSegments(path.trim('/')).build()
        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", BuildConfig.API_KEY)
            .post(body)
            .build()
        val response = client().newCall(request).execute()
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            // يمرّر JSON كاملاً لـ GuardianApi.friendlyError (child_code_clean، detail_ar، …)
            val bodyForParse = text.ifBlank { """{"message":"HTTP ${response.code}"}""" }
            throw IllegalStateException("HTTP ${response.code}: $bodyForParse")
        }
        return text
    }
}
