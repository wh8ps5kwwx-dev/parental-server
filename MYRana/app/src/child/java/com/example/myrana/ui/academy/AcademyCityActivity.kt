package com.example.myrana.ui.academy

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myrana.R
import com.example.myrana.academy.AcademyCityCatalog
import com.example.myrana.academy.AcademyPythonBridge
import com.example.myrana.academy.AcademyReporter
import com.google.android.material.button.MaterialButton

/** مدينة المعرفة — بناء المباني عبر Python. */
class AcademyCityActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_academy_city)

        AcademyPythonBridge.ensureStarted(this)

        val container = findViewById<LinearLayout>(R.id.layoutBuildings)
        container.removeAllViews()
        AcademyCityCatalog.buildings.forEach { building ->
            val btn = MaterialButton(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                text = "${building.emojiLabel}\n${building.starCost} ⭐"
                setOnClickListener { build(building.name, building.starCost) }
            }
            container.addView(btn)
        }

        findViewById<MaterialButton>(R.id.btnBackCity).setOnClickListener { finish() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val info = AcademyPythonBridge.cityInfo(this)
        findViewById<TextView>(R.id.textCityInfo).text =
            "⭐ النجوم المتاحة: ${info.stars}\nاستخدمي النجوم لبناء مدينة تعليمية جميلة"
        findViewById<TextView>(R.id.textCityView).text = info.cityText
    }

    private fun build(name: String, cost: Int) {
        val (msg, event) = AcademyPythonBridge.cityBuild(this, name, cost)
        findViewById<TextView>(R.id.textCityMsg).text = msg
        if (event != null) {
            AcademyReporter.sendReport(this, "city_building", event)
        }
        refresh()
    }
}
