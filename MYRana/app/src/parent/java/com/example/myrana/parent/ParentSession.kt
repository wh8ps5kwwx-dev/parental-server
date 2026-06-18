package com.example.myrana.parent

import android.content.Context

/**
 * حالة ولي الأمر محلياً (نكهة parent فقط).
 * تُحفظ بعد التحقق من البريد وربط الطفل.
 */
object ParentSession {

    private const val PREFS = "myrana_parent_session"
    private const val KEY_EMAIL = "guardian_email"
    private const val KEY_ROLE = "guardian_role"
    private const val KEY_VERIFIED = "email_verified"
    private const val KEY_CHILD_CODE = "child_code"
    private const val KEY_CHILD_NAME = "child_name"
    private const val KEY_LINKED = "child_linked"
    private const val KEY_PENDING_EMAIL_CODE = "pending_email_code"
    private const val KEY_PENDING_CHILD_CODE = "pending_link_child_code"
    private const val KEY_PENDING_CHILD_EMAIL = "pending_link_child_email"
    private const val KEY_PENDING_DEVICE_NAME = "pending_link_device_name"
    private const val KEY_PENDING_ANDROID_VERSION = "pending_link_android_version"
    private const val KEY_PENDING_DEVICE_VERIFY = "pending_link_device_verify"
    private const val KEY_DEVICE_VERIFIED = "device_link_verified"
    private const val KEY_PENDING_CHILD_NAME = "pending_child_name"
    private const val KEY_PENDING_CHILD_AGE = "pending_child_age"
    /** true أثناء ربط طفل إضافي — لا نمسح الطفل النشط الحالي */
    private const val KEY_ADDING_ANOTHER = "adding_another_child"
    private const val KEY_WELCOME_SEEN = "welcome_seen"
    /** قائمة الأطفال المرتبطين محلياً — code|name;code|name */
    private const val KEY_LINKED_CHILDREN = "linked_children_cache"

    fun guardianEmail(context: Context): String? =
        prefs(context).getString(KEY_EMAIL, null)

    fun guardianRole(context: Context): String =
        prefs(context).getString(KEY_ROLE, "ولي أمر") ?: "ولي أمر"

    fun isEmailVerified(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VERIFIED, false)

    fun isChildLinked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LINKED, false)

    fun childCode(context: Context): String? =
        prefs(context).getString(KEY_CHILD_CODE, null)

    fun childName(context: Context): String? =
        prefs(context).getString(KEY_CHILD_NAME, null)

    fun isDeviceLinkVerified(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEVICE_VERIFIED, false)

    fun saveVerifiedDevice(
        context: Context,
        childCode: String,
        childEmail: String,
        deviceName: String,
        androidVersion: String,
        deviceVerifyCode: String = "",
    ) {
        prefs(context).edit()
            .putString(KEY_PENDING_CHILD_CODE, childCode.trim())
            .putString(KEY_PENDING_CHILD_EMAIL, childEmail.trim())
            .putString(KEY_PENDING_DEVICE_NAME, deviceName.trim())
            .putString(KEY_PENDING_ANDROID_VERSION, androidVersion.trim())
            .putString(KEY_PENDING_DEVICE_VERIFY, deviceVerifyCode.trim())
            .putBoolean(KEY_DEVICE_VERIFIED, true)
            .apply()
    }

    fun pendingDeviceVerifyCode(context: Context): String? =
        prefs(context).getString(KEY_PENDING_DEVICE_VERIFY, null)

    fun pendingLinkChildCode(context: Context): String? =
        prefs(context).getString(KEY_PENDING_CHILD_CODE, null)

    fun pendingLinkChildEmail(context: Context): String? =
        prefs(context).getString(KEY_PENDING_CHILD_EMAIL, null)

    fun pendingLinkDeviceName(context: Context): String? =
        prefs(context).getString(KEY_PENDING_DEVICE_NAME, null)

    fun pendingLinkAndroidVersion(context: Context): String? =
        prefs(context).getString(KEY_PENDING_ANDROID_VERSION, null)

    fun clearPendingLink(context: Context) {
        prefs(context).edit()
            .remove(KEY_PENDING_CHILD_CODE)
            .remove(KEY_PENDING_CHILD_EMAIL)
            .remove(KEY_PENDING_DEVICE_NAME)
            .remove(KEY_PENDING_ANDROID_VERSION)
            .remove(KEY_PENDING_DEVICE_VERIFY)
            .putBoolean(KEY_DEVICE_VERIFIED, false)
            .apply()
    }

    /** يمسح كود CHILD خاطئ (مثل البريد المحوّل) من الذاكرة */
    fun sanitizeInvalidPendingCodes(context: Context) {
        val pending = pendingLinkChildCode(context)?.trim().orEmpty()
        if (pending.isNotEmpty() && !com.example.myrana.util.ChildCodeNormalizer.isValid(pending)) {
            clearPendingLink(context)
        }
    }

    fun saveGuardian(context: Context, email: String, role: String) {
        prefs(context).edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_ROLE, role.trim())
            .apply()
    }

    fun pendingEmailCode(context: Context): String? =
        prefs(context).getString(KEY_PENDING_EMAIL_CODE, null)

    fun savePendingEmailCode(context: Context, code: String) {
        prefs(context).edit().putString(KEY_PENDING_EMAIL_CODE, code.trim()).apply()
    }

    fun markEmailVerified(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_VERIFIED, true)
            .remove(KEY_PENDING_EMAIL_CODE)
            .apply()
    }

    fun isAddingAnotherChild(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ADDING_ANOTHER, false)

    fun hasSeenWelcome(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WELCOME_SEEN, false)

    fun markWelcomeSeen(context: Context) {
        prefs(context).edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
    }

    fun saveLinkedChild(context: Context, childCode: String, childName: String) {
        prefs(context).edit()
            .putString(KEY_CHILD_CODE, childCode.trim())
            .putString(KEY_CHILD_NAME, childName.trim())
            .putBoolean(KEY_LINKED, true)
            .putBoolean(KEY_ADDING_ANOTHER, false)
            .remove(KEY_PENDING_CHILD_NAME)
            .remove(KEY_PENDING_CHILD_AGE)
            .apply()
        clearPendingLink(context)
        mergeLinkedChild(context, childCode, childName)
    }

    /** كل الأطفال المرتبطين (من السيرفر أو الذاكرة المحلية). */
    fun linkedChildrenCached(context: Context): List<Pair<String, String>> {
        val raw = prefs(context).getString(KEY_LINKED_CHILDREN, null)?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            return raw.split(";").mapNotNull { part ->
                val sep = part.indexOf('|')
                if (sep <= 0) return@mapNotNull null
                val code = part.substring(0, sep).trim()
                val name = part.substring(sep + 1).trim().ifBlank { "طفل" }
                if (code.isBlank()) null else code to name
            }
        }
        val code = childCode(context)?.trim().orEmpty()
        if (code.isBlank()) return emptyList()
        return listOf(code to childName(context).orEmpty().ifBlank { "طفل" })
    }

    fun saveLinkedChildrenCache(context: Context, children: List<Pair<String, String>>) {
        if (children.isEmpty()) {
            prefs(context).edit().remove(KEY_LINKED_CHILDREN).apply()
            return
        }
        val serialized = children.joinToString(";") { (code, name) ->
            "${code.trim()}|${name.trim().replace(";", " ")}"
        }
        prefs(context).edit().putString(KEY_LINKED_CHILDREN, serialized).apply()
    }

    fun mergeLinkedChild(context: Context, childCode: String, childName: String) {
        val code = childCode.trim()
        if (code.isBlank()) return
        val name = childName.trim().ifBlank { "طفل" }
        val norm = com.example.myrana.util.ChildCodeNormalizer.forApi(code)
        val list = linkedChildrenCached(context).toMutableList()
        val idx = list.indexOfFirst {
            com.example.myrana.util.ChildCodeNormalizer.forApi(it.first) == norm
        }
        if (idx >= 0) list[idx] = code to name else list.add(code to name)
        saveLinkedChildrenCache(context, list)
    }

    fun linkedChildCount(context: Context): Int = linkedChildrenCached(context).size

    /** بدء ربط طفل جديد مع الإبقاء على الأطفال المرتبطين سابقاً. */
    fun beginAnotherChildLink(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ADDING_ANOTHER, true)
            .remove(KEY_PENDING_CHILD_NAME)
            .remove(KEY_PENDING_CHILD_AGE)
            .apply()
        clearPendingLink(context)
    }

    fun cancelAnotherChildLink(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ADDING_ANOTHER, false)
            .apply()
        clearPendingLink(context)
    }

    fun savePendingChildProfile(context: Context, name: String, age: Int) {
        prefs(context).edit()
            .putString(KEY_PENDING_CHILD_NAME, name.trim())
            .putInt(KEY_PENDING_CHILD_AGE, age.coerceIn(3, 18))
            .apply()
    }

    fun pendingChildName(context: Context): String? =
        prefs(context).getString(KEY_PENDING_CHILD_NAME, null)

    fun pendingChildAge(context: Context): Int =
        prefs(context).getInt(KEY_PENDING_CHILD_AGE, 10)

    fun hasPendingChildProfile(context: Context): Boolean =
        !pendingChildName(context).isNullOrBlank()

    /** السيرفر لا يعرف أي طفل — مسح كامل. */
    fun markLinkStale(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_LINKED, false)
            .putBoolean(KEY_ADDING_ANOTHER, false)
            .remove(KEY_CHILD_CODE)
            .remove(KEY_CHILD_NAME)
            .remove(KEY_PENDING_CHILD_NAME)
            .remove(KEY_PENDING_CHILD_AGE)
            .remove(KEY_LINKED_CHILDREN)
            .apply()
        clearPendingLink(context)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
