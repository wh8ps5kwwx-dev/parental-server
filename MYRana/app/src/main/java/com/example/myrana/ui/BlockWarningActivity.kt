package com.example.myrana.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myrana.R

/** شاشة تحذير عند محاولة فتح تطبيق محظور أو مجمّد. */
class BlockWarningActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_warning)

        val pkg = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty()
        val message = when (reason) {
            REASON_SITE -> getString(R.string.block_warning_site, pkg.ifBlank { "—" })
            REASON_YOUTUBE -> getString(R.string.block_warning_youtube, pkg.ifBlank { "—" })
            else -> getString(R.string.block_warning_message, pkg.ifBlank { "—" })
        }
        findViewById<TextView>(R.id.textBlockMessage).text = message

        findViewById<Button>(R.id.btnBlockOk).setOnClickListener { finish() }
    }

    companion object {
        const val EXTRA_PACKAGE = "blocked_package"
        const val EXTRA_REASON = "blocked_reason"
        const val REASON_SITE = "site"
        const val REASON_YOUTUBE = "youtube"
    }
}
