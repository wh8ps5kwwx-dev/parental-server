package com.example.myrana.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object AppIconHelper {

    fun toBase64Png(drawable: Drawable, sizeDp: Int, density: Float): String? {
        val sizePx = (sizeDp * density).roundToInt().coerceAtLeast(32)
        val bitmap = drawableToBitmap(drawable, sizePx)
        return compressToBase64(bitmap)
    }

    fun fromBase64Png(data: String): Bitmap? {
        val trimmed = data.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val bytes = Base64.decode(trimmed, Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }

    private fun compressToBase64(bitmap: Bitmap): String? {
        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            null
        }
    }
}
