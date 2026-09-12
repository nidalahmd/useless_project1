package com.example.wakeorwait.challenge

import kotlin.random.Random

data class ReverseWordProblem(
    val originalWord: String,
    val reversedWord: String
)

object ReverseWordGenerator {

    private val wordPool = listOf(
        "MORNING",
        "COFFEE",
        "CHALLENGE",
        "WAKEFUL",
        "SUNSHINE",
        "RUNNING",
        "DYNAMIC",
        "TRIUMPH",
        "VICTORY",
        "THUNDER",
        "FOCUS",
        "ENERGY",
        "WARRIOR",
        "COURAGE",
        "STRENGTH",
        "SUMMIT",
        "CHAMPION",
        "DISCIPLINE",
        "ALERTNESS",
        "HORIZON"
    )

    fun generateProblem(): ReverseWordProblem {
        val word = wordPool[Random.nextInt(wordPool.size)]
        return ReverseWordProblem(
            originalWord = word,
            reversedWord = word.reversed()
        )
    }

    fun verifyAnswer(userInput: String, problem: ReverseWordProblem): Boolean {
        val cleaned = userInput.trim()
        return cleaned.equals(problem.reversedWord, ignoreCase = true)
    }
}
