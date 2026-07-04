package com.example.myrana.parent.ui

import android.content.Context
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.parent.ParentSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object InstalledAppsLoader {

    sealed class Result {
        data class Success(val items: List<GuardianApi.InstalledAppItem>, val count: Int) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun load(context: Context): Result = withContext(Dispatchers.IO) {
        val childCode = ParentSession.childCode(context)?.trim().orEmpty()
        val email = ParentSession.guardianEmail(context)?.trim().orEmpty()
        if (childCode.isBlank() || email.isBlank()) {
            return@withContext Result.Error("اربط الطفل أولاً")
        }
        when (val request = GuardianApi.requestInstalledAppsSync(childCode, email)) {
            is GuardianApi.ApiResult.Error -> return@withContext Result.Error(request.message)
            else -> Unit
        }
        delay(5_000L)
        when (val fetch = GuardianApi.fetchInstalledApps(childCode)) {
            is GuardianApi.ApiResult.InstalledApps -> {
                if (fetch.items.isEmpty()) {
                    Result.Error("لا توجد تطبيقات — تأكدي أن جوال الطفل متصل")
                } else {
                    Result.Success(fetch.items, fetch.count)
                }
            }
            is GuardianApi.ApiResult.Error -> Result.Error(fetch.message)
            else -> Result.Error("فشل جلب قائمة التطبيقات")
        }
    }
}
