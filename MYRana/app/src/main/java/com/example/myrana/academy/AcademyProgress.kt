package com.example.myrana.academy

/** تقدم اللاعب — يُحمَّل من `academy_game.py`. */
data class AcademyProgress(
    var coins: Int = 0,
    var xp: Int = 0,
    var level: Int = 1,
    var stars: Int = 0,
    var correct: Int = 0,
    var buildings: MutableList<String> = mutableListOf(),
)
