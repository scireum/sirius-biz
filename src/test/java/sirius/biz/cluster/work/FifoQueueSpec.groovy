/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.cluster.work

import com.alibaba.fastjson.JSONObject
import sirius.db.redis.Redis
import sirius.kernel.BaseSpecification
import sirius.kernel.di.std.Part

class FifoQueueSpec extends BaseSpecification {

    @Part
    private static Redis redis

    def "local fifo works as expected"() {
        when:
        def fifo = new LocalFifoQueue()
        and:
        fifo.offer(new JSONObject().fluentPut("value", 10))
        fifo.offer(new JSONObject().fluentPut("value", 20))
        fifo.offer(new JSONObject().fluentPut("value", 30))
        then:
        fifo.poll().get("value") == 10
        fifo.poll().get("value") == 20
        fifo.poll().get("value") == 30
        fifo.poll() == null
    }


    def "redis fifo works as expected"() {
        when:
        def fifo = new RedisFifoQueue(redis, "fifo_test")
        and:
        fifo.offer(new JSONObject().fluentPut("value", 10))
        fifo.offer(new JSONObject().fluentPut("value", 20))
        fifo.offer(new JSONObject().fluentPut("value", 30))
        then:
        fifo.poll().get("value") == 10
        fifo.poll().get("value") == 20
        fifo.poll().get("value") == 30
        fifo.poll() == null
    }

}
