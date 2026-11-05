/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

/**
 * Client-side counterpart of sirius.biz.locks.EditLeases: holds the edit lease of one object for this page.
 * <p>
 * Usage:
 * <pre>
 * const api = sirius.editLease.httpApi('/my/editor/edit-lease', {CSRFToken: token});
 * const lease = sirius.editLease.create(api, {
 *     onChange: () => render(lease.getState(), lease.isRechecking()),
 *     onStale: () => reloadContent()
 * });
 * lease.start();
 * </pre>
 * Every write request of the editor has to carry <tt>editorSession=api.session</tt>, and the editor should call
 * <tt>lease.refresh()</tt> whenever the server rejects a write with the error code <tt>editLocked</tt>.
 * <p>
 * The lease is acquired by <tt>start()</tt> and renewed via a <tt>setTimeout</tt> chain, where the next heartbeat is
 * only scheduled once the previous answer arrived, and all lease requests run through one queue, so requests never
 * overlap or overtake each other. Inactivity is checked in the same tick; the page lifecycle releases the lease on
 * <tt>pagehide</tt> and re-acquires it when the page comes back from the back-forward cache.
 */
window.sirius.editLease = (function () {
    const DEFAULT_IDLE_TIMEOUT_MS = 10 * 60 * 1000;
    const DEFAULT_HEARTBEAT_MS = 30 * 1000;
    // A malformed or sub-second heartbeat interval must never make the setTimeout chain spin.
    const MIN_HEARTBEAT_MS = 1000;
    // Heartbeats and releases answer right away, so a request taking longer is considered hung.
    const REQUEST_TIMEOUT_MS = 15 * 1000;
    // Acquire and takeover wait server-side for writes of the previous holder (edit-leases.maxWriteDuration).
    const GRANT_TIMEOUT_MS = 60 * 1000;
    const GRANT_ACTIONS = ['acquire', 'takeover'];
    const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'wheel', 'scroll'];
    // Capture phase: a component stopping propagation must not hide activity; `scroll` does not bubble anyway.
    const ACTIVITY_LISTENER_OPTIONS = {passive: true, capture: true};

    /**
     * States reported by getState():
     * <ul>
     *     <li><tt>acquiring</tt>: waiting for the server, editing is not permitted yet</li>
     *     <li><tt>editing</tt>: this page holds the lease and may modify the object</li>
     *     <li><tt>readOnly</tt>: another session holds the lease, the lease is polled until it becomes free</li>
     *     <li><tt>idle</tt>: the lease was released due to inactivity, see resume()</li>
     * </ul>
     */
    const STATES = Object.freeze({
        ACQUIRING: 'acquiring',
        EDITING: 'editing',
        READ_ONLY: 'readOnly',
        IDLE: 'idle'
    });

    /**
     * Creates the server access for a route which invokes EditLeases#handleAction.
     *
     * @param url     the route of the lease endpoint
     * @param params  additional parameters sent with every lease request (e.g. the CSRFToken)
     * @param session the editor session to use; a fresh one per page is generated if omitted
     * @returns {{session: string, request: function(string): Promise<Object>, releaseOnUnload: function()}}
     */
    function httpApi(url, params, session) {
        const editorSession = session || generateSession();

        function body(action) {
            return new URLSearchParams({...(params || {}), action: action, editorSession: editorSession});
        }

        return {
            session: editorSession,
            request: function (action) {
                // Requests are queued, so a hung request would otherwise block all further lease requests.
                const timeout = GRANT_ACTIONS.includes(action) ? GRANT_TIMEOUT_MS : REQUEST_TIMEOUT_MS;
                return fetch(url, {
                    method: 'POST',
                    body: body(action),
                    // Older browsers (e.g. Safari < 16) lack AbortSignal.timeout: requests then simply never time out.
                    signal: typeof AbortSignal.timeout === 'function' ? AbortSignal.timeout(timeout) : undefined,
                    // Lets a release of a disposed editor complete even if the page is left right afterwards.
                    keepalive: action === 'release'
                }).then(response => {
                    if (!response.ok) {
                        throw new Error('Edit lease request failed with HTTP ' + response.status);
                    }
                    return response.json();
                });
            },
            releaseOnUnload: function () {
                navigator.sendBeacon(url, body('release'));
            }
        };
    }

    /**
     * Generates a random editor session. Unlike crypto.randomUUID(), crypto.getRandomValues() is also available
     * outside of secure contexts (e.g. plain HTTP).
     */
    function generateSession() {
        return Array.from(crypto.getRandomValues(new Uint8Array(16)),
            byte => byte.toString(16).padStart(2, '0')).join('');
    }

    function toMillis(seconds, fallbackMs) {
        const millis = Number(seconds) * 1000;
        return Number.isFinite(millis) && millis > 0 ? Math.max(millis, MIN_HEARTBEAT_MS) : fallbackMs;
    }

    /**
     * Creates the lease client for one edited object.
     * <p>
     * A client may be started again after it was disposed.
     *
     * @param api     the server access: <tt>request(action)</tt> resolving to <tt>{heldByMe, heldByOther,
     *                heartbeatSeconds, leaseSeconds}</tt> and <tt>releaseOnUnload()</tt> as a best-effort release
     *                which also works while the page is being unloaded (see httpApi). Outside of pagehide, releases
     *                are sent via <tt>request('release')</tt>, so they cannot overtake a later acquire
     * @param options <tt>onChange()</tt>: invoked whenever getState() or isRechecking() changed;
     *                <tt>onStale()</tt>: invoked whenever locally shown data may be outdated: the lease was lost,
     *                taken over, resumed or re-checked; <tt>idleTimeoutMs</tt>: time without pointer, keyboard,
     *                wheel or scroll activity after which an editing page releases the lease
     */
    function create(api, options) {
        options = options || {};
        const onChange = options.onChange || function () {
        };
        const onStale = options.onStale || function () {
        };
        const idleTimeoutMs = options.idleTimeoutMs || DEFAULT_IDLE_TIMEOUT_MS;

        let state = STATES.ACQUIRING;
        // Set synchronously by refresh(), i.e. before the rejected request's caller sees the error.
        let pendingRefresh = null;
        let heartbeatMs = DEFAULT_HEARTBEAT_MS;
        // Unknown until the first answer; until then the lease is assumed to last three heartbeats.
        let leaseMs = null;
        // When the last request was sent which the server answered with heldByMe: the server lease lasts at least
        // until leaseMs after this.
        let confirmedAt = 0;
        let timer = null;
        let lastActivity = Date.now();
        // Set on pagehide and dispose: no further heartbeats are scheduled until a back-forward cache restore.
        let suspended = false;
        // Set on pagehide until a back-forward cache restore: releases can then only be sent via a beacon.
        let hidden = false;
        let queue = Promise.resolve();
        // Bumped whenever answers of requests already issued must no longer be applied: on dispose, on pagehide,
        // when going idle and on refresh and takeover. Every queued task captures it when it is queued and is skipped, or drops its
        // result, if it changed.
        let generation = 0;

        function setState(newState) {
            if (state !== newState) {
                state = newState;
                onChange();
            }
        }

        function setPendingRefresh(promise) {
            pendingRefresh = promise;
            onChange();
        }

        /**
         * Queues a task. A failure is logged and retried by the next tick; with `rethrow` the returned promise
         * also rejects, so a consumer can report failures of actions the user triggered.
         */
        function enqueue(task, rethrow) {
            // Captured when queued, not when started: a task queued before a dispose/pagehide/idle must be dropped.
            const startGeneration = generation;
            const isCurrent = () => startGeneration === generation;
            const run = queue.then(async () => {
                if (!isCurrent()) {
                    return;
                }
                try {
                    await task(isCurrent);
                } catch (error) {
                    // A failed request leaves the state as it is; the next tick retries (unless it became obsolete).
                    console.error('Edit lease request failed', error);
                    if (isCurrent()) {
                        expireUnconfirmedLease();
                        schedule();
                    }
                    if (rethrow) {
                        throw error;
                    }
                }
            });
            // A rejected task must not break the chain for the tasks queued after it.
            queue = run.catch(() => {
            });
            return run;
        }

        /**
         * Sends a lease request and records when a granted lease was last confirmed.
         */
        async function send(action) {
            const sentAt = Date.now();
            const status = await api.request(action);
            if (status.heldByMe) {
                confirmedAt = sentAt;
            }
            return status;
        }

        /**
         * Stops editing once the server lease has expired for sure as no heartbeat got through (e.g. after a 401, a
         * CSRF 403 or a 5xx): the retry then runs on the acquisition path in tick(), which reloads once the lease is
         * held again.
         */
        function expireUnconfirmedLease() {
            const timeoutMs = leaseMs || 3 * heartbeatMs;
            if (state === STATES.EDITING && Date.now() - confirmedAt >= timeoutMs) {
                setState(STATES.ACQUIRING);
            }
        }

        function apply(status) {
            heartbeatMs = toMillis(status.heartbeatSeconds, DEFAULT_HEARTBEAT_MS);
            leaseMs = toMillis(status.leaseSeconds, null);
            setState(status.heldByMe ? STATES.EDITING : STATES.READ_ONLY);
            schedule();
        }

        function schedule() {
            stop();
            if (!suspended && state !== STATES.IDLE) {
                timer = setTimeout(tick, heartbeatMs);
            }
        }

        function stop() {
            if (timer) {
                clearTimeout(timer);
                timer = null;
            }
        }

        function tick() {
            timer = null;
            if (state === STATES.ACQUIRING) {
                // Retried a heartbeat after a failed acquire: the data loaded on start may have changed meanwhile.
                void requestLease('acquire', status => status.heldByMe);
                return;
            }
            if (Date.now() - lastActivity >= idleTimeoutMs) {
                const wasEditing = state === STATES.EDITING;
                setState(STATES.IDLE);
                generation++;
                if (wasEditing) {
                    void enqueue(async () => {
                        await send('release');
                    });
                }
                return;
            }
            if (state === STATES.READ_ONLY) {
                // Polls a read-only page, so it takes the lease over once the holder freed it or it expired.
                void requestLease('acquire', status => status.heldByMe);
                return;
            }
            void enqueue(heartbeat);
        }

        /**
         * Queues an acquire/takeover; `stale` decides from the answer whether the shown data may be outdated.
         */
        function requestLease(action, stale, rethrow) {
            return enqueue(async isCurrent => {
                const status = await send(action);
                if (!isCurrent()) {
                    releaseLateGrant(status);
                    return;
                }
                apply(status);
                if (stale(status)) {
                    onStale();
                }
            }, rethrow);
        }

        async function heartbeat(isCurrent) {
            const status = await send('heartbeat');
            if (!isCurrent()) {
                return;
            }
            if (status.heldByMe) {
                apply(status);
                return;
            }
            // The lease was lost - taken over, or expired (e.g. after the laptop slept). Either way what is on
            // screen may be outdated.
            if (status.heldByOther) {
                apply(status);
                onStale();
                return;
            }
            // Take a free lease back right away, but stop editing until it is ours again: should the acquire
            // fail, the retry then runs on the acquisition path in tick(), which reloads once the lease is held.
            setState(STATES.ACQUIRING);
            const next = await send('acquire');
            if (!isCurrent()) {
                releaseLateGrant(next);
                return;
            }
            apply(next);
            onStale();
        }

        function onActivity() {
            lastActivity = Date.now();
        }

        /**
         * Stops heartbeats and drops the answers of all requests already issued.
         */
        function suspend() {
            stop();
            generation++;
            suspended = true;
        }

        /**
         * Releases the lease whatever the state while the page is being hidden: the server only releases it for its
         * holder, and an acquire which was granted but not yet answered (state <tt>acquiring</tt>) must not block the
         * object either.
         */
        function releaseOnUnload() {
            api.releaseOnUnload();
        }

        /**
         * Releases the lease whatever the state when the client is disposed. Unlike a beacon, the release is queued:
         * it is sent after the requests already issued (so it also frees a lease they are granted) and before the
         * acquire of a start() following right away, which a late release would otherwise delete again.
         */
        function releaseQueued() {
            queue = queue.then(() => api.request('release')).catch(error => {
                console.error('Edit lease request failed', error);
            });
        }

        /**
         * Releases a lease granted by an acquire which was still in flight when the page was hidden: the beacon sent
         * back then was processed first, so the lease would otherwise stay blocked. After dispose() nothing is to be
         * done, as its queued release is sent after the acquire.
         */
        function releaseLateGrant(status) {
            if (suspended && hidden && status.heldByMe) {
                releaseOnUnload();
            }
        }

        function onPageHide() {
            suspend();
            hidden = true;
            releaseOnUnload();
        }

        function removeListeners() {
            ACTIVITY_EVENTS.forEach(eventName => document.removeEventListener(eventName,
                onActivity,
                ACTIVITY_LISTENER_OPTIONS));
            window.removeEventListener('pagehide', onPageHide);
            window.removeEventListener('pageshow', onPageShow);
        }

        function onPageShow(event) {
            // Never fires after dispose (the listener is removed), so clearing `suspended` cannot revive the lease.
            if (event.persisted) {
                suspended = false;
                hidden = false;
                if (state === STATES.IDLE) {
                    // Paused for inactivity before the page was hidden: the lease is only taken back via resume(),
                    // as someone else may have started editing meanwhile.
                    return;
                }
                lastActivity = Date.now();
                setState(STATES.ACQUIRING);
                void requestLease('acquire', () => true);
            }
        }

        return {
            /**
             * Registers the activity and page lifecycle listeners and acquires the lease.
             */
            start: function () {
                // Revives a disposed client: dispose() suspended it and bumped the generation.
                suspended = false;
                hidden = false;
                lastActivity = Date.now();
                setState(STATES.ACQUIRING);
                ACTIVITY_EVENTS.forEach(eventName => document.addEventListener(eventName,
                    onActivity,
                    ACTIVITY_LISTENER_OPTIONS));
                window.addEventListener('pagehide', onPageHide);
                window.addEventListener('pageshow', onPageShow);
                return requestLease('acquire', () => false);
            },
            /**
             * Releases the lease, stops all heartbeats and removes all listeners, e.g. when a modal editor is
             * closed. The client may be started again afterwards.
             */
            dispose: function () {
                if (suspended) {
                    // Already disposed, or the page is being hidden, which released the lease already.
                    removeListeners();
                    return;
                }
                suspend();
                releaseQueued();
                removeListeners();
            },
            /**
             * Returns the current state, one of sirius.editLease.STATES.
             */
            getState: function () {
                return state;
            },
            /**
             * Determines if the page may currently modify the object.
             */
            canEdit: function () {
                return state === STATES.EDITING;
            },
            /**
             * Determines if a re-check triggered by an `editLocked` rejection is pending, see refresh().
             */
            isRechecking: function () {
                return pendingRefresh !== null;
            },
            /**
             * Moves the lease to this page after the user confirmed; the previous holder can no longer save.
             * <p>
             * The returned promise rejects if the request failed, so the failure can be reported to the user. The
             * page then stays read-only and keeps polling the lease.
             */
            takeover: function () {
                // A poll firing while the takeover waits for pending writes would queue an acquire behind it, which
                // then reports the lease as held and reloads a second time. So the next poll is cancelled and the
                // answer of one already in flight dropped (it would schedule the next poll). The answer of the
                // takeover schedules again.
                stop();
                generation++;
                return requestLease('takeover', () => true, true);
            },
            /**
             * Re-acquires the lease after an inactivity pause.
             * <p>
             * The returned promise rejects if the request failed, so the failure can be reported to the user. The
             * page then keeps retrying to acquire the lease.
             */
            resume: function () {
                lastActivity = Date.now();
                // Leaves idle right away, so a failed request is retried on the acquisition path in tick().
                stop();
                setState(STATES.ACQUIRING);
                return requestLease('acquire', () => true, true);
            },
            /**
             * Re-checks the lease after the server rejected a write with `editLocked`. Only an editing page
             * re-checks: a late write rejected while idle or read-only must not silently re-acquire the lease.
             * Rejections arriving while a re-check is pending share it, so a burst of rejected writes reloads only
             * once.
             */
            refresh: function () {
                if (pendingRefresh) {
                    return pendingRefresh;
                }
                if (state !== STATES.EDITING) {
                    return Promise.resolve();
                }
                // Stop editing until the re-check answered: should it fail (even if it took the lease server-side),
                // the retry runs on the acquisition path in tick(), which reloads once the lease is held. The
                // pending heartbeat is cancelled, as it would queue a second acquire; apply() or the failure
                // reschedules.
                stop();
                // Drops the answer of a heartbeat which is still in flight: sent before the write was rejected, it
                // could still report heldByMe and switch back to editing before the re-check answers.
                generation++;
                setState(STATES.ACQUIRING);
                setPendingRefresh(requestLease('acquire', () => true).finally(() => {
                    setPendingRefresh(null);
                }));
                return pendingRefresh;
            }
        };
    }

    return {
        STATES: STATES,
        create: create,
        httpApi: httpApi
    };
}());
