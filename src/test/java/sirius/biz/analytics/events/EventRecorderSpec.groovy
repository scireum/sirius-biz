/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events

import sirius.db.jdbc.Databases
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

class EventRecorderSpec extends BaseSpecification {

    @Part
    private static EventRecorder recorder

    @Part
    private static Databases dbs

    def "events are recorded"() {
        when:
        recorder.record(new TestEvent1())
        recorder.record(new TestEvent1())
        recorder.record(new TestEvent1())
        recorder.record(new TestEvent1())
        and:
        recorder.record(new TestEvent2())
        recorder.record(new TestEvent2())
        recorder.record(new TestEvent2())
        recorder.record(new TestEvent2())
        and:
        recorder.process()
        then:
        dbs.get("clickhouse").createQuery("SELECT * FROM testevent1").queryList().size() == 4
        and:
        dbs.get("clickhouse").createQuery("SELECT * FROM testevent2").queryList().size() == 4
    }

    def "bufferedEvents-field is reset after processing"() {
        when:
        recorder.record(new TestEvent1())
        recorder.record(new TestEvent1())
        recorder.record(new TestEvent1())
        recorder.record(new TestEvent1())
        and:
        recorder.record(new TestEvent2())
        recorder.record(new TestEvent2())
        recorder.record(new TestEvent2())
        recorder.record(new TestEvent2())
        then:
        recorder.bufferedEvents.get() == 8
        and:
        recorder.process() == 8
        and:
        recorder.bufferedEvents.get() == 0
    }

    def "bufferedEvents-field does not get larger as MAX_BUFFER_SIZE"() {
        when:
        for (int i = 0; i < EventRecorder.MAX_BUFFER_SIZE + 20; i++) {
            recorder.record(new TestEvent1())
        }
        then:
        recorder.bufferedEvents.get() == EventRecorder.MAX_BUFFER_SIZE
        and:
        recorder.process()
        then:
        recorder.bufferedEvents.get() == 0
    }

    def "bufferedEvents-field is not incremented if the Event throws exception on save"() {
        when:
        recorder.record(new TestEvent3ThrowsExceptionOnSave())
        then:
        recorder.buffer.poll() == null
        recorder.bufferedEvents.get() == 0
        and:
        recorder.process() == 0
    }
}
