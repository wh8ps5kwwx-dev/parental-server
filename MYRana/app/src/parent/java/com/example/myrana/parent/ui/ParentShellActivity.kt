package com.example.myrana.parent.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myrana.R
import com.google.android.material.appbar.MaterialToolbar

/** قشرة موحّدة لصفحات لوحة ولي الأمر — نفس الخلفية البنفسجية وشريط العودة. */
abstract class ParentShellActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_shell)
        findViewById<MaterialToolbar>(R.id.parentToolbar).apply {
            title = screenTitle()
            setNavigationOnClickListener { finish() }
        }
        layoutInflater.inflate(contentLayoutId(), findViewById(R.id.parentShellContent), true)
        onShellReady()
    }

    abstract fun screenTitle(): String

    abstract fun contentLayoutId(): Int

    open fun onShellReady() {}
}
