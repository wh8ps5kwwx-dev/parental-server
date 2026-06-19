// أضيفي هذه الدوال داخل object GuardianApi في GuardianApi.kt
// وأضيفي sealed class entries و data classes في نهاية الملف

/*
    fun fetchGuardianSettings(parentEmail: String): ApiResult { ... }
    fun saveGuardianSettings(parentEmail: String, settings: Map<String, Any?>): ApiResult { ... }
    fun fetchAuditLog(parentEmail: String, childCode: String?): ApiResult { ... }
    fun sendEmailSummary(parentEmail: String, childCode: String, period: String): ApiResult { ... }

    // في ChildDashboardData أضيفي:
    val permissionsOk: Boolean = false,
    val permissions: Map<String, Any?> = emptyMap(),

    // ApiResult:
    data class GuardianSettingsLoaded(val settings: Map<String, Any?>) : ApiResult()
    data class AuditLog(val lines: List<String>) : ApiResult()
*/

// انسخي المحتوى الكامل من patches/android/GuardianApi_methods.txt
