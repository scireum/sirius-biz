/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.sequences

import sirius.db.mixing.OMA
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException

import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

class SequencesSpec extends BaseSpecification {

    @Part
    private static Sequences sequences;

    @Part
    private static OMA oma;

    def setupSpec() {
        oma.getReadyFuture().await(Duration.ofSeconds(60));
    }

    def "a new sequence is automatically created"() {
        when:
        String value = sequences.generateId(String.valueOf(ThreadLocalRandom.current().nextLong()));
        then:
        value == "1"
    }

    def "a sequence is incremented by generate id"() {
        when:
        String value = sequences.generateId("test");
        String value1 = sequences.generateId("test");
        then:
        Integer.parseInt(value) == Integer.parseInt(value1) - 1
    }

    def "a new next value can be set"() {
        when:
        sequences.setCounterValue("test", 1000, false);
        String value = sequences.generateId("test");
        then:
        "1000" == value
    }

    def "a cannot be set to a lower value"() {
        when:
        String value = sequences.generateId("test");
        sequences.generateId("test");
        sequences.setCounterValue("test", Integer.parseInt(value), false);
        then:
        thrown(HandledException)
    }

    def "a can be set to a lower value when force is true"() {
        when:
        String value = sequences.generateId("test");
        sequences.generateId("test");
        sequences.setCounterValue("test", Integer.parseInt(value), true);
        then:
        notThrown(HandledException)
    }

}
