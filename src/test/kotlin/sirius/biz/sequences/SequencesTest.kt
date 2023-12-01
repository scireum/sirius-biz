/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences

import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException

import java.util.concurrent.ThreadLocalRandom

class SequencesSpec extends BaseSpecification {

    @Part
    protected static Sequences sequences

            def "a new sequence is automatically created"() {
        setup:
        def id = "__generated" + ThreadLocalRandom.current().nextInt()
        when:
        String value = sequences.generateId(id)
        String value1 = sequences.generateId(id)
        then:
        value == "1"
        and:
        value1 == "2"
    }

    def "a sequence is incremented by generate id"() {
        setup:
        def id = "__generated" + ThreadLocalRandom.current().nextInt()
        when:
        String value = sequences.generateId(id)
        String value1 = sequences.generateId(id)
        then:
        Integer.parseInt(value) == Integer.parseInt(value1) - 1
    }

    def "a new next value can be set"() {
        setup:
        def id = "__generated" + ThreadLocalRandom.current().nextInt()
        when:
        sequences.setNextValue(id, 1000, false)
        String value = sequences.generateId(id)
        then:
        "1000" == value
    }

    def "a cannot be set to a lower value"() {
        setup:
        def id = "__generated" + ThreadLocalRandom.current().nextInt()
        when:
        String value = sequences.generateId(id)
        sequences.generateId(id)
        sequences.setNextValue(id, Integer.parseInt(value), false)
        then:
        thrown(HandledException)
    }

    def "a can be set to a lower value when force is true"() {
        setup:
        def id = "__generated" + ThreadLocalRandom.current().nextInt()
        when:
        String value = sequences.generateId(id)
        sequences.generateId(id)
        sequences.setNextValue(id, Integer.parseInt(value), true)
        then:
        notThrown(HandledException)
    }

}
