package com.example.myrana.sync

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.data.remote.dto.RegisterChildRequest
import com.example.myrana.identity.ChildIdentity
import com.example.myrana.session.ChildSession
import com.example.myrana.util.ChildCodeNormalizer
import com.example.myrana.util.ServerConnectionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * يحافظ على اتساق الربط بين الجوال والسيرفر.
 * إذا فُقد التسجيل على Render (قاعدة بيانات مُصفَّرة) يُعاد التسجيل تلقائياً.
 */
object LinkStateGuard {

    enum class Status {
        OK,
        WAITING_PARENT,
        REREGISTERED,
        SKIPPED,
        FAILED,
    }

    suspend fun ensureServerRegistration(context: Context): Status = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!ChildSession.isSetupComplete(app)) return@withContext Status.SKIPPED

        val display = ChildIdentity.displayCode(app)
            ?: return@withContext Status.FAILED

        when (NetworkModule.queryChildRegistrationState(display)) {
            NetworkModule.ChildRegistrationState.LINKED,
            NetworkModule.ChildRegistrationState.WAITING,
            -> Status.OK

            NetworkModule.ChildRegistrationState.NOT_ON_SERVER -> {
                if (reregister(app, display)) Status.REREGISTERED else Status.FAILED
            }

            NetworkModule.ChildRegistrationState.ERROR -> Status.FAILED
        }
    }

    private suspend fun reregister(context: Context, displayCode: String): Boolean {
        val email = ChildSession.childEmail(context)?.trim().orEmpty()
        if (email.isEmpty()) return false

        val serverCheck = ServerConnectionHelper.checkConnectivity(context)
        if (!serverCheck.ok) return false

        val suffix = ChildCodeNormalizer.forApi(displayCode)
        val codeForRegister = suffix.ifBlank {
            UUID.randomUUID().toString().take(8).uppercase()
        }
        val deviceName = Build.MODEL.ifBlank { "Android" }
        val androidVersion = "Android ${Build.VERSION.RELEASE}"

        return try {
            val response = NetworkModule.registerChildDevice(
                RegisterChildRequest(
                    childCode = codeForRegister,
                    childEmail = email,
                    deviceName = deviceName,
                    androidVersion = androidVersion,
                    androidDeviceId = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ANDROID_ID,
                    ).orEmpty(),
                ),
            )
            val serverDisplay = response.childCode?.trim().orEmpty()
            val bound = serverDisplay.ifBlank { ChildCodeNormalizer.normalize("CHILD-$codeForRegister") }
            ChildIdentity.bind(context, bound)
            ChildSession.updateChildCode(context, bound)
            true
        } catch (_: Exception) {
            false
        }
    }
}
