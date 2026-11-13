package com.jlees.mibrowser

import com.jlees.mibrowser.answers.engineAnswer
import com.jlees.mibrowser.answers.formatNumber
import com.jlees.mibrowser.answers.inlineAnswer
import com.jlees.mibrowser.answers.keepsAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

/** Inline answers (answers/InlineAnswers): what gets answered in the Pill, and what's left as a search. */
class AnswersTest {
    private fun a(q: String) = inlineAnswer(q, ZonedDateTime.of(2026, 11, 13, 12, 0, 0, 0, ZoneId.of("America/New_York")), Random(1))?.answer

    @Test fun math() {
        assertEquals("14", a("2 + 3 * 4"))
        assertEquals("20", a("(2+3)*4"))
        assertEquals("1,024", a("2^10"))
        assertEquals("-4", a("-2^2"))
        assertEquals("30", a("20% of 150"))
        assertEquals("1", a("10 % 3"))
        assertEquals("0.5", a("50%"))
        assertEquals("120", a("5!"))
        assertEquals("12", a("3 x 4"))
        assertEquals("1,001", a("1,000 + 1"))
        assertEquals("1.414213562", a("sqrt(2)"))
        assertEquals("4", a("sqrt 16"))
        assertEquals("6.283185307", a("2pi"))
        assertEquals("2", a("log(100)"))
        assertEquals("0.5", a("sin(30°)"))
        assertEquals("3", a("(1+2"))
    }

    @Test fun ordinarySearchesAreLeftAlone() {
        listOf("hello", "2024", "e", "covid-19", "weather tomorrow", "5 km", "iphone 15 pro", "1/0", "https://a.com/1+1", "pi day")
            .forEach { assertNull(it, inlineAnswer(it)) }
    }

    @Test fun units() {
        assertEquals("3.10686 miles", a("5 km in mi"))
        assertEquals("1.60934 kilometers", a("mile to km"))
        assertEquals("37 degrees Celsius", a("98.6 f to c"))
        assertEquals("212 degrees Fahrenheit", a("100 °c in fahrenheit"))
        assertEquals("12.7 centimeters", a("5 in in cm"))
        assertEquals("1 kilogram", a("1000 grams to kg"))
        assertEquals("1.024 kilobytes", a("1 kib in kb"))
        assertEquals("1 hour", a("60 minutes to hours"))
        assertNull(a("5 km in kg")) // different kinds
    }

    @Test fun basesTimeAndChance() {
        assertEquals("0xFF", a("255 in hex"))
        assertEquals("255", a("0xff to decimal"))
        assertEquals("0b1010", a("10 to binary"))
        val london = inlineAnswer("time in london", ZonedDateTime.of(2026, 11, 13, 12, 0, 0, 0, ZoneId.of("America/New_York")))!!
        assertTrue(london.question, london.question.startsWith("Time in London"))
        assertTrue(london.answer, london.answer.startsWith("5:00")) // 12:00 in New York
        assertTrue(a("flip a coin") in setOf("Heads", "Tails"))
        assertTrue(a("roll a d20")!!.toInt() in 1..20)
    }

    @Test fun theEnginesAnswerOnlyForWhatsTypedNow() {
        val google = listOf("5 km in miles", "= 3.10685596 miles")
        assertEquals("3.10685596 miles", engineAnswer("5 km in mi ", google, "5 km in mi")?.answer)
        assertNull(engineAnswer("5 km", google, "5 km in mi")) // the last text's suggestions: not this answer
        assertNull(engineAnswer("cats", listOf("cats and dogs"), "cats"))
    }

    @Test fun bigNumbersAreGroupedButCopiedPlain() {
        val big = inlineAnswer("2^20")!!
        assertEquals("1,048,576" to "1048576", big.answer to big.copy)
        assertEquals("-1,234.5", com.jlees.mibrowser.answers.groupThousands("-1234.5"))
        assertEquals("999", com.jlees.mibrowser.answers.groupThousands("999"))
        assertEquals("1,000,000 meters", a("1000 km in m"))
        assertEquals("999,999,999,999 inches =", inlineAnswer("999999999999 in to mm")!!.question) // what was typed, not rounded
    }

    @Test fun anAnswerStaysWhileTypingOnOrBack() {
        val keeps = ::keepsAnswer
        assertTrue(keeps("2^1", "2^"))     // backspacing over it
        assertTrue(keeps("2^1", "2^10"))   // typing on from it
        assertEquals(false, keeps("2^1", "cats"))
        assertEquals(false, keeps("2^1", ""))
    }

    @Test fun numbersAreRoundedAndShortened() {
        assertEquals("0.3333333333", formatNumber(1.0 / 3))
        assertEquals("1e20", formatNumber(1e20))
        assertEquals("1.5e-7", formatNumber(1.5e-7))
        assertEquals("0.3", formatNumber(0.1 + 0.2))
    }
}
