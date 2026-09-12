package com.example.wakeorwait.challenge

import org.junit.Assert.*
import org.junit.Test

class ReverseWordGeneratorTest {

    @Test
    fun testGenerateAndVerifyReverseWord() {
        for (i in 0 until 50) {
            val problem = ReverseWordGenerator.generateProblem()
            assertEquals(problem.originalWord.reversed(), problem.reversedWord)

            // Case-insensitive verification
            assertTrue(ReverseWordGenerator.verifyAnswer(problem.reversedWord.lowercase(), problem))
            assertTrue(ReverseWordGenerator.verifyAnswer(problem.reversedWord.uppercase(), problem))

            // Extra whitespace tolerance
            assertTrue(ReverseWordGenerator.verifyAnswer("  ${problem.reversedWord}  ", problem))

            // Wrong answer
            assertFalse(ReverseWordGenerator.verifyAnswer("INCORRECT", problem))
            assertFalse(ReverseWordGenerator.verifyAnswer(problem.originalWord, problem))
        }
    }
}
