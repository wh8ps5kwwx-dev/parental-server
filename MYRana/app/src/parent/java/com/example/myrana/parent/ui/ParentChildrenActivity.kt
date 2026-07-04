package com.example.myrana.parent.ui

import android.content.Intent
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.parent.ParentSession
import com.example.myrana.util.ChildCodeNormalizer
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParentChildrenActivity : ParentShellActivity() {

    private var linkedChildrenRows: List<Pair<String, String>> = emptyList()
    private var spinnerIgnoreSelection = false

    override fun screenTitle(): String = getString(R.string.parent_hub_children)

    override fun contentLayoutId(): Int = R.layout.content_parent_children

    override fun onShellReady() {
        findViewById<Spinner>(R.id.spinnerChildren).onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    if (spinnerIgnoreSelection) return
                    val row = linkedChildrenRows.getOrNull(position) ?: return
                    ParentSession.saveLinkedChild(this@ParentChildrenActivity, row.first, row.second)
                    Toast.makeText(this@ParentChildrenActivity, "تم اختيار ${row.second}", Toast.LENGTH_SHORT).show()
                    refreshSummary()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        findViewById<MaterialButton>(R.id.btnAddAnotherChild).setOnClickListener {
            ParentSession.startAddAnotherChild(this)
            startActivity(
                Intent(this, ParentMainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
        }
        refreshSummary()
        loadChildren()
    }

    private fun refreshSummary() {
        val name = ParentSession.childName(this).orEmpty()
        val code = ParentSession.childCode(this).orEmpty()
        findViewById<TextView>(R.id.textLinkedChild).text = getString(
            R.string.parent_linked_with_role,
            name,
            ParentSession.guardianRole(this),
            code,
        )
    }

    private fun loadChildren() {
        val email = ParentSession.guardianEmail(this).orEmpty()
        if (email.isBlank()) return
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { GuardianApi.fetchLinkedChildren(email) }) {
                is GuardianApi.ApiResult.ChildrenList -> {
                    if (result.children.isEmpty()) return@launch
                    val rows = result.children.mapNotNull { child ->
                        val code = child["child_code"]?.toString().orEmpty()
                        if (code.isBlank()) return@mapNotNull null
                        val name = child["name"]?.toString()?.ifBlank { "طفل" } ?: "طفل"
                        val online = if (child["online"] == true) "متصل" else "غير متصل"
                        Triple(code, name, online)
                    }
                    if (rows.isEmpty()) return@launch
                    linkedChildrenRows = rows.map { it.first to it.second }
                    val labels = rows.map { (code, name, status) ->
                        getString(R.string.parent_child_spinner_item, name, code, status)
                    }
                    val spinner = findViewById<Spinner>(R.id.spinnerChildren)
                    spinnerIgnoreSelection = true
                    spinner.adapter = ArrayAdapter(
                        this@ParentChildrenActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        labels,
                    )
                    val active = ParentSession.childCode(this@ParentChildrenActivity)
                    val activeNorm = ChildCodeNormalizer.forApi(active.orEmpty())
                    var selectIndex = rows.indexOfFirst { ChildCodeNormalizer.forApi(it.first) == activeNorm }
                    if (selectIndex < 0) {
                        selectIndex = 0
                        ParentSession.saveLinkedChild(
                            this@ParentChildrenActivity,
                            rows[0].first,
                            rows[0].second,
                        )
                    }
                    spinner.setSelection(selectIndex)
                    spinnerIgnoreSelection = false
                    findViewById<TextView>(R.id.textLinkedChild).text = getString(
                        R.string.parent_linked_children_summary,
                        linkedChildrenRows.size,
                    )
                }
                else -> Unit
            }
        }
    }
}
