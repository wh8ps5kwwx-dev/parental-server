package com.example.myrana.parent.ui

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.util.AppIconHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class ParentBlockActivity : ParentShellActivity() {

    private enum class DurationMode {
        MINUTES_15,
        MINUTES_30,
        MINUTES_60,
        MINUTES_120,
        UNTIL_CANCEL,
        CUSTOM,
    }

    private lateinit var freezeAdapter: FreezeAppGridAdapter
    private var durationMode: DurationMode = DurationMode.MINUTES_30

    override fun screenTitle(): String = getString(R.string.parent_hub_block)

    override fun contentLayoutId(): Int = R.layout.content_parent_block

    override fun onShellReady() {
        val input = findViewById<TextInputEditText>(R.id.inputTarget)
        val status = findViewById<TextView>(R.id.textBlockMessage)
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupFreezeDuration)
        val layoutCustom = findViewById<LinearLayout>(R.id.layoutCustomSchedule)

        freezeAdapter = FreezeAppGridAdapter { item ->
            showSelectedApp(item)
            findViewById<MaterialCardView>(R.id.cardFreezeDuration).visibility = View.VISIBLE
            input.setText(item.packageName)
        }
        findViewById<RecyclerView>(R.id.recyclerFreezeApps).apply {
            layoutManager = GridLayoutManager(this@ParentBlockActivity, 4)
            adapter = freezeAdapter
            setHasFixedSize(true)
        }

        findViewById<MaterialButton>(R.id.btnReloadFreezeApps).setOnClickListener { loadApps() }
        findViewById<MaterialButton>(R.id.btnApplyFreeze).setOnClickListener { applyFreeze() }
        findViewById<MaterialButton>(R.id.btnAllow).setOnClickListener {
            ParentControlHelper.sendCommand(lifecycleScope, this, "allow", "") { msg, _ ->
                status.text = msg
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.btnBlockSite).setOnClickListener {
            ParentControlHelper.sendCommand(lifecycleScope, this, "block_site", input.text?.toString().orEmpty()) { msg, _ ->
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
        findViewById<MaterialButton>(R.id.btnApplyDefaultBlocklist).setOnClickListener { applyDefaultBlocklist() }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            durationMode = when (checkedIds.firstOrNull()) {
                R.id.chipFreeze15 -> DurationMode.MINUTES_15
                R.id.chipFreeze30 -> DurationMode.MINUTES_30
                R.id.chipFreeze60 -> DurationMode.MINUTES_60
                R.id.chipFreeze120 -> DurationMode.MINUTES_120
                R.id.chipFreezeUntilCancel -> DurationMode.UNTIL_CANCEL
                R.id.chipFreezeCustom -> DurationMode.CUSTOM
                else -> durationMode
            }
            layoutCustom.visibility = if (durationMode == DurationMode.CUSTOM) View.VISIBLE else View.GONE
        }
        findViewById<Chip>(R.id.chipFreeze30).isChecked = true

        loadApps()
    }

    private fun loadApps() {
        val loading = findViewById<TextView>(R.id.textFreezeAppsLoading)
        val empty = findViewById<TextView>(R.id.textFreezeAppsEmpty)
        val list = findViewById<RecyclerView>(R.id.recyclerFreezeApps)
        val reload = findViewById<MaterialButton>(R.id.btnReloadFreezeApps)

        loading.visibility = View.VISIBLE
        empty.visibility = View.GONE
        list.visibility = View.GONE
        reload.isEnabled = false

        lifecycleScope.launch {
            when (val result = InstalledAppsLoader.load(this@ParentBlockActivity)) {
                is InstalledAppsLoader.Result.Success -> {
                    loading.visibility = View.GONE
                    empty.visibility = View.GONE
                    list.visibility = View.VISIBLE
                    freezeAdapter.submit(result.items)
                    findViewById<TextView>(R.id.textBlockMessage).text =
                        getString(R.string.parent_freeze_apps_loaded, result.count)
                }
                is InstalledAppsLoader.Result.Error -> {
                    loading.visibility = View.GONE
                    list.visibility = View.GONE
                    empty.visibility = View.VISIBLE
                    empty.text = result.message
                    Toast.makeText(this@ParentBlockActivity, result.message, Toast.LENGTH_SHORT).show()
                }
            }
            reload.isEnabled = true
        }
    }

    private fun showSelectedApp(item: GuardianApi.InstalledAppItem) {
        findViewById<MaterialCardView>(R.id.cardSelectedApp).visibility = View.VISIBLE
        findViewById<TextView>(R.id.textSelectedAppName).text = item.appLabel
        val iconView = findViewById<ImageView>(R.id.imgSelectedAppIcon)
        val bitmap = item.iconBase64?.let { AppIconHelper.fromBase64Png(it) }
        if (bitmap != null) {
            iconView.setImageBitmap(bitmap)
        } else {
            iconView.setImageResource(android.R.drawable.sym_def_app_icon)
        }
    }

    private fun applyFreeze() {
        val item = freezeAdapter.selectedItem()
        if (item == null) {
            Toast.makeText(this, getString(R.string.parent_freeze_select_app_first), Toast.LENGTH_SHORT).show()
            return
        }
        val childCode = ParentControlHelper.requireChildCode(this) ?: return
        val status = findViewById<TextView>(R.id.textBlockMessage)
        val btn = findViewById<MaterialButton>(R.id.btnApplyFreeze)
        btn.isEnabled = false

        when (durationMode) {
            DurationMode.UNTIL_CANCEL -> {
                ParentControlHelper.sendCommand(lifecycleScope, this, "freeze_app", item.packageName) { msg, _ ->
                    status.text = msg
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    btn.isEnabled = true
                }
                return
            }
            DurationMode.CUSTOM -> {
                val start = findViewById<TextInputEditText>(R.id.inputScheduleStart).text?.toString().orEmpty().trim()
                val end = findViewById<TextInputEditText>(R.id.inputScheduleEnd).text?.toString().orEmpty().trim()
                if (start.isBlank() || end.isBlank()) {
                    Toast.makeText(this, getString(R.string.parent_freeze_enter_times), Toast.LENGTH_SHORT).show()
                    btn.isEnabled = true
                    return
                }
                scheduleFreeze(childCode, item.packageName, start, end, item.appLabel, btn, status)
                return
            }
            else -> {
                val minutes = when (durationMode) {
                    DurationMode.MINUTES_15 -> 15
                    DurationMode.MINUTES_30 -> 30
                    DurationMode.MINUTES_60 -> 60
                    DurationMode.MINUTES_120 -> 120
                    else -> 30
                }
                val startCal = Calendar.getInstance()
                val endCal = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }
                scheduleFreeze(
                    childCode,
                    item.packageName,
                    formatHm(startCal),
                    formatHm(endCal),
                    item.appLabel,
                    btn,
                    status,
                )
            }
        }
    }

    private fun scheduleFreeze(
        childCode: String,
        pkg: String,
        start: String,
        end: String,
        appLabel: String,
        btn: MaterialButton,
        status: TextView,
    ) {
        lifecycleScope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    GuardianApi.addSchedule(childCode, "freeze_app", pkg, start, end)
                }
            ) {
                is GuardianApi.ApiResult.Ok -> {
                    val msg = getString(R.string.parent_freeze_scheduled_ok, appLabel, start, end)
                    status.text = msg
                    Toast.makeText(this@ParentBlockActivity, msg, Toast.LENGTH_LONG).show()
                }
                is GuardianApi.ApiResult.Error ->
                    Toast.makeText(this@ParentBlockActivity, result.message, Toast.LENGTH_SHORT).show()
                else -> Unit
            }
            btn.isEnabled = true
        }
    }

    private fun formatHm(cal: Calendar): String =
        String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

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
