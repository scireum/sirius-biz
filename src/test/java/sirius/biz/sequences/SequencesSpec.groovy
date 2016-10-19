/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences

import sirius.db.mixing.Schema
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException

import java.time.Duration

class SequencesSpec extends BaseSpecification {

    @Part
    private static Sequences sequences;

    @Part
    private static Schema schema;

    def "a sequence creates consecutive numbers when used serially"() {
        given:
        schema.getReadyFuture().await(Duration.ofSeconds(45));
        when:
        int value1 = sequences.generateId("test")
        then:
        sequences.generateId("test") == value1 + 1
    }

    def "a sequence can be set to used a new next value"() {
        given:
        schema.getReadyFuture().await(Duration.ofSeconds(45));
        when:
        sequences.setCounterValue("test", 1000, true)
        then:
        sequences.generateId("test") == 1000
    }

    def "a sequence  cannot re-issue used numbers without being forced"() {
        given:
        schema.getReadyFuture().await(Duration.ofSeconds(45));
        when:
        sequences.setCounterValue("test", 5000, true)
        and:
        sequences.setCounterValue("test", 1000, false)
        then:
        thrown HandledException
    }

    def "a sequence can re-issue used numbers when being forced"() {
        given:
        schema.getReadyFuture().await(Duration.ofSeconds(45));
        when:
        sequences.setCounterValue("test", 5000, true)
        and:
        sequences.setCounterValue("test", 1000, true)
        then:
        notThrown HandledException
    }

}
