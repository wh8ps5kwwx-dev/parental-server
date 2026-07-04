package com.example.myrana.parent

import android.content.Context
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.util.ChildCodeNormalizer

/**
 * مزامنة حالة الربط من السيرفر — **لا يمسح الربط المحلي** عند فقدان بيانات Render.
 * الربط يُحفظ على جوال الأم؛ السيرفر يُستعاد في الخلفية عبر restore-link.
 */
object ParentLinkSync {

    enum class Result {
        OK,
        NOT_LOGGED_IN,
        NOT_LINKED_LOCALLY,
        RESTORED,
        /** السيرفر نسي مؤقتاً — الربط المحلي يبقى، نعيد المحاولة لاحقاً */
        PENDING_RESTORE,
        NETWORK_ERROR,
    }

    fun refreshFromServer(context: Context): Result {
        val email = ParentSession.guardianEmail(context)?.trim().orEmpty()
        if (email.isEmpty()) return Result.NOT_LOGGED_IN
        if (!ParentSession.isChildLinked(context)) return Result.NOT_LINKED_LOCALLY

        val localCode = ParentSession.childCode(context)?.let { ChildCodeNormalizer.normalize(it) }
            ?: return Result.NOT_LINKED_LOCALLY

        return when (val api = GuardianApi.fetchLinkedChildren(email)) {
            is GuardianApi.ApiResult.ChildrenList -> {
                if (api.children.isEmpty()) {
                    return tryRestoreLink(context, email, localCode)
                }
                val found = api.children.any { row ->
                    ChildCodeNormalizer.normalize(row["child_code"]?.toString().orEmpty())
                        .equals(localCode, ignoreCase = true)
                }
                if (!found) {
                    adoptFirstChildFromServer(context, api.children)
                }
                Result.OK
            }
            is GuardianApi.ApiResult.Error -> Result.NETWORK_ERROR
            else -> Result.NETWORK_ERROR
        }
    }

    private fun tryRestoreLink(context: Context, email: String, localCode: String): Result {
        val token = ParentSession.restoreToken(context, localCode)
        if (token.isNullOrBlank()) {
            // لا restore_token بعد — نبقي الربط محلياً (ربط سابق قبل v1.5.5)
            return Result.PENDING_RESTORE
        }
        val name = ParentSession.childName(context).orEmpty().ifBlank { "طفل" }
        return when (
            GuardianApi.restoreLink(
                guardianEmail = email,
                childCode = localCode,
                restoreToken = token,
                name = name,
                age = ParentSession.childAge(context),
                guardianRole = ParentSession.guardianRole(context),
            )
        ) {
            is GuardianApi.ApiResult.LinkSuccess,
            is GuardianApi.ApiResult.Ok,
            -> Result.RESTORED
            else -> Result.PENDING_RESTORE
        }
    }

    private fun adoptFirstChildFromServer(
        context: Context,
        children: List<Map<String, Any?>>,
    ) {
        val row = children.firstOrNull() ?: return
        val code = ChildCodeNormalizer.normalize(row["child_code"]?.toString().orEmpty())
        if (code.isBlank()) return
        val name = row["name"]?.toString()?.ifBlank { "طفل" } ?: "طفل"
        val age = (row["age"] as? Number)?.toInt() ?: ParentSession.childAge(context)
        ParentSession.saveLinkedChild(context, code, name, age)
    }
}
