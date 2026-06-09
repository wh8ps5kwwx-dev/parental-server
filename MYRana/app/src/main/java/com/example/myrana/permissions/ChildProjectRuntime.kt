package com.example.myrana.permissions

import android.content.Context
import com.example.myrana.academy.AcademyPythonBridge
import com.example.myrana.session.ChildSession
import com.example.myrana.sync.BackgroundMonitoring

/**
 * تشغيل أجزاء مشروع الطفل — **المراقبة مستقلة عن اللعب**.
 *
 * المراقبة (حظر + Chrome + تقارير) تعمل حتى لو الطفل:
 * - أغلق الأكاديمية
 * - يستخدم تطبيقات أخرى
 * - أزال التطبيق من القائمة الحديثة
 */
object ChildProjectRuntime {

    fun snapshot(context: Context): SystemPermissions.Snapshot =
        SystemPermissions.readSnapshot(context)

    fun isMonitoringOperational(context: Context): Boolean =
        ChildSession.isSetupComplete(context) && snapshot(context).mandatoryReady

    fun isChildStackReady(context: Context): Boolean =
        ChildPermissionEvaluator.canEnterGame(context)

    /**
     * المراقبة بالخلفية — **بدون فتح اللعبة**.
     * WorkManager + خدمة أمامية + Accessibility (من النظام).
     */
    fun activateMonitoring(context: Context) {
        if (!isMonitoringOperational(context)) return
        BackgroundMonitoring.ensureRunning(context.applicationContext)
    }

    /** محرك Python للأكاديمية فقط — عند الدخول للعب. */
    fun activateAcademyEngine(context: Context) {
        if (!isMonitoringOperational(context)) return
        AcademyPythonBridge.ensureStarted(context.applicationContext)
    }

    /** مراقبة + محرك اللعبة (عند فتح الأكاديمية). */
    fun activateChildStack(context: Context) {
        activateMonitoring(context)
        activateAcademyEngine(context)
    }
}
