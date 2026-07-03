package com.example.myrana.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myrana.ui.academy.AcademyMenuActivity

/**
 * إعادة توجيه — لعبة الألوان أُلغيت.
 * الواجهة الرسمية: [AcademyMenuActivity] (أكاديمية العباقرة).
 */
class GameActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, AcademyMenuActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
        finish()
    }
}
