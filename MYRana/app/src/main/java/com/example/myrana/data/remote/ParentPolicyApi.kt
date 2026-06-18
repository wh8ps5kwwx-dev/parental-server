package com.example.myrana.data.remote

import com.example.myrana.data.remote.dto.PolicyEnvelopeDto
import com.example.myrana.data.remote.dto.PolicyPushDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * عقد Retrofit لسياسة الحظر على السيرفر (مسارات تحت `/api/`).
 *
 * السيرفر: `E:\parent_monitor_project\server.py` على Render.
 * تطبيق الأم يرسل `/send-command`؛ السيرفر يحدّث جدول السياسة تلقائياً.
 */
interface ParentPolicyApi {

    /**
     * سحب القوائم الكاملة المحظورة لجهاز الطفل.
     * @param deviceId نفس `child_code` المخزَّن في [DeviceIdentity].
     */
    @GET("v1/devices/{deviceId}/policy")
    suspend fun fetchPolicy(
        @Path("deviceId") deviceId: String,
        @retrofit2.http.Query("since_revision") sinceRevision: Long? = null,
    ): PolicyEnvelopeDto

    /**
     * رفع المواقع/الحزم المعلّقة محلياً ودمجها على السيرفر.
     */
    @POST("v1/devices/{deviceId}/policy/push")
    suspend fun pushPolicy(
        @Path("deviceId") deviceId: String,
        @Body body: PolicyPushDto
    ): PolicyEnvelopeDto
}
