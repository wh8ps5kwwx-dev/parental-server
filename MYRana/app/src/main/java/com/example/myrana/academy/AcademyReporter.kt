package com.example.myrana.academy

import android.content.Context
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.session.ChildSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** إرسال تقارير اللعب للسيرفر — مثل `app.send_report` في Python. */
object AcademyReporter {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sendReport(context: Context, event: String, value: String) {
        val childCode = ChildSession.childCode(context.applicationContext) ?: return
        scope.launch {
            try {
                NetworkModule.postRoot(
                    "add-report",
                    mapOf(
                        "event" to event,
                        "value" to value,
                        "child_code" to childCode,
                    ),
                )
            } catch (_: Exception) {
            }
        }
    }

    fun sendAlert(context: Context, message: String) {
        val childCode = ChildSession.childCode(context.applicationContext) ?: return
        scope.launch {
            NetworkModule.postAlertSync(childCode, message)
        }
    }
}
