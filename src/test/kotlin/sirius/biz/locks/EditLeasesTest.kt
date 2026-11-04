/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import sirius.db.redis.Redis
import sirius.kernel.SiriusExtension
import sirius.kernel.di.std.Part
import sirius.kernel.health.HandledException
import sirius.web.controller.Controller
import sirius.web.http.TestRequest
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private const val SCOPE = EditLeasesTestController.SCOPE

/**
 * Verifies the lease semantics of [EditLeases] against the real Redis of the test environment.
 */
@ExtendWith(SiriusExtension::class)
class EditLeasesTest {

    @Test
    fun `a change to the key between watch and exec discards the transaction`() {
        // Pins the assumption the service is built on: WATCH is sent synchronously, so a write by any
        // other connection afterwards makes EXEC return null - the GET in between may use the pool.
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")

        try {
            redis.exec({ "test watch semantics" }) { jedis ->
                jedis.transaction(false).use { transaction ->
                    transaction.watch(key(id))
                    jedis.set(key(id), "changed")
                    transaction.multi()
                    transaction.expire(key(id), 5)
                    assertNull(transaction.exec(), "EXEC must be discarded after a concurrent change")
                }
            }
        } finally {
            // The plain SET above dropped the TTL, so the key would otherwise never expire.
            redis.exec({ "clean up watch semantics test" }) { jedis -> jedis.del(key(id)) }
        }
    }

    @Test
    fun `a free lease is granted and expires after the lease timeout`() {
        val id = uniqueId()

        val status = leases.acquire(SCOPE, id, "session-a")

        assertTrue(status.heldByMe)
        val ttl = ttl(id)
        assertTrue(ttl in 1..90, "TTL must follow leaseTimeout, was $ttl")
    }

    @Test
    fun `an abandoned lease expires in Redis and is then granted to another session`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")
        // Keeps the TTL set by acquire, only shortens it - so this verifies the key is created as an expiring one.
        redis.exec({ "shorten ttl" }) { jedis -> jedis.pexpire(key(id), 300) }

        awaitCondition("the lease must expire") { holder(id) == null }

        assertTrue(leases.acquire(SCOPE, id, "session-b").heldByMe)
    }

    @Test
    fun `of sessions acquiring a free lease at once exactly one gets it`() {
        val id = uniqueId()
        val start = CountDownLatch(1)
        val acquires = (1..8).map { index ->
            CompletableFuture.supplyAsync {
                start.await(5, TimeUnit.SECONDS)
                leases.acquire(SCOPE, id, "session-$index")
            }
        }

        start.countDown()
        val statuses = acquires.map { it.get(10, TimeUnit.SECONDS) }

        assertEquals(1, statuses.count { it.heldByMe }, "exactly one session must hold the lease")
        assertTrue(statuses.filter { !it.heldByMe }.all { it.heldByOther })
    }

    @Test
    fun `a second session is refused and told the lease is held by another`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")

        val status = leases.acquire(SCOPE, id, "session-b")

        assertFalse(status.heldByMe)
        assertTrue(status.heldByOther)
    }

    @Test
    fun `the holding session may acquire again`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")

        assertTrue(leases.acquire(SCOPE, id, "session-a").heldByMe)
    }

    @Test
    fun `leases of different scopes on the same id are independent`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")

        assertTrue(leases.acquire("other-editor", id, "session-b").heldByMe)
        assertTrue(leases.acquire(SCOPE, id, "session-b").heldByOther)

        leases.release("other-editor", id, "session-b")
    }

    @Test
    fun `ids looking like other keys never share a lease or collide with write tracking`() {
        val id = uniqueId()
        leases.acquire(SCOPE, "$id:writes", "session-a")
        leases.runWhileHeldBy(SCOPE, "$id:writes", "session-a") {}

        assertTrue(leases.acquire(SCOPE, id, "session-b").heldByMe)
        leases.runWhileHeldBy(SCOPE, id, "session-b") {}
        assertThrows(IllegalArgumentException::class.java) { leases.acquire("$SCOPE:x", id, "session-c") }
    }

    @Test
    fun `once the lease expired another session gets it`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")
        redis.exec({ "simulate expiry" }) { jedis -> jedis.del(key(id)) }

        assertTrue(leases.acquire(SCOPE, id, "session-b").heldByMe)
    }

    @Test
    fun `a heartbeat renews the lease of the holder`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")
        redis.exec({ "shorten ttl" }) { jedis -> jedis.expire(key(id), 5) }

        assertTrue(leases.heartbeat(SCOPE, id, "session-a").heldByMe)
        assertTrue(ttl(id) > 5, "heartbeat must restore the full lease timeout")
    }

    @Test
    fun `a heartbeat from another session fails and leaves the lease alone`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")
        redis.exec({ "shorten ttl" }) { jedis -> jedis.expire(key(id), 5) }

        val status = leases.heartbeat(SCOPE, id, "session-b")

        assertFalse(status.heldByMe)
        assertTrue(status.heldByOther)
        assertTrue(ttl(id) <= 5, "a foreign heartbeat must not extend the lease")
    }

    @Test
    fun `a heartbeat on an expired lease reports it as free`() {
        val id = uniqueId()

        val status = leases.heartbeat(SCOPE, id, "session-a")

        assertFalse(status.heldByMe)
        assertFalse(status.heldByOther)
    }

    @Test
    fun `only the holder can release the lease`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")

        leases.release(SCOPE, id, "session-b")
        assertFalse(leases.acquire(SCOPE, id, "session-b").heldByMe)

        leases.release(SCOPE, id, "session-a")
        assertTrue(leases.acquire(SCOPE, id, "session-b").heldByMe)
    }

    @Test
    fun `takeover moves the lease and the previous holder's heartbeat then fails`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")

        assertTrue(leases.takeover(SCOPE, id, "session-b").heldByMe)

        val previousHolder = leases.heartbeat(SCOPE, id, "session-a")
        assertFalse(previousHolder.heldByMe)
        assertTrue(previousHolder.heldByOther)
    }

    @Test
    fun `a blank session never holds the lease`() {
        val id = uniqueId()

        assertFalse(leases.acquire(SCOPE, id, "").heldByMe)
        assertFalse(leases.takeover(SCOPE, id, null).heldByMe)
        assertThrows(HandledException::class.java) { leases.runWhileHeldBy(SCOPE, id, null) {} }
    }

    @Test
    fun `release reports the lease as free only if the session held it`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")

        val foreignRelease = leases.release(SCOPE, id, "session-b")
        assertFalse(foreignRelease.heldByMe)
        assertTrue(foreignRelease.heldByOther, "a release ignored for a non-holder must not report the lease as free")

        assertEquals(EditLeases.FREE, leases.release(SCOPE, id, "session-a"))
    }

    @Test
    fun `runWhileHeldBy applies writes of the holder and rejects others with the editLocked code`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")
        var applied = 0

        leases.runWhileHeldBy(SCOPE, id, "session-a") { applied++ }
        val exception = assertThrows(HandledException::class.java) {
            leases.runWhileHeldBy(SCOPE, id, "session-b") { applied++ }
        }

        assertEquals(1, applied, "only the write of the holder must be applied")
        assertEquals(EditLeases.ERROR_CODE_EDIT_LOCKED, exception.getHint(Controller.ERROR_CODE).asString())
        assertEquals(0, pendingWrites(id), "a completed write must no longer count as in flight")
    }

    @Test
    fun `a takeover waits until the write of the previous holder in flight is done`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")
        val writeStarted = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        val writer = thread {
            leases.runWhileHeldBy(SCOPE, id, "session-a") {
                writeStarted.countDown()
                finishWrite.await(10, TimeUnit.SECONDS)
            }
        }
        assertTrue(writeStarted.await(5, TimeUnit.SECONDS))

        val takeover = CompletableFuture.supplyAsync { leases.takeover(SCOPE, id, "session-b") }
        Thread.sleep(500)
        assertFalse(takeover.isDone, "the takeover must wait for the write in flight")
        assertThrows(HandledException::class.java) { leases.runWhileHeldBy(SCOPE, id, "session-a") {} }

        finishWrite.countDown()
        writer.join(5000)
        assertTrue(takeover.get(5, TimeUnit.SECONDS).heldByMe)
    }

    @Test
    fun `a write outliving its registration does not hide later writes from a takeover`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")
        // Simulates a write running longer than maxWriteDuration: its registration expires while it is in flight.
        leases.runWhileHeldBy(SCOPE, id, "session-a") {
            redis.exec({ "simulate expired write registration" }) { jedis -> jedis.del(writesKey(id)) }
        }
        assertEquals(0, pendingWrites(id))

        leases.takeover(SCOPE, id, "session-b")
        val (finishWrite, writer) = startWrite(id, "session-b")
        val takeover = CompletableFuture.supplyAsync { leases.takeover(SCOPE, id, "session-c") }
        Thread.sleep(500)
        assertFalse(takeover.isDone, "the takeover must still wait for the write of the next holder")

        finishWrite.countDown()
        writer.join(5000)
        assertTrue(takeover.get(5, TimeUnit.SECONDS).heldByMe)
    }

    @Test
    fun `a takeover overtaken while waiting for pending writes reports the actual holder`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")
        val (finishWrite, writer) = startWrite(id, "session-a")

        val first = CompletableFuture.supplyAsync { leases.takeover(SCOPE, id, "session-b") }
        // The second takeover must only start once the first one moved the lease and is waiting for the write.
        awaitCondition("the first takeover must have moved the lease") { holder(id) == "session-b" }
        val second = CompletableFuture.supplyAsync { leases.takeover(SCOPE, id, "session-c") }
        awaitCondition("the second takeover must have moved the lease") { holder(id) == "session-c" }

        finishWrite.countDown()
        writer.join(5000)
        val firstStatus = first.get(5, TimeUnit.SECONDS)
        assertFalse(firstStatus.heldByMe, "a lease taken over meanwhile must not be reported as held")
        assertTrue(firstStatus.heldByOther)
        assertTrue(second.get(5, TimeUnit.SECONDS).heldByMe)
    }

    @Test
    fun `acquiring an expired lease waits until the write of the previous holder in flight is done`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")
        val writeStarted = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        val writer = thread {
            leases.runWhileHeldBy(SCOPE, id, "session-a") {
                writeStarted.countDown()
                finishWrite.await(10, TimeUnit.SECONDS)
            }
        }
        assertTrue(writeStarted.await(5, TimeUnit.SECONDS))
        redis.exec({ "simulate expiry" }) { jedis -> jedis.del(key(id)) }

        val acquire = CompletableFuture.supplyAsync { leases.acquire(SCOPE, id, "session-b") }
        Thread.sleep(500)
        assertFalse(acquire.isDone, "acquiring the expired lease must wait for the write in flight")

        finishWrite.countDown()
        writer.join(5000)
        assertTrue(acquire.get(5, TimeUnit.SECONDS).heldByMe)
    }

    @Test
    fun `a takeover stops waiting for a write running longer than the max write duration`() {
        // test.conf shortens edit-leases.maxWriteDuration to 2 seconds.
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")
        val (finishWrite, writer) = startWrite(id, "session-a")

        try {
            val startedAt = System.currentTimeMillis()
            val status = leases.takeover(SCOPE, id, "session-b")
            val waited = System.currentTimeMillis() - startedAt

            assertTrue(status.heldByMe)
            assertTrue(
                waited in 1500..6000,
                "the takeover must wait for about the max write duration, waited $waited ms"
            )
        } finally {
            finishWrite.countDown()
            writer.join(5000)
        }
    }

    @Test
    fun `write deadlines are based on the clock of Redis`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")
        val (finishWrite, writer) = startWrite(id, "session-a")

        try {
            val deadline = redis.query({ "read write deadline" }) { jedis ->
                jedis.zrangeWithScores(writesKey(id), 0, -1).single().score.toLong()
            }
            val redisNow = redis.query({ "read redis time" }) { jedis ->
                jedis.eval("local t = redis.call('TIME') return t[1] * 1000 + math.floor(t[2] / 1000)") as Long
            }
            // test.conf sets maxWriteDuration to 2 seconds.
            assertTrue(deadline - redisNow in 1000..2000, "deadline must be Redis time + maxWriteDuration")
        } finally {
            finishWrite.countDown()
            writer.join(5000)
        }
    }

    @Test
    fun `the lease route reports the status the client relies on`() {
        val id = uniqueId()

        val acquired = leaseAction(id, "acquire", "session-a")
        assertTrue(acquired.path("heldByMe").asBoolean())
        assertFalse(acquired.path("heldByOther").asBoolean())
        assertEquals(30, acquired.path("heartbeatSeconds").asInt())
        assertEquals(90, acquired.path("leaseSeconds").asInt())

        val refused = leaseAction(id, "acquire", "session-b")
        assertFalse(refused.path("heldByMe").asBoolean())
        assertTrue(refused.path("heldByOther").asBoolean())

        assertTrue(leaseAction(id, "heartbeat", "session-a").path("heldByMe").asBoolean())
        assertTrue(leaseAction(id, "takeover", "session-b").path("heldByMe").asBoolean())
        assertTrue(leaseAction(id, "heartbeat", "session-a").path("heldByOther").asBoolean())

        val released = leaseAction(id, "release", "session-b")
        assertFalse(released.path("heldByMe").asBoolean())
        assertFalse(released.path("heldByOther").asBoolean())
        assertNull(holder(id))
    }

    @Test
    fun `the lease route rejects an unknown action`() {
        val response = TestRequest.SAFEPOST("/test/edit-lease/${uniqueId()}")
            .withParameter(EditLeases.PARAM_ACTION, "steal")
            .withParameter(EditLeases.PARAM_EDITOR_SESSION, "session-a")
            .execute()

        assertFalse(response.contentAsJson.path("success").asBoolean())
    }

    @Test
    fun `a write route rejects a session not holding the lease with the editLocked code`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")

        val accepted = writeRequest(id, "session-a").execute()
        val rejected = writeRequest(id, "session-b").execute()

        assertTrue(accepted.contentAsJson.path("written").asBoolean())
        assertFalse(rejected.contentAsJson.path("success").asBoolean())
        assertEquals(EditLeases.ERROR_CODE_EDIT_LOCKED, rejected.contentAsJson.path("code").asString())
        awaitCondition("the completed write must no longer count as in flight") { pendingWrites(id) == 0L }
    }

    @Test
    fun `a takeover waits until a write request of the previous holder is completely handled`() {
        val id = uniqueId()
        leases.acquire(SCOPE, id, "session-a")
        val writeStarted = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        EditLeasesTestController.writeStarted = writeStarted
        EditLeasesTestController.finishWrite = finishWrite

        try {
            val write = writeRequest(id, "session-a").executeAsync()
            assertTrue(writeStarted.await(5, TimeUnit.SECONDS))

            val takeover = CompletableFuture.supplyAsync { leases.takeover(SCOPE, id, "session-b") }
            Thread.sleep(500)
            assertFalse(takeover.isDone, "the takeover must wait for the write request in flight")

            finishWrite.countDown()
            assertTrue(write.await(Duration.ofSeconds(5)), "the write request must complete")
            assertTrue(write.get().contentAsJson.path("written").asBoolean())
            // Released by the completion promise of the request, i.e. via endWriteAsync.
            assertTrue(takeover.get(5, TimeUnit.SECONDS).heldByMe)
            assertEquals(0, pendingWrites(id))
        } finally {
            finishWrite.countDown()
            EditLeasesTestController.writeStarted = null
            EditLeasesTestController.finishWrite = null
        }
    }

    private fun leaseAction(id: String, action: String, session: String) =
        TestRequest.SAFEPOST("/test/edit-lease/$id")
            .withParameter(EditLeases.PARAM_ACTION, action)
            .withParameter(EditLeases.PARAM_EDITOR_SESSION, session)
            .execute()
            .contentAsJson

    private fun writeRequest(id: String, session: String) =
        TestRequest.SAFEPOST("/test/edit-lease/$id/write")
            .withParameter(EditLeases.PARAM_EDITOR_SESSION, session)

    private fun holder(id: String): String? = redis.query({ "read holder" }) { jedis -> jedis.get(key(id)) }

    /**
     * Polls until the given condition holds, failing after five seconds.
     */
    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition()) {
            assertTrue(System.currentTimeMillis() < deadline, message)
            Thread.sleep(20)
        }
    }

    private fun uniqueId() = "EDIT-LEASE-TEST-" + UUID.randomUUID()

    private fun key(id: String) = "edit-lease:$SCOPE:$id"

    private fun pendingWrites(id: String): Long = redis.query({ "read pending writes" }) { jedis -> jedis.zcard(writesKey(id)) }

    private fun writesKey(id: String) = "edit-lease-writes:$SCOPE:$id"

    /**
     * Starts a write of the given session which stays in flight until the returned latch is counted down.
     */
    private fun startWrite(id: String, session: String): Pair<CountDownLatch, Thread> {
        val writeStarted = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        val writer = thread {
            leases.runWhileHeldBy(SCOPE, id, session) {
                writeStarted.countDown()
                finishWrite.await(10, TimeUnit.SECONDS)
            }
        }
        assertTrue(writeStarted.await(5, TimeUnit.SECONDS))
        return finishWrite to writer
    }

    private fun ttl(id: String): Long = redis.query({ "read ttl" }) { jedis -> jedis.ttl(key(id)) }

    companion object {
        @Part
        @JvmStatic
        private lateinit var leases: EditLeases

        @Part
        @JvmStatic
        private lateinit var redis: Redis
    }
}
