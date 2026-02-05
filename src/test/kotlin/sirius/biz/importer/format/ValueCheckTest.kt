/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import sirius.kernel.commons.Value

/**
 * Tests the [ValueCheck] class.
 */
class ValueCheckTest {

    @Test
    fun `amount scale check correctly marks non numeric values as invalid`() {
        val scaleCheck = AmountScaleCheck(5, 2)
        val number = Value.of("TEST")
        assertThrows<IllegalArgumentException> {
            scaleCheck.perform(number)
        }
    }

    @CsvSource(
        delimiter = '|', useHeadersInDisplayName = true, textBlock = """
        number
        1234
        '1234'
        123456
        '123456'
        1234.56
        '1234.56'"""
    )
    @ParameterizedTest
    fun `numbers exceeding the provided fixed point precision are correctly marked as invalid`(number: String) {
        assertThrows<IllegalArgumentException> {
            AmountScaleCheck(5, 2).perform(Value.of(number))
        }
    }

    @CsvSource(
        delimiter = '|', useHeadersInDisplayName = true, textBlock = """
        number
        123456
        '123456'
        1234.50
        '1234.50'"""
    )
    @ParameterizedTest
    fun `numbers exceeding the provided arbitrary precision are correctly marked as invalid`(number: String) {
        assertThrows<IllegalArgumentException> {
            AmountScaleCheck(5, 2).useArbitraryPrecision().perform(Value.of(number))
        }
    }

    @CsvSource(
        delimiter = '|', useHeadersInDisplayName = true, textBlock = """
        number
        0.123
        '0.123'
        12.345
        '12.345'"""
    )
    @ParameterizedTest
    fun `numbers exceeding the provided scale are correctly marked as invalid`(number: String) {
        assertThrows<IllegalArgumentException> {
            AmountScaleCheck(5, 2).perform(Value.of(number))
        }
    }

    @CsvSource(
        delimiter = '|', useHeadersInDisplayName = true, textBlock = """
        precision | scale | number
        10        | 0     | 1234512345
        10        | 0     | '1234512345'
        5         | 2     | 0.12
        5         | 2     | '0.12'
        5         | 2     | 1.23
        5         | 2     | '1.23'
        5         | 2     | 12.34
        5         | 2     | '12.34'
        5         | 2     | 123.45
        5         | 2     | '123.45'
        5         | 2     | 1
        5         | 2     | '1'
        5         | 2     | 12
        5         | 2     | '12'
        5         | 2     | 123
        5         | 2     | '123'"""
    )
    @ParameterizedTest
    fun `numbers matching the provided scale and fixed-point precision are correctly marked as valid`(
        precision: Int,
        scale: Int,
        number: String
    ) {
        AmountScaleCheck(precision, scale).perform(Value.of(number))
    }

    @CsvSource(
        delimiter = '|', useHeadersInDisplayName = true, textBlock = """
        precision | scale | number
        10        | 0     | 1234512345
        10        | 0     | '1234512345'
        3         | 2     | 0.12
        3         | 2     | '0.12'
        3         | 2     | 1.20
        3         | 2     | '1.20'
        3         | 2     | 12.3
        3         | 2     | '12.3'
        3         | 2     | 123
        3         | 2     | '123'"""
    )
    @ParameterizedTest
    fun `numbers matching the provided scale and arbitrary precision are correctly marked as valid`(
        precision: Int,
        scale: Int,
        number: String
    ) {
        AmountScaleCheck(precision, scale).useArbitraryPrecision().perform(Value.of(number))
    }

    @Test
    fun `LengthCheck works as expected`() {
        // Check with proper lengths work and perform an auto-trim
        LengthCheck(5).perform(Value.of("     55555"))
        LengthCheck(5).perform(Value.of("55555"))
        // Without trimming, the check fails appropriately
        assertThrows<IllegalArgumentException> {
            LengthCheck(5).checkUntrimmed().perform(Value.of("     55555"))
        }
        // The check fails for an input which is too long
        assertThrows<IllegalArgumentException> {
            LengthCheck(5).checkUntrimmed().perform(Value.of("555555555"))
        }
    }

    @Test
    fun `RequiredCheck works as expected`() {
        // Check accepts values and whitespaces (when checking untrimmed)
        RequiredCheck().perform(Value.of("55555"))
        RequiredCheck().checkUntrimmed().perform(Value.of("  "))
        // Check detects an empty input
        assertThrows<IllegalArgumentException> {
            RequiredCheck().perform(Value.of(""))
        }
        // Check detects a null input
        assertThrows<IllegalArgumentException> {
            RequiredCheck().perform(Value.EMPTY)
        }
        // Check detects a whitespace input (when trimming is enabled)
        assertThrows<IllegalArgumentException> {
            RequiredCheck().perform(Value.of("  "))
        }
    }

    @Test
    fun `ValueInListCheck works as expected`() {
        // Check accepts values and whitespaces (when checking trimmed)
        ValueInListCheck("A", "B").perform(Value.of("A"))
        ValueInListCheck("A", "B").perform(Value.of(" A"))
        ValueInListCheck("A", "B").perform(Value.of("B"))
        ValueInListCheck("A", "B").perform(Value.of("B "))
        // Check ignores an empty value
        ValueInListCheck("A", "B").perform(Value.of(""))
        ValueInListCheck("A", "B").perform(Value.EMPTY)
        // Check detects an invalid input
        assertThrows<IllegalArgumentException> {
            ValueInListCheck("A", "B").perform(Value.of("C"))
        }
        // Check detects an invalid input when checking untrimmed
        assertThrows<IllegalArgumentException> {
            ValueInListCheck("A", "B").checkUntrimmed().perform(Value.of("A "))
        }
    }
}
