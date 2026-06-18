package com.example.myrana.util

import android.content.Context
import com.example.myrana.network.NetworkMonitor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * فحص وإيقاظ السيرفر على Render + رسائل عربية واضحة لأخطاء الشبكة/DNS.
 */
object ServerConnectionHelper {

    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 2_500L

    enum class ErrorKind {
        NO_INTERNET,
        DNS_OR_SERVER,
        SERVER_TIMEOUT,
        INVALID_CHILD_CODE,
        INVALID_LINK_CODE,
        SERVER_ERROR,
        OTHER,
    }

    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    data class CheckResult(
        val ok: Boolean,
        val message: String,
        val kind: ErrorKind = if (ok) ErrorKind.OTHER else ErrorKind.DNS_OR_SERVER,
    )

    /** يُستدعى قبل الربط — يتحقق من الإنترنت ثم يُعيد المحاولة حتى 3 مرات. */
    fun checkConnectivity(context: Context): CheckResult {
        if (!NetworkMonitor.isOnline(context)) {
            return CheckResult(
                ok = false,
                message = "لا يوجد إنترنت على الجهاز.\nفعّلي Wi‑Fi أو بيانات الجوال (4G/5G) ثم أعيدي المحاولة.",
                kind = ErrorKind.NO_INTERNET,
            )
        }
        return wakeServerAndCheck()
    }

    fun wakeServerAndCheck(): CheckResult {
        if (!ServerConfig.isConfigured()) {
            return CheckResult(
                ok = false,
                message = "عنوان السيرفر غير صالح في build.gradle",
                kind = ErrorKind.SERVER_ERROR,
            )
        }

        val healthUrl = ServerConfig.rootHttpUrl()!!.newBuilder().addPathSegments("health").build()
        var lastError: Throwable? = null

        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val request = Request.Builder()
                    .url(healthUrl)
                    .header("X-API-KEY", ServerConfig.apiKey)
                    .get()
                    .build()
                probeClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return CheckResult(
                            ok = true,
                            message = "السيرفر متاح — يمكنكِ متابعة الربط",
                        )
                    }
                    lastError = IOException("HTTP ${response.code}")
                }
            } catch (e: Exception) {
                lastError = e
            }
            if (attempt < MAX_ATTEMPTS - 1) {
                Thread.sleep(RETRY_DELAY_MS)
            }
        }
        val kind = classify(lastError ?: IOException("unknown"))
        return CheckResult(false, messageFor(kind, lastError), kind)
    }

    fun healthUrl(): String = ServerConfig.healthUrl()

    fun classify(t: Throwable?): ErrorKind {
        val chain = generateSequence(t) { it.cause }.toList()
        for (err in chain) {
            when (err) {
                is UnknownHostException -> return ErrorKind.DNS_OR_SERVER
                is SocketTimeoutException -> return ErrorKind.SERVER_TIMEOUT
            }
        }
        val raw = t?.message.orEmpty()
        if (raw.contains("Unable to resolve host", ignoreCase = true) ||
            raw.contains("No address associated with hostname", ignoreCase = true)
        ) {
            return ErrorKind.DNS_OR_SERVER
        }
        if (raw.contains("timeout", ignoreCase = true) ||
            raw.contains("timed out", ignoreCase = true)
        ) {
            return ErrorKind.SERVER_TIMEOUT
        }
        if (raw.contains("Child not found", ignoreCase = true) ||
            raw.contains("لم يُعثر", ignoreCase = true) ||
            raw.contains("غير مسجل", ignoreCase = true)
        ) {
            return ErrorKind.INVALID_CHILD_CODE
        }
        if (raw.contains("Invalid or expired verification", ignoreCase = true) ||
            raw.contains("invalid_verification", ignoreCase = true) ||
            raw.contains("رمز الربط غير صحيح", ignoreCase = true)
        ) {
            return ErrorKind.INVALID_LINK_CODE
        }
        return ErrorKind.OTHER
    }

    fun friendlyMessage(t: Throwable): String = messageFor(classify(t), t)

    fun messageFor(kind: ErrorKind, t: Throwable? = null): String {
        return when (kind) {
            ErrorKind.NO_INTERNET ->
                "لا يوجد إنترنت على الجهاز.\nفعّلي Wi‑Fi أو بيانات الجوال ثم أعيدي المحاولة."
            ErrorKind.DNS_OR_SERVER -> buildString {
                appendLine("تعذّر الوصول للسيرفر (DNS/شبكة).")
                appendLine("① تأكدي من الإنترنت على الجوال")
                appendLine("② عطّلي Private DNS إن كان مفعّلاً")
                appendLine("③ افتحي Chrome:")
                appendLine(healthUrl())
                appendLine("④ انتظري 60–90 ثانية (Render قد يكون نائماً)")
                append("⑤ اضغطي «فحص الاتصال» ثم أعيدي الربط")
            }
            ErrorKind.SERVER_TIMEOUT -> buildString {
                appendLine("السيرفر بطيء أو نائم على Render.")
                appendLine("① افتحي ${healthUrl()} في Chrome")
                appendLine("② انتظري حتى يظهر status ok")
                append("③ اضغطي «فحص الاتصال» ثم أعيدي إرسال رمز الربط")
            }
            ErrorKind.INVALID_CHILD_CODE ->
                "كود الطفل غير صحيح أو غير مسجّل.\nمن جوال الطفل: «تسجيل الجهاز» ثم انسخي CHILD-XXXXXXXX."
            ErrorKind.INVALID_LINK_CODE ->
                "رمز الربط غير صحيح أو منتهي.\nاضغطي «إرسال رمز الربط» واستخدمي آخر رمز من Gmail (6 أرقام)."
            ErrorKind.SERVER_ERROR ->
                "خطأ من السيرفر — حاولي لاحقاً أو اضغطي «فحص الاتصال»."
            ErrorKind.OTHER ->
                t?.message?.takeIf { it.isNotBlank() } ?: "خطأ في الاتصال — حاولي مرة أخرى"
        }
    }
}
