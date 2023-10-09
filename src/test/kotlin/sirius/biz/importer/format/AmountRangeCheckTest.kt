/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format

class AmountRangeCheckTest extends BaseSpecification {

    def "values below the inclusive lower limit are correctly marked as invalid"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitInclusive(Amount.ONE).perform(Value.of(0))
        then:
        thrown IllegalArgumentException
    }

    def "values equal to the inclusive lower limit are correctly marked as valid"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitInclusive(Amount.ONE).perform(Value.of(1))
        then:
        noExceptionThrown()
    }

    def "values above the inclusive lower limit are correctly marked as valid"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitInclusive(Amount.ONE).perform(Value.of(2))
        then:
        noExceptionThrown()
    }

    def "values below the exclusive lower limit are correctly marked as invalid"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitExclusive(Amount.ONE).perform(Value.of(0))
        then:
        thrown IllegalArgumentException
    }

    def "values equal to the exclusive lower limit are correctly marked as invalid"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitExclusive(Amount.ONE).perform(Value.of(1))
        then:
        thrown IllegalArgumentException
    }

    def "values above the exclusive lower limit are correctly marked as valid"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitExclusive(Amount.ONE).perform(Value.of(2))
        then:
        noExceptionThrown()
    }

    def "values above the inclusive upper limit are correctly marked as invalid"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withUpperLimitInclusive(Amount.ONE_HUNDRED).
        perform(Value.of(101))
        then:
        thrown IllegalArgumentException
    }

    def "values equal to the inclusive upper limit are correctly marked as valid"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withUpperLimitInclusive(Amount.ONE_HUNDRED).
        perform(Value.of(100))
        then:
        noExceptionThrown()
    }

    def "values below the inclusive upper limit are correctly marked as valid"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withUpperLimitInclusive(Amount.ONE_HUNDRED).
        perform(Value.of(99))
        then:
        noExceptionThrown()
    }

    def "values above the exclusive upper limit are correctly marked as invalid"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withUpperLimitExclusive(Amount.ONE_HUNDRED).
        perform(Value.of(101))
        then:
        thrown IllegalArgumentException
    }

    def "values equal to the exclusive upper limit are correctly marked as invalid"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withUpperLimitExclusive(Amount.ONE_HUNDRED).
        perform(Value.of(100))
        then:
        thrown IllegalArgumentException
    }

    def "values below the exclusive upper limit are correctly marked as valid"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withUpperLimitExclusive(Amount.ONE_HUNDRED).
        perform(Value.of(99))
        then:
        noExceptionThrown()
    }

    def "empty values are ignored"() {
        when:
        new AmountRangeCheck(NumberFormat.NO_DECIMAL_PLACES).withLowerLimitInclusive(Amount.ONE).
        withUpperLimitExclusive(Amount.ONE_HUNDRED).
        perform(Value.of(Amount.NOTHING))
        then:
        noExceptionThrown()
    }
}
