/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format

import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import sirius.kernel.commons.Value

class DateTimeFormatCheckTest {

    @CsvSource(
            delimiter = '|', useHeadersInDisplayName = true, textBlock = """
        format   | date
        ddMMuuuu | 1092019
        ddMMuuuu | '1092019'
        ddMMuuuu | 'TEST'
        ddMMuuuu | '01.09.2019'
        dd.MM.uuuu | 31.09.2019
        dd.MM.uuuu | 4.4.19
        dd.MM.uuuu | 4.011.19"""
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
        dd.MM.uuuu | '01.09.2019'
        dd.MM.uuuu | 23.10.2019
        dd.MM.uuuu | 01.05.1854
        dd.MM.uuuu | 24.12.9000"""
    )
    @ParameterizedTest
    fun `dates matching the provided format are correctly marked as valid`(format: String, date: String) {
        DateTimeFormatCheck(format).perform(Value.of(date))
    }
}
