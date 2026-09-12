package com.example.wakeorwait.challenge

import com.example.wakeorwait.data.model.Difficulty
import org.junit.Assert.*
import org.junit.Test

class MathGeneratorTest {

    @Test
    fun testGenerateProblemNormalMode() {
        for (i in 0 until 50) {
            val problem = MathGenerator.generateProblem(Difficulty.NORMAL)
            assertNotNull(problem.expression)
            assertTrue(problem.expression.isNotEmpty())

            // Verification of correct answer
            assertTrue(MathGenerator.verifyAnswer(problem.solution.toString(), problem))
            // Verification of leading/trailing space trimming
            assertTrue(MathGenerator.verifyAnswer("  ${problem.solution}  ", problem))
            // Verification of wrong answer
            assertFalse(MathGenerator.verifyAnswer((problem.solution + 1).toString(), problem))
            // Verification of non-numeric answer
            assertFalse(MathGenerator.verifyAnswer("abc", problem))
        }
    }

    @Test
    fun testGenerateProblemEvilMode() {
        for (i in 0 until 50) {
            val problem = MathGenerator.generateProblem(Difficulty.EVIL)
            assertNotNull(problem.expression)
            assertTrue(problem.expression.isNotEmpty())

            // Verification
            assertTrue(MathGenerator.verifyAnswer(problem.solution.toString(), problem))
            assertFalse(MathGenerator.verifyAnswer((problem.solution - 1).toString(), problem))
        }
    }
}
