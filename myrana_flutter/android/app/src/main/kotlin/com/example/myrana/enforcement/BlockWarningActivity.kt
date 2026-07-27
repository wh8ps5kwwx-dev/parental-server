package com.example.myrana.enforcement

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * شاشة بسيطة تُظهر سبب الحظر (بدون XML/Strings).
 */
class BlockWarningActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        val message = when (reason) {
            REASON_SITE -> "تم حظر موقع: ${pkg.ifBlank { "—" }}"
            REASON_YOUTUBE -> "تم حظر فيديو/كلمة على YouTube: ${pkg.ifBlank { "—" }}"
            REASON_TIME -> "تم تطبيق حد وقت الشاشة على: ${pkg.ifBlank { "—" }}"
            else -> "تم حظر: ${pkg.ifBlank { "—" }}"
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }

        val tv = TextView(this).apply {
            text = message
            textSize = 18f
        }

        val btn = Button(this).apply {
            text = "تم"
            setOnClickListener { finish() }
        }

        root.addView(tv)
        root.addView(btn)
        setContentView(root)
    }

    companion object {
        const val EXTRA_PACKAGE = "blocked_package"
        const val EXTRA_REASON = "blocked_reason"

        const val REASON_SITE = "site"
        const val REASON_YOUTUBE = "youtube"
        const val REASON_TIME = "time"
    }
}

