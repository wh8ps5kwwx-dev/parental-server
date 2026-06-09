package com.example.myrana.ui.academy

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myrana.R
import com.example.myrana.academy.AcademyPythonBridge
import com.google.android.material.button.MaterialButton

/** لوحة الإنجازات — من Python. */
class AcademyRewardsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_academy_rewards)
        AcademyPythonBridge.ensureStarted(this)
        findViewById<MaterialButton>(R.id.btnBackRewards).setOnClickListener { finish() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        findViewById<TextView>(R.id.textRewardsBody).text =
            AcademyPythonBridge.rewardsText(this)
    }
}
