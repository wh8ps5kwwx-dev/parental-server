package com.example.myrana.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * استجابة JSON من مسار `GET …/policy` أو `POST …/policy/push`.
 * الحقول اختيارية لأن السيرفر قد يعيد قوائم فارغة.
 */
data class PolicyEnvelopeDto(
    /** رقم مراجعة يزيد عند كل تغيير على السيرفر (للمقارنة لاحقاً). */
    @SerializedName("revision") val revision: Long?,
    @SerializedName("blockedHosts") val blockedHosts: List<String>?,
    @SerializedName("blockedPackages") val blockedPackages: List<String>?,
    @SerializedName("videoKeywords") val videoKeywords: List<String>?
)

/**
 * جسم طلب الرفع: يجمع كل الصفوف ذات `pending_upload` محلياً قبل الإرسال.
 */
data class PolicyPushDto(
    @SerializedName("blockedHosts") val blockedHosts: List<String>,
    @SerializedName("blockedPackages") val blockedPackages: List<String>
)
