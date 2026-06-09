package com.example.myrana.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myrana.R

/** إغلاق تلقائي عند تجاوز الحد أو وقت النوم. */
class ScreenTimeLimitActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_time_limit)

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val minutes = intent.getIntExtra(EXTRA_MINUTES, 120)
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()

        val title = findViewById<TextView>(R.id.textLimitTitle)
        val message = findViewById<TextView>(R.id.textLimitMessage)

        if (reason == REASON_SLEEP) {
            title.text = getString(R.string.screen_time_sleep_title)
            message.text = getString(R.string.screen_time_sleep_msg, pkg.ifBlank { "—" })
        } else {
            title.text = getString(R.string.screen_time_limit_title)
            message.text = getString(R.string.screen_time_limit_msg, minutes, pkg.ifBlank { "—" })
        }

        findViewById<Button>(R.id.btnLimitOk).setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_PACKAGE = "screen_pkg"
        const val EXTRA_MINUTES = "screen_block_minutes"
        const val EXTRA_REASON = "screen_reason"
        const val REASON_SLEEP = "sleep"
    }
}
