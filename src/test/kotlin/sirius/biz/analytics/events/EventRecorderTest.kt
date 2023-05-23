/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.events

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.db.jdbc.Databases
import sirius.kernel.SiriusExtension
import sirius.kernel.async.BackgroundLoop
import sirius.kernel.di.std.Part
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(SiriusExtension::class)
class EventRecorderTest {

    companion object {
        @Part
        private lateinit var recorder: EventRecorder

        @Part
        private lateinit var dbs: Databases

        @BeforeAll
        @JvmStatic
        fun setup() {
            BackgroundLoop.disable(EventProcessorLoop::class.java).await(Duration.ofMinutes(1))
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            BackgroundLoop.enable(EventProcessorLoop::class.java)
        }
    }

    @Test
    fun `Events are recorded`() {
        recorder.record(TestEvent1())
        recorder.record(TestEvent1())
        recorder.record(TestEvent1())
        recorder.record(TestEvent1())

        recorder.record(TestEvent2())
        recorder.record(TestEvent2())
        recorder.record(TestEvent2())
        recorder.record(TestEvent2())

        recorder.process()

        assertEquals(4, dbs.get("clickhouse").createQuery("SELECT * FROM testevent1").queryList().size)
        assertEquals(4, dbs.get("clickhouse").createQuery("SELECT * FROM testevent2").queryList().size)
    }

    @Test
    fun `BufferedEvents-field is reset after processing`() {
        recorder.record(TestEvent1())
        recorder.record(TestEvent1())
        recorder.record(TestEvent1())
        recorder.record(TestEvent1())

        recorder.record(TestEvent2())
        recorder.record(TestEvent2())
        recorder.record(TestEvent2())
        recorder.record(TestEvent2())

        recorder.javaClass.getDeclaredField("bufferedEvents").apply {
            isAccessible = true
            val bufferedEvents = get(recorder) as AtomicInteger
            assertEquals(8, bufferedEvents.get())
            assertEquals(8, recorder.process())
            assertEquals(0, bufferedEvents.get())
        }
    }

    @Test
    fun `BufferedEvents-field does not get larger as MAX_BUFFER_SIZE`() {
        for (i in 0 until EventRecorder.MAX_BUFFER_SIZE + 20) {
            recorder.record(TestEvent1())
        }
        recorder.javaClass.getDeclaredField("bufferedEvents").apply {
            isAccessible = true
            val bufferedEvents = get(recorder) as AtomicInteger
            assertEquals(EventRecorder.MAX_BUFFER_SIZE, bufferedEvents.get())
            recorder.process()
            assertEquals(0, bufferedEvents.get())
        }
    }

    @Test
    fun `bufferedEvents-field is not incremented if the Event throws exception on save`() {
        recorder.record(TestEvent3ThrowsExceptionOnSave())

        recorder.javaClass.getDeclaredField("buffer").apply {
            isAccessible = true
            val buffer = get(recorder) as Queue<*>
            assertNull(buffer.poll())
        }
        recorder.javaClass.getDeclaredField("bufferedEvents").apply {
            isAccessible = true
            val bufferedEvents = get(recorder) as AtomicInteger
            assertEquals(0, bufferedEvents.get())
        }
        assertEquals(0, recorder.process())
    }

}
