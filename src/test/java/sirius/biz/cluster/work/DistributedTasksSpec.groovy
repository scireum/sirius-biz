/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work

import com.alibaba.fastjson.JSONObject
import org.junit.jupiter.api.Tag
import sirius.biz.cluster.work.DistributedTaskExecutor
import sirius.biz.cluster.work.DistributedTasks
import sirius.kernel.BaseSpecification
import sirius.kernel.Tags
import sirius.kernel.async.Future
import sirius.kernel.commons.Wait
import sirius.kernel.di.std.Part

import java.time.Duration

@Tag(Tags.NIGHTLY)
class DistributedTasksSpec extends BaseSpecification {

    @Part
    private static DistributedTasks distributedTasks

    private static Future fifoSynchronizer
    private static List<Integer> prioritizedValues

    static class FifoTestExecutor implements DistributedTaskExecutor {

        @Override
        String queueName() {
            return "fifo-test"
        }

        @Override
        void executeWork(JSONObject context) throws Exception {
            if (context.get("test") == "test") {
                fifoSynchronizer.success()
            }
        }
    }

    static class PrioritizedTestExecutor implements DistributedTaskExecutor {

        @Override
        String queueName() {
            return "prioritized-test"
        }

        @Override
        void executeWork(JSONObject context) throws Exception {
            Wait.seconds(2)
            prioritizedValues.add(context.get("value"))
            if (context.get("value") == 30) {
                fifoSynchronizer.success()
            }
        }
    }

    def "FIFO queues executes work"() {
        setup:
        fifoSynchronizer = new Future()
        when:
        distributedTasks.submitFIFOTask(FifoTestExecutor.class, new JSONObject().fluentPut("test", "test"))
        then:
        fifoSynchronizer.await(Duration.ofSeconds(15))
    }

    def "Prioritized queues executes work in an appropriate order"() {
        setup:
        prioritizedValues = new ArrayList<>()
        fifoSynchronizer = new Future()
        when:
        distributedTasks.submitPrioritizedTask(PrioritizedTestExecutor.class,
                                               "Token1",
                                               new JSONObject().fluentPut("value", 10))
        Wait.seconds(1)
        distributedTasks.submitPrioritizedTask(PrioritizedTestExecutor.class,
                                               "Token1",
                                               new JSONObject().fluentPut("value", 30))
        Wait.seconds(1)
        distributedTasks.submitPrioritizedTask(PrioritizedTestExecutor.class,
                                               "Token2",
                                               new JSONObject().fluentPut("value", 20))
        then:
        fifoSynchronizer.await(Duration.ofSeconds(15))
        and:
        prioritizedValues.get(0) == 10
        prioritizedValues.get(1) == 20
        prioritizedValues.get(2) == 30
    }

}
