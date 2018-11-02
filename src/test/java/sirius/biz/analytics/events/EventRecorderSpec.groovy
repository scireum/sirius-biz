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

}
