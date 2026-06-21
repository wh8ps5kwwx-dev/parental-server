package com.example.myrana.data.remote

import com.example.myrana.BuildConfig
import com.example.myrana.util.ChildCodeNormalizer
import com.example.myrana.data.remote.dto.ParentCommandDto
import com.example.myrana.data.remote.dto.RegisterChildRequest
import com.example.myrana.data.remote.dto.RegisterChildResponse
import com.example.myrana.data.remote.dto.UsageAppItem
import com.example.myrana.screentime.ScreenTimePolicy
import com.google.gson.Gson
import com.example.myrana.util.ServerConfig
import com.example.myrana.util.ServerConnectionHelper
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
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

    private const val TAG = "MYRanaLink"

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
        val base = ServerConfig.rootHttpUrl()
            ?: throw IllegalStateException("عنوان السيرفر غير صالح")
        val url = base.newBuilder().addPathSegments("register-child-device").build()
        val body = gson.toJson(request).toRequestBody("application/json".toMediaType())
        Log.i(TAG, "register-child-device → code=${request.childCode} device=${request.deviceName}")
        val httpRequest = Request.Builder()
            .url(url)
            .header("X-API-KEY", ServerConfig.apiKey)
            .post(body)
            .build()
        return try {
            executeWithRetry(httpRequest).use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "register-child-device FAILED http=${response.code} body=$text")
                    val hint = if (response.code == 404) {
                        " — السيرفر قديم على Render (حدّثي server.py)"
                    } else ""
                    throw IllegalStateException("فشل التسجيل (${response.code})$hint: $text")
                }
                val parsed = gson.fromJson(text, RegisterChildResponse::class.java)
                Log.i(TAG, "register-child-device OK serverCode=${parsed.childCode} emailSent=${parsed.emailSent}")
                parsed
            }
        } catch (e: Exception) {
            throw IllegalStateException(ServerConnectionHelper.friendlyMessage(e), e)
        }
    }

    enum class ChildRegistrationState {
        LINKED,
        WAITING,
        NOT_ON_SERVER,
        ERROR,
    }

    data class ChildLinkStatus(
        val state: ChildRegistrationState,
        val detail: String = "",
        val errorKind: ServerConnectionHelper.ErrorKind = ServerConnectionHelper.ErrorKind.OTHER,
    )

    /** حالة تسجيل/ربط الطفل على السيرفر مع رسالة تفصيلية عند الفشل. */
    fun queryChildLinkStatus(childCode: String): ChildLinkStatus {
        val base = ServerConfig.rootHttpUrl()
            ?: return ChildLinkStatus(
                ChildRegistrationState.ERROR,
                "عنوان السيرفر غير صالح",
                ServerConnectionHelper.ErrorKind.SERVER_ERROR,
            )
        val code = ChildCodeNormalizer.forApi(childCode)
        val url = base.newBuilder()
            .addPathSegments("child-link-status")
            .addQueryParameter("child_code", code)
            .build()
        Log.i(TAG, "child-link-status → apiCode=$code url=$url")
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", ServerConfig.apiKey)
            .get()
            .build()
        return try {
            val response = executeWithRetry(request)
            response.use { r ->
                if (r.code == 404) {
                    Log.w(TAG, "child-link-status NOT_ON_SERVER code=$code")
                    return ChildLinkStatus(
                        ChildRegistrationState.NOT_ON_SERVER,
                        "كود الطفل غير مسجّل على السيرفر — سجّلي من جوال الطفل أولاً",
                        ServerConnectionHelper.ErrorKind.INVALID_CHILD_CODE,
                    )
                }
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    return ChildLinkStatus(
                        ChildRegistrationState.ERROR,
                        "تعذّر فحص السيرفر (HTTP ${r.code})",
                        ServerConnectionHelper.ErrorKind.SERVER_ERROR,
                    )
                }
                val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
                val json: Map<String, Any?> = gson.fromJson(text, mapType)
                val linked = json["linked"] == true
                Log.i(TAG, "child-link-status OK code=$code linked=$linked")
                if (linked) {
                    ChildLinkStatus(ChildRegistrationState.LINKED)
                } else {
                    ChildLinkStatus(ChildRegistrationState.WAITING, "الطفل مسجّل — أكملي رمز الربط")
                }
            }
        } catch (e: Exception) {
            val kind = ServerConnectionHelper.classify(e)
            ChildLinkStatus(
                ChildRegistrationState.ERROR,
                ServerConnectionHelper.messageFor(kind, e),
                kind,
            )
        }
    }

    /** حالة تسجيل/ربط الطفل على السيرفر. */
    fun queryChildRegistrationState(childCode: String): ChildRegistrationState =
        queryChildLinkStatus(childCode).state

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

    /** رفع استخدام — يُفضَّل OutboxRepository.submitUsage. */
    fun uploadUsageSync(childCode: String, secondsByPackage: Map<String, Long>): Boolean {
        if (secondsByPackage.isEmpty()) return true
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return false
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        val entries = secondsByPackage.map { (pkg, sec) ->
            mapOf("package" to pkg, "day" to day, "seconds" to sec)
        }
        val payload = mapOf("child_code" to childCode, "entries" to entries)
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

    /** تقرير استخدام أسبوعي — قائمة مرتبة من الأكثر استخداماً. */
    fun fetchWeeklyUsageList(childCode: String): List<UsageAppItem> {
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return emptyList()
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
        if (!response.isSuccessful || body.isBlank()) return emptyList()
        val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any?>>() {}.type
        val json: Map<String, Any?> = gson.fromJson(body, mapType)
        @Suppress("UNCHECKED_CAST")
        val apps = json["apps"] as? List<Map<String, Any?>> ?: return emptyList()
        return apps.mapNotNull { row ->
            val pkg = row["package_name"]?.toString()?.trim().orEmpty()
            if (pkg.isEmpty()) return@mapNotNull null
            val sec = (row["total_seconds"] as? Number)?.toLong() ?: 0L
            UsageAppItem(packageName = pkg, totalSeconds = sec)
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

    /** نبضة اتصال من جهاز الطفل — تتضمن حالة الصلاحيات ونسبة البطارية. */
    fun postChildHeartbeat(
        childCode: String,
        permissions: Map<String, Any?>? = null,
        batteryPct: Int? = null,
    ): Boolean {
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
        if (batteryPct != null && batteryPct in 0..100) {
            payload["battery_pct"] = batteryPct
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
        val code = ChildCodeNormalizer.forApi(childCode)
        if (code.isBlank()) return null
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return null
        val url = base.newBuilder()
            .addPathSegments("get-command")
            .addQueryParameter("child_code", code)
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
                        .header("X-API-KEY", ServerConfig.apiKey)
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
            // Render free tier may cold-start 60–90s; 30s caused "timeout" on first request.
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    private fun buildApi(): ParentPolicyApi {
        val client = client()
        return Retrofit.Builder()
            .baseUrl(ServerConfig.baseApiUrl)
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
        val base = ServerConfig.rootHttpUrl()
            ?: throw IllegalStateException("عنوان السيرفر غير صالح")
        val url = base.newBuilder().addPathSegments(path.trim('/')).build()
        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .header("X-API-KEY", ServerConfig.apiKey)
            .post(body)
            .build()
        return try {
            executeWithRetry(request).use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val bodyForParse = text.ifBlank { """{"message":"HTTP ${response.code}"}""" }
                    throw IllegalStateException("HTTP ${response.code}: $bodyForParse")
                }
                text
            }
        } catch (e: Exception) {
            throw IllegalStateException(ServerConnectionHelper.friendlyMessage(e), e)
        }
    }

    private fun executeWithRetry(request: Request): okhttp3.Response {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return client().newCall(request).execute()
            } catch (e: Exception) {
                lastError = e as? Exception ?: Exception(e)
                val kind = ServerConnectionHelper.classify(e)
                val retryable = kind == ServerConnectionHelper.ErrorKind.DNS_OR_SERVER ||
                    kind == ServerConnectionHelper.ErrorKind.SERVER_TIMEOUT
                if (retryable && attempt < 2) {
                    Thread.sleep(2_500L)
                } else {
                    throw lastError!!
                }
            }
        }
        throw lastError ?: IOException("network")
    }
}
