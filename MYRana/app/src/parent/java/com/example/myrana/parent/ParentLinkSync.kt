package com.example.myrana.parent

import android.content.Context
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.util.ChildCodeNormalizer

/**
 * مزامنة حالة الربط من السيرفر إلى [ParentSession] — يمنع اعتقاد الأم أن الطفل مربوط
 * بينما السيرفر فقد السجل (مثلاً بعد إعادة تشغيل Render).
 * عند الفقدان: يحاول استعادة الربط تلقائياً عبر [GuardianApi.restoreLink].
 */
object ParentLinkSync {

    enum class Result {
        OK,
        NOT_LOGGED_IN,
        NOT_LINKED_LOCALLY,
        RESTORED,
        STALE_CLEARED,
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
                if (!ParentSession.isChildLinked(context)) {
                    adoptFirstChildFromServer(context, api.children)
                    return Result.OK
                }
                val found = api.children.any { row ->
                    val serverCode = ChildCodeNormalizer.normalize(
                        row["child_code"]?.toString().orEmpty(),
                    )
                    serverCode.equals(localCode, ignoreCase = true)
                }
                if (!found) {
                    adoptFirstChildFromServer(context, api.children)
                    Result.OK
                } else {
                    Result.OK
                }
            }
            is GuardianApi.ApiResult.Error -> Result.NETWORK_ERROR
            else -> Result.NETWORK_ERROR
        }
    }

    private fun tryRestoreLink(context: Context, email: String, localCode: String): Result {
        val token = ParentSession.restoreToken(context, localCode)
        if (token.isNullOrBlank()) {
            ParentSession.markLinkStale(context)
            return Result.STALE_CLEARED
        }
        val name = ParentSession.childName(context).orEmpty().ifBlank { "طفل" }
        return when (
            GuardianApi.restoreLink(
                guardianEmail = email,
                childCode = localCode,
                restoreToken = token,
                name = name,
                age = ParentSession.pendingChildAge(context),
                guardianRole = ParentSession.guardianRole(context),
            )
        ) {
            is GuardianApi.ApiResult.LinkSuccess,
            is GuardianApi.ApiResult.Ok,
            -> Result.RESTORED
            else -> {
                ParentSession.markLinkStale(context)
                Result.STALE_CLEARED
            }
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
        ParentSession.saveLinkedChild(context, code, name)
    }
}
