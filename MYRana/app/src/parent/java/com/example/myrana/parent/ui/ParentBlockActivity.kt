package com.example.myrana.parent.ui

import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParentBlockActivity : ParentShellActivity() {

    override fun screenTitle(): String = getString(R.string.parent_hub_block)

    override fun contentLayoutId(): Int = R.layout.content_parent_block

    override fun onShellReady() {
        val input = findViewById<TextInputEditText>(R.id.inputTarget)
        val status = findViewById<TextView>(R.id.textBlockMessage)

        findViewById<MaterialButton>(R.id.btnBlockSite).setOnClickListener {
            ParentControlHelper.sendCommand(lifecycleScope, this, "block_site", input.text?.toString().orEmpty()) { msg, err ->
                status.text = msg
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.btnBlockApp).setOnClickListener {
            ParentControlHelper.sendCommand(lifecycleScope, this, "block_app", input.text?.toString().orEmpty()) { msg, _ ->
                status.text = msg
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.btnFreezeApp).setOnClickListener {
            ParentControlHelper.sendCommand(lifecycleScope, this, "freeze_app", input.text?.toString().orEmpty()) { msg, _ ->
                status.text = msg
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.btnAllow).setOnClickListener {
            ParentControlHelper.sendCommand(lifecycleScope, this, "allow", "") { msg, _ ->
                status.text = msg
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.btnScheduleFreeze).setOnClickListener { scheduleFreeze() }
        findViewById<MaterialButton>(R.id.btnApplyDefaultBlocklist).setOnClickListener { applyDefaultBlocklist() }
    }

    private fun scheduleFreeze() {
        val childCode = ParentControlHelper.requireChildCode(this) ?: return
        val pkg = findViewById<TextInputEditText>(R.id.inputTarget).text?.toString().orEmpty().trim()
        val start = findViewById<TextInputEditText>(R.id.inputScheduleStart).text?.toString().orEmpty().trim()
        val end = findViewById<TextInputEditText>(R.id.inputScheduleEnd).text?.toString().orEmpty().trim()
        if (pkg.isBlank() || start.isBlank() || end.isBlank()) {
            Toast.makeText(this, "أدخل الحزمة ووقت البداية والنهاية", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    GuardianApi.addSchedule(childCode, "freeze_app", pkg, start, end)
                }
            ) {
                is GuardianApi.ApiResult.Ok -> {
                    findViewById<TextView>(R.id.textBlockMessage).text = result.message
                    Toast.makeText(this@ParentBlockActivity, result.message, Toast.LENGTH_SHORT).show()
                }
                is GuardianApi.ApiResult.Error -> Toast.makeText(this@ParentBlockActivity, result.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }
    }

    private fun applyDefaultBlocklist() {
        val childCode = ParentControlHelper.requireChildCode(this) ?: return
        findViewById<MaterialButton>(R.id.btnApplyDefaultBlocklist).isEnabled = false
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { GuardianApi.applyDefaultBlocklist(childCode) }) {
                is GuardianApi.ApiResult.Ok -> {
                    findViewById<TextView>(R.id.textBlockMessage).text = result.message
                    Toast.makeText(this@ParentBlockActivity, result.message, Toast.LENGTH_SHORT).show()
                }
                is GuardianApi.ApiResult.Error -> Toast.makeText(this@ParentBlockActivity, result.message, Toast.LENGTH_SHORT).show()
                else -> Toast.makeText(this@ParentBlockActivity, "فشل تطبيق القائمة", Toast.LENGTH_SHORT).show()
            }
            findViewById<MaterialButton>(R.id.btnApplyDefaultBlocklist).isEnabled = true
        }
    }
}
