package com.example.myrana.parent.ui

import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.parent.ParentSession
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class ParentAppsActivity : ParentShellActivity() {

    private lateinit var installedAppAdapter: InstalledAppAdapter

    override fun screenTitle(): String = getString(R.string.parent_hub_apps)

    override fun contentLayoutId(): Int = R.layout.content_parent_apps

    override fun onShellReady() {
        installedAppAdapter = InstalledAppAdapter(packageManager) { item ->
            ParentControlHelper.sendCommand(lifecycleScope, this, "block_app", item.packageName) { msg, _ ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<RecyclerView>(R.id.recyclerInstalledApps).apply {
            layoutManager = LinearLayoutManager(this@ParentAppsActivity)
            adapter = installedAppAdapter
        }
        findViewById<MaterialButton>(R.id.btnLoadInstalledApps).setOnClickListener { loadInstalledApps() }
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        val childCode = ParentSession.childCode(this)
        val email = ParentSession.guardianEmail(this)
        if (childCode.isNullOrBlank() || email.isNullOrBlank()) {
            Toast.makeText(this, "اربط الطفل أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val btn = findViewById<MaterialButton>(R.id.btnLoadInstalledApps)
        btn.isEnabled = false
        Toast.makeText(this, getString(R.string.parent_installed_apps_loading), Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            when (val result = InstalledAppsLoader.load(this@ParentAppsActivity)) {
                is InstalledAppsLoader.Result.Success -> bindInstalledApps(result.items, result.count)
                is InstalledAppsLoader.Result.Error -> {
                    findViewById<TextView>(R.id.textInstalledAppsEmpty).apply {
                        visibility = View.VISIBLE
                        text = result.message
                    }
                    Toast.makeText(this@ParentAppsActivity, result.message, Toast.LENGTH_SHORT).show()
                }
            }
            btn.isEnabled = true
        }
    }

    private fun bindInstalledApps(items: List<GuardianApi.InstalledAppItem>, count: Int) {
        val title = findViewById<TextView>(R.id.textInstalledAppsTitle)
        val empty = findViewById<TextView>(R.id.textInstalledAppsEmpty)
        val list = findViewById<RecyclerView>(R.id.recyclerInstalledApps)
        if (items.isEmpty()) {
            title.visibility = View.GONE
            list.visibility = View.GONE
            empty.visibility = View.VISIBLE
            empty.text = getString(R.string.parent_installed_apps_empty)
            return
        }
        empty.visibility = View.GONE
        title.visibility = View.VISIBLE
        title.text = getString(R.string.parent_installed_apps_title, count)
        list.visibility = View.VISIBLE
        installedAppAdapter.submit(items)
        Toast.makeText(this, "تم عرض $count تطبيق مثبت", Toast.LENGTH_SHORT).show()
    }
}
