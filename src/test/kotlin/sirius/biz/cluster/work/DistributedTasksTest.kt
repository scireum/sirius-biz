/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work

import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.kernel.SiriusExtension
import sirius.kernel.Tags
import sirius.kernel.async.Future
import sirius.kernel.commons.Json
import sirius.kernel.commons.Wait
import sirius.kernel.di.std.Part
import java.time.Duration
import kotlin.test.assertEquals

@Tag(Tags.NIGHTLY)
@ExtendWith(SiriusExtension::class)
class DistributedTasksTest {

    companion object {
        @Part
        private lateinit var distributedTasks: DistributedTasks

        private lateinit var fifoSynchronizer: Future
        private lateinit var prioritizedValues: MutableList<Int>

        class FifoTestExecutor : DistributedTaskExecutor {

            override fun queueName(): String = "fifo-test"

            override fun executeWork(context: ObjectNode) {
                if (context.path("test").asText() == "test") {
                    fifoSynchronizer.success()
                }
            }
        }

        class PrioritizedTestExecutor : DistributedTaskExecutor {

            override fun queueName(): String = "prioritized-test"

            override fun executeWork(context: ObjectNode) {
                Wait.seconds(2.0)
                prioritizedValues.add(context.path("value").asInt())
                if (context.path("value").asInt() == 30) {
                    fifoSynchronizer.success()
                }
            }
        }
    }

    @Test
    fun `FIFO queues executes work`() {
        fifoSynchronizer = Future()
        distributedTasks.submitFIFOTask(FifoTestExecutor::class.java, Json.createObject().put("test", "test"))
        assertTrue(fifoSynchronizer.await(Duration.ofSeconds(15)))
    }

    @Test
    fun `Prioritized queues executes work in an appropriate order`() {
        prioritizedValues = mutableListOf()
        fifoSynchronizer = Future()

        distributedTasks.submitPrioritizedTask(
                PrioritizedTestExecutor::class.java,
                "Token1",
                Json.createObject().put("value", 10)
        )
        Wait.seconds(1.0)
        distributedTasks.submitPrioritizedTask(
                PrioritizedTestExecutor::class.java,
                "Token1",
                Json.createObject().put("value", 30)
        )
        Wait.seconds(1.0)
        distributedTasks.submitPrioritizedTask(
                PrioritizedTestExecutor::class.java,
                "Token2",
                Json.createObject().put("value", 20)
        )

        fifoSynchronizer.await(Duration.ofSeconds(15))

        assertEquals(10, prioritizedValues[0])
        assertEquals(20, prioritizedValues[1])
        assertEquals(30, prioritizedValues[2])
    }
}
