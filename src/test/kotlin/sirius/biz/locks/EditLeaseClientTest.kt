/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Verifies the client of [EditLeases] (<tt>/assets/scripts/edit-lease.js</tt>) in a simulated browser.
 *
 * The script runs in GraalJS. Timers and <tt>Date.now()</tt> follow a fake clock moved by [Browser.advance], and the
 * lease endpoint is replaced by a fake whose requests stay pending until a test answers or fails them, in order. As
 * GraalJS settles all promises whenever a call from Kotlin returns, every step can be asserted right away.
 */
class EditLeaseClientTest {

    private lateinit var browser: Browser

    @AfterEach
    fun closeBrowser() {
        browser.close()
    }

    @Test
    fun `httpApi posts the action, the editor session and the extra parameters`() {
        browser = Browser()

        browser.js("httpApi = sirius.editLease.httpApi('/editor/lease', {CSRFToken: 'token'}, 'session-1')")
        browser.js("result = track(httpApi.request('heartbeat'))")

        assertEquals("resolved", browser.string("result.state"))
        assertEquals("/editor/lease", browser.string("fetches[0].url"))
        assertEquals("POST", browser.string("fetches[0].method"))
        assertEquals("CSRFToken=token&action=heartbeat&editorSession=session-1", browser.string("fetches[0].body"))
    }

    @Test
    fun `httpApi aborts hung requests, waiting longer for acquire and takeover`() {
        browser = Browser()

        browser.js("httpApi = sirius.editLease.httpApi('/editor/lease', {}, 'session-1')")
        browser.js("['heartbeat', 'release', 'acquire', 'takeover'].forEach(action => httpApi.request(action))")

        assertTrue(browser.bool("fetches.every(fetch => fetch.signal)"), "every request must be abortable")
        assertEquals("15000,15000,60000,60000", browser.string("abortTimeouts.join()"))
    }

    @Test
    fun `httpApi rejects on an HTTP error`() {
        browser = Browser()
        browser.js("response = {ok: false, status: 403}")

        browser.js("result = track(sirius.editLease.httpApi('/editor/lease', {}, 'session-1').request('heartbeat'))")

        assertEquals("rejected", browser.string("result.state"))
        assertTrue(browser.string("result.error").contains("HTTP 403"))
    }

    @Test
    fun `httpApi releases via a beacon`() {
        browser = Browser()

        browser.js("sirius.editLease.httpApi('/editor/lease', {CSRFToken: 'token'}, 'session-1').releaseOnUnload()")

        assertEquals("/editor/lease", browser.string("beacons[0].url"))
        assertEquals("CSRFToken=token&action=release&editorSession=session-1", browser.string("beacons[0].body"))
    }

    @Test
    fun `httpApi generates a session outside of secure contexts`() {
        browser = Browser(secureContext = false)

        val first = browser.string("sirius.editLease.httpApi('/editor/lease').session")
        val second = browser.string("sirius.editLease.httpApi('/editor/lease').session")

        assertTrue(first.matches(Regex("[0-9a-f]{32}")), "unexpected session $first")
        assertNotEquals(first, second)
    }

    @Test
    fun `create works without options`() {
        browser = Browser()

        browser.js("sirius.editLease.create(createApi())")
    }

    @Test
    fun `heartbeats keep an editing page editing`() {
        browser = Browser()
        browser.startLease()

        repeat(5) {
            browser.js("fire(document, 'keydown')")
            browser.advance(30_000)
            assertEquals("heartbeat", browser.answer("HELD"))
        }

        assertEquals("editing", browser.state())
    }

    @Test
    fun `a missing or sub-second heartbeat interval cannot make the timer spin`() {
        for ((heartbeatSeconds, expectedDelay) in listOf("undefined" to 30_000, "0" to 30_000, "0.2" to 1000)) {
            browser = Browser()
            browser.js("api = createApi(); sirius.editLease.create(api).start(); delays.length = 0")

            browser.answer("{heldByMe: true, heldByOther: false, heartbeatSeconds: $heartbeatSeconds}")

            assertEquals(expectedDelay, browser.js("delays[0]").asInt(), "heartbeatSeconds=$heartbeatSeconds")
            browser.close()
        }
        browser = Browser()
    }

    @Test
    fun `a lease taken over turns the page read-only and reloads`() {
        browser = Browser()
        browser.startLease()

        browser.advance(30_000)
        browser.answer("OTHER")

        assertEquals("readOnly", browser.state())
        assertEquals(1, browser.reloads())
    }

    @Test
    fun `lasting heartbeat failures stop editing once the lease expired for sure`() {
        browser = Browser()
        browser.startLease()

        repeat(2) {
            browser.advance(30_000)
            browser.fail()
        }
        assertEquals("editing", browser.state(), "the server lease is still valid after 60 s")

        browser.advance(30_000)
        browser.fail()
        assertEquals("acquiring", browser.state(), "the server lease expired after 90 s")

        browser.advance(30_000)
        assertEquals("acquire", browser.pending())
        browser.answer("HELD")
        assertEquals("editing", browser.state())
        assertEquals(1, browser.reloads(), "the content must be reloaded once the lease is held again")
    }

    @Test
    fun `a lease which expired meanwhile is taken back and reloaded`() {
        browser = Browser()
        browser.startLease()

        browser.advance(30_000)
        assertEquals("heartbeat", browser.answer("FREE"))
        assertEquals("acquiring", browser.state())
        assertEquals("acquire", browser.answer("HELD"))

        assertEquals("editing", browser.state())
        assertEquals(1, browser.reloads())
    }

    @Test
    fun `refresh re-checks once for a burst of rejected writes and reloads`() {
        browser = Browser()
        browser.startLease()

        browser.js("recheck = lease.refresh()")
        assertTrue(browser.bool("lease.refresh() === recheck"), "rejections during a re-check must share it")
        assertTrue(browser.bool("lease.isRechecking()"))
        browser.answer("HELD")

        assertEquals("editing", browser.state())
        assertEquals(1, browser.reloads())
        assertFalse(browser.bool("lease.isRechecking()"))
    }

    @Test
    fun `refresh drops the answer of a heartbeat which was in flight when the write was rejected`() {
        browser = Browser()
        browser.startLease()
        browser.advance(30_000)
        assertEquals("heartbeat", browser.pending())

        browser.js("lease.refresh(); changesBefore = changes.length")
        browser.answer("HELD")
        assertFalse(
            browser.bool("changes.slice(changesBefore).includes('editing')"),
            "must not edit while re-checking"
        )

        browser.answer("OTHER")
        assertEquals("readOnly", browser.state())
    }

    @Test
    fun `a failed takeover rejects and keeps polling`() {
        browser = Browser()
        browser.startLease("OTHER")

        browser.js("result = track(lease.takeover())")
        browser.fail()

        assertEquals("rejected", browser.string("result.state"))
        assertEquals("readOnly", browser.state())
        browser.advance(30_000)
        assertEquals("acquire", browser.pending())
    }

    @Test
    fun `inactivity releases the lease, a failed resume rejects and keeps retrying`() {
        browser = Browser()
        browser.startLease()
        browser.goIdle()
        assertTrue(browser.bool("api.requests.some(request => request.action === 'release')"))

        browser.js("result = track(lease.resume())")
        browser.fail()

        assertEquals("rejected", browser.string("result.state"))
        assertEquals("acquiring", browser.state())
        browser.advance(30_000)
        assertEquals("acquire", browser.pending())
        browser.answer("HELD")
        assertEquals("editing", browser.state())
    }

    @Test
    fun `dispose releases the lease once and stops the heartbeats`() {
        browser = Browser()
        browser.startLease()

        browser.js("lease.dispose(); lease.dispose()")

        assertEquals(1, browser.js("api.releases").asInt())
        assertEquals(0, browser.js("timers.length").asInt())
    }

    @Test
    fun `a disposed client can be started again`() {
        browser = Browser()
        browser.startLease()
        browser.js("lease.dispose()")

        browser.js("lease.start()")
        browser.answer("HELD")

        assertEquals("editing", browser.state())
        assertEquals(1, browser.js("timers.length").asInt(), "heartbeats must run again")
    }

    @Test
    fun `pagehide releases even while the acquire is pending`() {
        browser = Browser()
        browser.js("api = createApi(); sirius.editLease.create(api).start()")

        browser.js("fire(window, 'pagehide')")

        assertEquals(1, browser.js("api.releases").asInt())
    }

    @Test
    fun `an acquire granted after pagehide is released again`() {
        browser = Browser()
        browser.js("api = createApi(); sirius.editLease.create(api).start()")
        assertEquals("acquire", browser.pending())
        browser.js("fire(window, 'pagehide')")

        browser.answer("HELD")

        assertEquals(2, browser.js("api.releases").asInt())
    }

    @Test
    fun `a page restored from the back-forward cache re-acquires and reloads`() {
        browser = Browser()
        browser.startLease()

        browser.js("fire(window, 'pagehide'); fire(window, 'pageshow', {persisted: true})")
        browser.answer("HELD")

        assertEquals("editing", browser.state())
        assertEquals(1, browser.reloads())
    }

    @Test
    fun `every kind of activity keeps the page editing, also when a component stops its propagation`() {
        browser = Browser()
        browser.startLease()

        for (event in listOf("pointerdown", "keydown", "wheel", "scroll")) {
            assertTrue(
                browser.bool("document.options.$event?.capture === true"),
                "$event must be observed in the capture phase"
            )
            browser.advanceAnswering(5 * 60_000)
            browser.js("fire(document, '$event')")
        }
        browser.advanceAnswering(5 * 60_000)

        assertEquals("editing", browser.state(), "20 minutes with activity every 5 minutes must not go idle")
    }

    @Test
    fun `the next heartbeat is only sent once the previous one was answered`() {
        browser = Browser()
        browser.startLease()

        browser.advance(30_000)
        browser.advance(50_000)
        assertEquals("heartbeat", browser.pending(), "no second heartbeat while the first is unanswered")

        browser.answer("HELD")
        browser.advance(30_000)
        assertEquals("heartbeat", browser.pending())
    }

    @Test
    fun `a read-only page polls and takes the lease once it is free`() {
        browser = Browser()
        browser.startLease("OTHER")

        browser.advance(30_000)
        assertEquals("acquire", browser.answer("OTHER"))
        assertEquals("readOnly", browser.state())
        assertEquals(0, browser.reloads())

        browser.advance(30_000)
        assertEquals("acquire", browser.answer("HELD"))
        assertEquals("editing", browser.state())
        assertEquals(1, browser.reloads(), "the content must be reloaded once the lease is held")
    }

    @Test
    fun `an inactive read-only page goes idle without releasing or polling`() {
        browser = Browser()
        browser.startLease("OTHER")

        browser.goIdle("OTHER")
        browser.advance(5 * 60_000)

        assertEquals("", browser.pending())
        assertFalse(browser.bool("api.requests.some(request => request.action === 'release')"))
        assertEquals(0, browser.js("api.releases").asInt())
    }

    @Test
    fun `a page restored from the back-forward cache counts as active`() {
        browser = Browser()
        browser.startLease()
        browser.advanceAnswering(9 * 60_000)

        browser.js("fire(window, 'pagehide'); fire(window, 'pageshow', {persisted: true})")
        browser.answer("HELD")
        browser.advanceAnswering(2 * 60_000)

        assertEquals("editing", browser.state(), "the inactivity must count from the restore")
    }

    /**
     * A browser simulated in GraalJS, which has loaded edit-lease.js.
     */
    private class Browser(secureContext: Boolean = true) : AutoCloseable {

        private val context = Context.newBuilder("js").option("engine.WarnInterpreterOnly", "false").build()

        init {
            context.eval("js", BROWSER)
            if (!secureContext) {
                context.eval("js", "delete crypto.randomUUID")
            }
            context.eval(Source.newBuilder("js", SCRIPT, "edit-lease.js").build())
        }

        fun js(code: String): Value = context.eval("js", code)

        fun string(code: String): String = js(code).asString()

        fun bool(code: String): Boolean = js(code).asBoolean()

        fun state() = string("lease.getState()")

        fun reloads() = js("reloads.length").asInt()

        fun pending() = string("api.pending()")

        /**
         * Answers the oldest pending lease request with the given status and returns its action.
         */
        fun answer(status: String) = string("api.answer($status)")

        /**
         * Fails the oldest pending lease request and returns its action.
         */
        fun fail() = string("api.fail()")

        /**
         * Creates and starts a lease client, answering its first acquire with the given status.
         */
        fun startLease(firstAnswer: String = "HELD") {
            js("startLease()")
            answer(firstAnswer)
        }

        /**
         * Moves the fake clock forward, running each due timer in its own call, so that the promises it creates
         * are settled before the next one runs.
         */
        fun advance(millis: Long) {
            js("advanceTo = now + $millis")
            while (bool("runNextTimer()")) {
                // Runs until no timer is due anymore.
            }
        }

        /**
         * Moves the fake clock forward in heartbeat steps, answering every lease request with the given status.
         */
        fun advanceAnswering(millis: Long, status: String = "HELD") {
            repeat((millis / 30_000).toInt()) {
                advance(30_000)
                answerAll(status)
            }
        }

        /**
         * Keeps answering lease requests with the given status until the page went idle due to inactivity.
         */
        fun goIdle(status: String = "HELD") {
            repeat(25) {
                if (state() == "idle") {
                    return
                }
                advance(30_000)
                answerAll(status)
            }
            assertEquals("idle", state())
        }

        private fun answerAll(status: String) {
            while (pending().isNotEmpty()) {
                answer(status)
            }
        }

        override fun close() {
            context.close()
        }
    }

    companion object {
        private val SCRIPT = EditLeaseClientTest::class.java.getResource("/default/assets/scripts/edit-lease.js")!!
            .readText()

        /**
         * The browser APIs used by edit-lease.js (GraalJS only provides plain ECMAScript), backed by fakes which
         * record their use, and the fake lease endpoint.
         */
        private val BROWSER = """
            console.error = () => {};

            var now = 1000000;
            var advanceTo = now;
            var timers = [];
            var delays = [];
            var nextTimerId = 1;
            Date.now = () => now;
            function setTimeout(callback, delay) {
                delays.push(delay);
                const id = nextTimerId++;
                timers.push({id, at: now + (Number.isFinite(delay) ? delay : 0), callback});
                return id;
            }
            function clearTimeout(id) {
                timers = timers.filter(timer => timer.id !== id);
            }
            function runNextTimer() {
                timers.sort((a, b) => a.at - b.at);
                if (!timers.length || timers[0].at > advanceTo) {
                    now = advanceTo;
                    return false;
                }
                const timer = timers.shift();
                now = timer.at;
                timer.callback();
                return true;
            }

            function eventTarget() {
                return {
                    listeners: {},
                    options: {},
                    addEventListener(type, listener, options) {
                        (this.listeners[type] ||= new Set()).add(listener);
                        this.options[type] = options;
                    },
                    removeEventListener(type, listener) {
                        this.listeners[type]?.delete(listener);
                    }
                };
            }
            var window = eventTarget();
            window.sirius = {};
            var sirius = window.sirius;
            var document = eventTarget();
            function fire(target, type, event = {}) {
                [...(target.listeners[type] || [])].forEach(listener => listener(event));
            }

            class URLSearchParams {
                constructor(init) {
                    this.entries = Object.entries(init || {}).map(([key, value]) => [key, String(value)]);
                }
                toString() {
                    return this.entries.map(([key, value]) => key + '=' + value).join('&');
                }
            }
            var crypto = {
                getRandomValues(array) {
                    for (let i = 0; i < array.length; i++) {
                        array[i] = Math.floor(Math.random() * 256);
                    }
                    return array;
                },
                randomUUID() {
                    return 'only-available-in-secure-contexts';
                }
            };
            var abortTimeouts = [];
            var AbortSignal = {
                timeout(millis) {
                    abortTimeouts.push(millis);
                    return {aborted: false};
                }
            };
            var beacons = [];
            var navigator = {
                sendBeacon(url, body) {
                    beacons.push({url, body: String(body)});
                    return true;
                }
            };
            var fetches = [];
            var response = {ok: true, status: 200};
            function fetch(url, init) {
                fetches.push({url, method: init.method, signal: init.signal, body: String(init.body)});
                return Promise.resolve({...response, json: () => Promise.resolve(HELD)});
            }

            function track(promise) {
                const result = {state: 'pending'};
                promise.then(() => result.state = 'resolved', error => {
                    result.state = 'rejected';
                    result.error = String(error);
                });
                return result;
            }

            var HELD = {heldByMe: true, heldByOther: false, heartbeatSeconds: 30, leaseSeconds: 90};
            var OTHER = {heldByMe: false, heldByOther: true, heartbeatSeconds: 30, leaseSeconds: 90};
            var FREE = {heldByMe: false, heldByOther: false, heartbeatSeconds: 30, leaseSeconds: 90};

            function createApi() {
                const requests = [];
                function next() {
                    const request = requests.find(candidate => !candidate.done);
                    if (!request) {
                        throw new Error('expected a pending request');
                    }
                    request.done = true;
                    return request;
                }
                const api = {
                    session: 'test-session',
                    requests,
                    releases: 0,
                    request(action) {
                        return new Promise((resolve, reject) => requests.push({action, resolve, reject}));
                    },
                    releaseOnUnload() {
                        api.releases++;
                    },
                    answer(status) {
                        const request = next();
                        request.resolve(status);
                        return request.action;
                    },
                    fail() {
                        const request = next();
                        request.reject(new Error('Edit lease request failed with HTTP 500'));
                        return request.action;
                    },
                    pending() {
                        return requests.filter(request => !request.done).map(request => request.action).join();
                    }
                };
                return api;
            }

            var api, lease, changes, reloads;
            function startLease() {
                api = createApi();
                changes = [];
                reloads = [];
                lease = sirius.editLease.create(api, {
                    onChange: () => changes.push(lease.getState()),
                    onStale: () => reloads.push(lease.getState())
                });
                lease.start();
            }
        """.trimIndent()
    }
}
