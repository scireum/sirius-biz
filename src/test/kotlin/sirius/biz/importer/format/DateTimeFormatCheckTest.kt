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

class DateTimeFormatCheckTest {

    @Test
    fun `valid dates throws no exception`() {
        DateTimeFormatCheck("dd.MM.uuuu").perform(Value.of("23.10.2019"))
        DateTimeFormatCheck("dd.MM.uuuu").perform(Value.of("01.05.1854"))
        DateTimeFormatCheck("dd.MM.uuuu").perform(Value.of("24.12.9000"))
    }

    @Test
    fun `invalid date throws exception`() {
        assertThrows<IllegalArgumentException> {
            DateTimeFormatCheck("dd.MM.uuuu").perform(Value.of("31.09.2019"))
        }
    }

    @Test
    fun `date with too few numbers is invalid`() {
        assertThrows<IllegalArgumentException> {
            DateTimeFormatCheck("dd.MM.uuuu").perform(Value.of("4.4.19"))
        }
    }

    @Test
    fun `date with too many numbers is invalid`() {
        assertThrows<IllegalArgumentException> {
            DateTimeFormatCheck("dd.MM.uuuu").perform(Value.of("4.011.19"))
        }
    }

    @CsvSource(
            delimiter = '|', useHeadersInDisplayName = true, textBlock = """
        format   | date
        ddMMuuuu | 1092019
        ddMMuuuu | '1092019'
        ddMMuuuu | 'TEST'
        ddMMuuuu | '01.09.2019'"""
    )
    @ParameterizedTest
    fun `dates not matching the provided format are correctly marked as invalid`(format: String, date: String) {
        assertThrows<IllegalArgumentException> {
            DateTimeFormatCheck(format).perform(Value.of(date))
        }
    }

    @CsvSource(
            delimiter = '|', useHeadersInDisplayName = true, textBlock = """
        format     | date
        ddMMuuuu   | 11092019
        ddMMuuuu   | '01092019'
        ddMMuuuu   | '11092019'
        dd.MM.uuuu | '01.09.2019'"""
    )
    @ParameterizedTest
    fun `dates matching the provided format are correctly marked as valid`(format: String, date: String) {
        DateTimeFormatCheck(format).perform(Value.of(date))
    }
}
