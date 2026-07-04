package com.example.myrana.parent.ui

import android.content.Context
import android.widget.Toast
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.parent.ParentSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ParentControlHelper {

    fun requireChildCode(context: Context): String? {
        val code = ParentSession.childCode(context)?.takeIf { it.isNotBlank() }
        if (code == null) {
            Toast.makeText(context, "اربط الطفل أولاً", Toast.LENGTH_SHORT).show()
        }
        return code
    }

    fun sendCommand(
        scope: CoroutineScope,
        context: Context,
        action: String,
        value: String,
        onDone: ((message: String, isError: Boolean) -> Unit)? = null,
    ) {
        val childCode = ParentSession.childCode(context)
        val email = ParentSession.guardianEmail(context)
        if (childCode.isNullOrBlank() || email.isNullOrBlank()) {
            val msg = "اربط الطفل أولاً"
            onDone?.invoke(msg, true) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            return
        }
        if (action != "allow" && value.isBlank()) {
            val msg = "أدخل اسم الموقع أو الحزمة"
            onDone?.invoke(msg, true) ?: Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    GuardianApi.sendCommand(action, value, childCode, email)
                }
            ) {
                is GuardianApi.ApiResult.Ok -> {
                    onDone?.invoke(result.message, false)
                        ?: Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
                is GuardianApi.ApiResult.Error -> {
                    onDone?.invoke(result.message, true)
                        ?: Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
                else -> Unit
            }
        }
    }
}
