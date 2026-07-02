package com.example.myrana.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * أمر واحد من تطبيق الأم عبر `GET /get-command`.
 *
 * أمثلة `action`: `block_app`, `block_site`, `allow`, أو `none` إن لا يوجد أمر.
 * @param value اسم التطبيق أو الموقع حسب نوع الأمر.
 */
data class ParentCommandDto(
    @SerializedName("action") val action: String?,
    @SerializedName("value") val value: String?
)
