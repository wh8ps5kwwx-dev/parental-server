package com.example.myrana_flutter.native

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import java.io.ByteArrayOutputStream

object InstalledAppsCollector {

    fun collect(context: Context, maxApps: Int = 120): List<Map<String, String?>> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(launcher, PackageManager.GET_META_DATA)
        val own = context.packageName
        val seen = linkedSetOf<String>()
        val result = mutableListOf<Map<String, String?>>()
        for (info in activities) {
            val pkg = info.activityInfo?.packageName?.trim().orEmpty()
            if (pkg.isBlank() || pkg == own || pkg.startsWith("com.example.myrana") || !seen.add(pkg)) {
                continue
            }
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg.substringAfterLast('.')
            }
            val iconB64 = try {
                toBase64Png(pm.getApplicationIcon(pkg))
            } catch (_: Exception) {
                null
            }
            result.add(
                mapOf(
                    "package_name" to pkg,
                    "app_label" to label,
                    "icon_b64" to iconB64,
                ),
            )
            if (result.size >= maxApps) break
        }
        return result
    }

    private fun toBase64Png(drawable: Drawable): String? {
        val bitmap = when (drawable) {
            is BitmapDrawable -> drawable.bitmap
            else -> {
                val w = drawable.intrinsicWidth.coerceAtLeast(1)
                val h = drawable.intrinsicHeight.coerceAtLeast(1)
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                }
            }
        }
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
