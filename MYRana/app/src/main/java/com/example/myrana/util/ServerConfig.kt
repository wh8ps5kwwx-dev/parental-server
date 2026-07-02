package com.example.myrana.util

import com.example.myrana.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * عنوان السيرفر الموحّد — يُقرأ من build.gradle (BuildConfig).
 * لا تستخدم بيانات وهمية: كل الربط عبر API على Render.
 */
object ServerConfig {

    val rootUrl: String = BuildConfig.SERVER_ROOT_URL.trim().let { if (it.endsWith("/")) it else "$it/" }

    val baseApiUrl: String = BuildConfig.SERVER_BASE_URL.trim().let { if (it.endsWith("/")) it else "$it/" }

    val apiKey: String = BuildConfig.API_KEY

    fun rootHttpUrl() = rootUrl.toHttpUrlOrNull()

    fun healthUrl(): String = "${rootUrl.trimEnd('/')}/health"

    fun isConfigured(): Boolean = rootHttpUrl() != null
}
