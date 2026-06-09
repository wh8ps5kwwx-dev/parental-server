package com.example.myrana.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myrana.R
import com.example.myrana.screentime.UsageLevel

/** تنبيه أصفر أو أحمر عند اقتراب حد وقت الاستخدام. */
class ScreenTimeWarningActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_time_warning)

        val levelName = intent.getStringExtra(EXTRA_LEVEL).orEmpty()
        val minutesUsed = intent.getIntExtra(EXTRA_MINUTES_USED, 0)
        val blockMinutes = intent.getIntExtra(EXTRA_BLOCK_MINUTES, 120)
        val level = runCatching { UsageLevel.valueOf(levelName) }.getOrDefault(UsageLevel.YELLOW)

        val dot = findViewById<View>(R.id.indicatorDot)
        val title = findViewById<TextView>(R.id.textWarningTitle)
        val message = findViewById<TextView>(R.id.textWarningMessage)

        when (level) {
            UsageLevel.YELLOW -> {
                dot.setBackgroundColor(Color.parseColor("#FFC107"))
                title.text = getString(R.string.screen_time_warn_yellow_title)
                message.text = getString(
                    R.string.screen_time_warn_yellow_msg,
                    minutesUsed,
                    blockMinutes,
                )
            }
            UsageLevel.RED -> {
                dot.setBackgroundColor(Color.parseColor("#F44336"))
                title.text = getString(R.string.screen_time_warn_red_title)
                message.text = getString(
                    R.string.screen_time_warn_red_msg,
                    minutesUsed,
                    blockMinutes,
                )
            }
            else -> finish()
            return
        }

        findViewById<Button>(R.id.btnWarningOk).setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_PACKAGE = "screen_pkg"
        const val EXTRA_LEVEL = "screen_level"
        const val EXTRA_MINUTES_USED = "screen_minutes_used"
        const val EXTRA_BLOCK_MINUTES = "screen_block_minutes"
    }
}
