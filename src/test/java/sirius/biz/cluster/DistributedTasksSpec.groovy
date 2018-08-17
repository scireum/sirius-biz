/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster

import com.alibaba.fastjson.JSONObject
import sirius.biz.cluster.work.DistributedTaskExecutor
import sirius.biz.cluster.work.DistributedTasks
import sirius.kernel.BaseSpecification
import sirius.kernel.async.Future
import sirius.kernel.di.std.Part

import java.time.Duration

class DistributedTasksSpec extends BaseSpecification {

    @Part
    private static DistributedTasks distributedTasks

    private static Future synchronizer

    static class TestWork extends DistributedTaskExecutor {

        @Override
        String queueName() {
            return "test"
        }

        @Override
        void executeWork(JSONObject context) throws Exception {
            synchronizer.success()
        }
    }

    def "DistributedTasks executes work"() {
        setup:
        synchronizer = new Future()
        when:
        distributedTasks.submitTask(TestWork.class, new JSONObject())
        then:
        synchronizer.await(Duration.ofSeconds(15))
    }

}
