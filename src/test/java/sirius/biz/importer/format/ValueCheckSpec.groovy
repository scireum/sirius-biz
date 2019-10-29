/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.format


import sirius.kernel.commons.Value
import spock.lang.Specification

class ValueCheckSpec extends Specification {

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

}
