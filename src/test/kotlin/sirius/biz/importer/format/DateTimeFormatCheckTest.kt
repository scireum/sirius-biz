/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format

class DateTimeFormatCheckTest extends BaseSpecification {

    def "valid dates throws no exception"() {
        when:
        new DateTimeFormatCheck("dd.MM.uuuu").perform(Value.of("23.10.2019"))
        new DateTimeFormatCheck("dd.MM.uuuu").perform(Value.of("01.05.1854"))
        new DateTimeFormatCheck("dd.MM.uuuu").perform(Value.of("24.12.9000"))
        then:
        noExceptionThrown()
    }

    def "invalid date throws exception"() {
        when:
        new DateTimeFormatCheck("dd.MM.uuuu").perform(Value.of("31.09.2019"))
        then:
        thrown(IllegalArgumentException)
    }

    def "date with to little numbers is invalid"() {
        when:
        new DateTimeFormatCheck("dd.MM.uuuu").perform(Value.of("4.4.19"))
        then:
        thrown(IllegalArgumentException)
    }

    def "date with to much numbers is invalid"() {
        when:
        new DateTimeFormatCheck("dd.MM.uuuu").perform(Value.of("4.011.19"))
        then:
        thrown(IllegalArgumentException)
    }

    def "dates not matching the provided format are correctly marked as invalid"() {
        when:
        new DateTimeFormatCheck("ddMMuuuu").perform(Value.of(date))
        then:
        thrown IllegalArgumentException
                where:
        date << [1092019, "1092019", "TEST", "01.09.2019"]
    }

    def "dates matching the provided format are correctly marked as valid"() {
        expect:
        new DateTimeFormatCheck(format).perform(Value.of(date))
        where:
        format       | date
        "ddMMuuuu"   | 11092019
        "ddMMuuuu"   | "01092019"
        "ddMMuuuu"   | "11092019"
        "dd.MM.uuuu" | "01.09.2019"
    }
}
