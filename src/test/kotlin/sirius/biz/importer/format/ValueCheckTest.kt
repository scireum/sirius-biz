/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format

class ValueCheckTest extends BaseSpecification {

    def "amount scale check correctly marks non numeric values as invalid"() {
        when:
        AmountScaleCheck scaleCheck = new AmountScaleCheck(5, 2)
        Value number = Value.of("TEST")
        and:
        scaleCheck.perform(number)
        then:
        thrown IllegalArgumentException
    }

    def "numbers exceeding the provided precision are correctly marked as invalid"() {
        when:
        new AmountScaleCheck(5, 2).perform(Value.of(number))
        then:
        thrown IllegalArgumentException
                where:
        number << [1234, "1234", 123456, "123456", 1234.56, "1234.56"]
    }

    def "numbers exceeding the provided scale are correctly marked as invalid"() {
        when:
        new AmountScaleCheck(5, 2).perform(Value.of(number))
        then:
        thrown IllegalArgumentException
                where:
        number << [0.123, "0.123", 12.345, "12.345"]
    }

    def "numbers matching the provided scale and precission are correctly marked as valid"() {
        expect:
        new AmountScaleCheck(precision, scale).perform(Value.of(number))
        where:
        precision | scale | number
        10        | 0     | 1234512345
        10        | 0     | "1234512345"
        5         | 2     | 0.12
        5         | 2     | "0.12"
        5         | 2     | 1.23
        5         | 2     | "1.23"
        5         | 2     | 12.34
        5         | 2     | "12.34"
        5         | 2     | 123.45
        5         | 2     | "123.45"
        5         | 2     | 1
        5         | 2     | "1"
        5         | 2     | 12
        5         | 2     | "12"
        5         | 2     | 123
        5         | 2     | "123"
    }

    def "LenghCheck works as expected"() {
        when: "Check with proper lengths work and perform an auto-trim"
        new LengthCheck(5).perform(Value.of("     55555"))
        new LengthCheck(5).perform(Value.of("55555"))
        then:
        noExceptionThrown()
        when: "Without trimming, the check fails appropriately"
        new LengthCheck(5).checkUntrimmed().perform(Value.of("     55555"))
        then:
        thrown(IllegalArgumentException)
        when: "The check fails for an input which is too long"
        new LengthCheck(5).checkUntrimmed().perform(Value.of("555555555"))
        then:
        thrown(IllegalArgumentException)
    }

    def "RequiredCheck works as expected"() {
        when: "Check accepts values and whitespaces (when checking untrimmed)"
        new RequiredCheck().perform(Value.of("55555"))
        new RequiredCheck().checkUntrimmed().perform(Value.of("  "))
        then:
        noExceptionThrown()
        when: "Check detects an empty input"
        new RequiredCheck().perform(Value.of(""))
        then:
        thrown(IllegalArgumentException)
        when: "Check detects a null input"
        new RequiredCheck().perform(Value.EMPTY)
        then:
        thrown(IllegalArgumentException)
        when: "Check detects a whitespace input (when trimming is enabled)"
        new RequiredCheck().perform(Value.of("  "))
        then:
        thrown(IllegalArgumentException)
    }

    def "ValueInListCheck works as expected"() {
        when: "Check accepts values and whitespaces (when checking trimmed)"
        new ValueInListCheck("A","B").perform(Value.of("A"))
        new ValueInListCheck("A","B").perform(Value.of(" A"))
        new ValueInListCheck("A","B").perform(Value.of("B"))
        new ValueInListCheck("A","B").perform(Value.of("B "))
        and: "Check ignores an empty value"
        new ValueInListCheck("A","B").perform(Value.of(""))
        new ValueInListCheck("A","B").perform(Value.EMPTY)
        then:
        noExceptionThrown()
        when: "Check detects an invalid input"
        new ValueInListCheck("A","B").perform(Value.of("C"))
        then:
        thrown(IllegalArgumentException)
        when: "Check detects an invalid input when checking untrimmed"
        new ValueInListCheck("A","B").checkUntrimmed().perform(Value.of("A "))
        then:
        thrown(IllegalArgumentException)
    }

}
