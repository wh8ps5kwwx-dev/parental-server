package com.example.myrana.ui.academy

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.myrana.R
import com.example.myrana.academy.AcademyPythonBridge
import com.example.myrana.academy.AcademyProgressStore
import com.example.myrana.academy.AcademyReporter
import com.example.myrana.academy.ChallengeType
import com.google.android.material.button.MaterialButton

/** تحديات — المنطق من `academy_game.py` عبر Chaquopy. */
class AcademyChallengeActivity : AppCompatActivity() {

    private lateinit var challengeType: ChallengeType
    private var current: AcademyPythonBridge.Question? = null
    private val optionButtons = mutableListOf<MaterialButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_academy_challenge)

        AcademyPythonBridge.ensureStarted(this)

        val typeName = intent.getStringExtra(EXTRA_TYPE) ?: ChallengeType.MATH.name
        challengeType = ChallengeType.valueOf(typeName)

        findViewById<TextView>(R.id.textChallengeTitle).text = challengeType.title
        optionButtons.add(findViewById(R.id.btnOption1))
        optionButtons.add(findViewById(R.id.btnOption2))
        optionButtons.add(findViewById(R.id.btnOption3))

        optionButtons.forEach { btn ->
            btn.setOnClickListener { answer(btn) }
        }
        findViewById<MaterialButton>(R.id.btnNewQuestion).setOnClickListener {
            newQuestion()
        }
        findViewById<MaterialButton>(R.id.btnBackAcademy).setOnClickListener {
            finish()
        }

        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun refreshStats() {
        findViewById<TextView>(R.id.textChallengeStats).text =
            AcademyProgressStore.statsLine(this)
    }

    private fun categoryKey(): String = when (challengeType) {
        ChallengeType.MATH -> "math"
        ChallengeType.SCIENCE -> "science"
        ChallengeType.LOGIC -> "logic"
    }

    private fun newQuestion() {
        current = AcademyPythonBridge.randomQuestion(this, categoryKey())
        findViewById<TextView>(R.id.textQuestion).text = current?.q.orEmpty()
        optionButtons.forEachIndexed { index, btn ->
            val label = current?.options?.getOrNull(index).orEmpty()
            btn.text = label
            btn.tag = label
        }
        findViewById<TextView>(R.id.textChallengeMsg).text = getString(R.string.academy_press_start)
    }

    private fun answer(btn: MaterialButton) {
        val question = current ?: return
        val selected = btn.tag as? String ?: return
        val result = AcademyPythonBridge.gradeAnswer(
            this,
            categoryKey(),
            selected,
            question.answer,
        )
        findViewById<TextView>(R.id.textChallengeMsg).text = result.message
        if (result.leveled) {
            AcademyReporter.sendAlert(this, "الطفل وصل إلى المستوى ${result.level}")
        }
        AcademyReporter.sendReport(this, result.event, result.eventValue)
        refreshStats()
        newQuestion()
    }

    companion object {
        const val EXTRA_TYPE = "challenge_type"
    }
}
