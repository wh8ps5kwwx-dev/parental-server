package com.example.myrana.parent.ui

import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.parent.ParentSession
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParentMessageActivity : ParentShellActivity() {

    override fun screenTitle(): String = getString(R.string.parent_hub_message)

    override fun contentLayoutId(): Int = R.layout.content_parent_message

    override fun onShellReady() {
        findViewById<MaterialButton>(R.id.btnSendMessage).setOnClickListener { sendMessage() }
    }

    private fun sendMessage() {
        val childCode = ParentControlHelper.requireChildCode(this) ?: return
        val text = findViewById<TextInputEditText>(R.id.inputMessage).text?.toString().orEmpty().trim()
        if (text.isBlank()) {
            Toast.makeText(this, "اكتبي الرسالة أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val role = ParentSession.guardianRole(this)
        lifecycleScope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    GuardianApi.sendGuardianMessage(childCode, role, text)
                }
            ) {
                is GuardianApi.ApiResult.Ok -> {
                    findViewById<TextView>(R.id.textMessageStatus).text = result.message
                    Toast.makeText(this@ParentMessageActivity, getString(R.string.parent_message_sent), Toast.LENGTH_SHORT).show()
                    findViewById<TextInputEditText>(R.id.inputMessage).text?.clear()
                }
                is GuardianApi.ApiResult.Error -> {
                    findViewById<TextView>(R.id.textMessageStatus).text = result.message
                    Toast.makeText(this@ParentMessageActivity, result.message, Toast.LENGTH_SHORT).show()
                }
                else -> Unit
            }
        }
    }
}
