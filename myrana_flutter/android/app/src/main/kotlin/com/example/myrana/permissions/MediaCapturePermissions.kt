package com.example.myrana.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * صلاحية الكاميرا والميكروفون — اختيارية.
 * تُطلب وتُبلَّغ حالتها لولي الأمر (رؤية الجاهزية)، وليست تصويرًا/تنصتًا صامتًا.
 */
object MediaCapturePermissions {

    val permissions: Array<String> = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    fun hasCamera(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasMicrophone(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
}
