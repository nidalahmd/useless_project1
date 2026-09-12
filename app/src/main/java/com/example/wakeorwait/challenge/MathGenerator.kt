package com.example.wakeorwait.challenge

import com.example.wakeorwait.data.model.Difficulty
import kotlin.random.Random

data class MathProblem(
    val expression: String,
    val solution: Int
)

object MathGenerator {

    fun generateProblem(difficulty: Difficulty): MathProblem {
        val rand = Random.Default
        return when (difficulty) {
            Difficulty.EVIL -> {
                // Occasionally generate 3-term expression
                val type = rand.nextInt(3)
                when (type) {
                    0 -> {
                        // a * b - c
                        val a = rand.nextInt(12, 35)
                        val b = rand.nextInt(3, 8)
                        val c = rand.nextInt(10, 40)
                        MathProblem("$a × $b − $c", (a * b) - c)
                    }
                    1 -> {
                        // a * b + c
                        val a = rand.nextInt(11, 25)
                        val b = rand.nextInt(4, 9)
                        val c = rand.nextInt(15, 50)
                        MathProblem("$a × $b + $c", (a * b) + c)
                    }
                    else -> {
                        // 2-digit multiplication or subtraction
                        val a = rand.nextInt(35, 89)
                        val b = rand.nextInt(18, 45)
                        MathProblem("$a − $b", a - b)
                    }
                }
            }
            Difficulty.NORMAL, Difficulty.EASY -> {
                val op = rand.nextInt(3)
                when (op) {
                    0 -> {
                        // Addition: two 2-digit numbers
                        val a = rand.nextInt(23, 78)
                        val b = rand.nextInt(15, 69)
                        MathProblem("$a + $b", a + b)
                    }
                    1 -> {
                        // Subtraction: two 2-digit numbers resulting in positive
                        val a = rand.nextInt(45, 99)
                        val b = rand.nextInt(12, a - 5)
                        MathProblem("$a − $b", a - b)
                    }
                    else -> {
                        // Multiplication: single digit times single/small double digit
                        val a = rand.nextInt(6, 12)
                        val b = rand.nextInt(6, 12)
                        MathProblem("$a × $b", a * b)
                    }
                }
            }
        }
    }

    fun verifyAnswer(userAnswer: String, problem: MathProblem): Boolean {
        val cleaned = userAnswer.trim()
        val num = cleaned.toIntOrNull() ?: return false
        return num == problem.solution
    }
}
