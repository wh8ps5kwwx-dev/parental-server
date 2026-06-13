package com.example.myrana.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * جسم `POST /register-child-device` — تسجيل جهاز الطفل على سيرفر parent_monitor_project.
 */
data class RegisterChildRequest(
    /** معرّف فريد يُولَّد تلقائياً (مثل CHILD-A1B2C3D4). */
    @SerializedName("child_code") val childCode: String,
    @SerializedName("child_email") val childEmail: String,
    @SerializedName("guardian_email") val guardianEmail: String = "",
    @SerializedName("parent_email") val parentEmail: String = "",
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("android_version") val androidVersion: String,
    /** معرّف الجهاز الحقيقي من أندرويد (ليس واجهة وهمية). */
    @SerializedName("android_device_id") val androidDeviceId: String = "",
)

/**
 * استجابة التسجيل؛ يحتوي `device_verify_code` لربط الأم بالجهاز.
 */
data class RegisterChildResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("message") val message: String?,
    @SerializedName("child_code") val childCode: String?,
    /** الرمز الذي تدخله الأم + الطفل — يُتحقق منه على السيرفر. */
    @SerializedName("device_verify_code") val deviceVerifyCode: String?,
    @SerializedName("verification_code") val verificationCode: String?,
    @SerializedName("delivery") val delivery: String?,
    /** true إذا أُرسل الرمز فعلياً بالبريد (لا يُعاد في JSON). */
    @SerializedName("email_sent") val emailSent: Boolean? = null,
    @SerializedName("dev_fallback") val devFallback: Boolean? = null,
)
