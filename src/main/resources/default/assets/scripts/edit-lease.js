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
 *     onChange: () => render(lease.getState(), lease.isRechecking(), lease.isHeldByOther()),
 *     onStale: () => reloadContent(),
 *     onReadOnlyPoll: () => refreshContent()
 * });
 * lease.start();
 * </pre>
 * Every write request of the editor has to carry <tt>editorSession=api.session</tt>, and the editor should call
 * <tt>lease.refresh()</tt> whenever the server rejects a write with the error code <tt>editLocked</tt>, and
 * <tt>lease.notifyChanged()</tt> after every successful write.
 * <p>
 * The lease is acquired by <tt>start()</tt> and renewed via a <tt>setTimeout</tt> chain, where the next heartbeat is
 * only scheduled once the previous answer arrived, and all lease requests run through one queue, so requests never
 * overlap or overtake each other. Inactivity is checked in the same tick; the page lifecycle releases the lease on
 * <tt>pagehide</tt> and re-acquires it when the page comes back from the back-forward cache.
 * <p>
 * As browsers throttle or even pause timers of hidden pages (e.g. Safari), a page which becomes visible again checks
 * the lease right away instead of waiting for its next tick. Pages of the same browser editing the same object also
 * tell each other via a <tt>BroadcastChannel</tt> when they release the lease, so a waiting page takes it over at
 * once instead of with its next poll, and when they changed the object, so a read-only page shows the change at once
 * instead of with its next poll. Changes made in other browsers are still shown with the next poll.
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
    // A release sent via a beacon is announced before the server processed it: other pages wait a moment before
    // they check the lease, so their acquire does not overtake the release.
    const UNCONFIRMED_RELEASE_DELAY_MS = 1000;
    const CHANNEL_PREFIX = 'sirius-edit-lease:';
    // A burst of writes (e.g. reordering) is shown to read-only pages with one refresh.
    const CHANGE_DEBOUNCE_MS = 250;
    const MESSAGE_RELEASED = 'released';
    const MESSAGE_CHANGED = 'changed';
    const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'wheel', 'scroll'];
    // Capture phase: a component stopping propagation must not hide activity; `scroll` does not bubble anyway.
    const ACTIVITY_LISTENER_OPTIONS = {passive: true, capture: true};

    /**
     * States reported by getState():
     * <ul>
     *     <li><tt>acquiring</tt>: waiting for the server, editing is not permitted yet</li>
     *     <li><tt>editing</tt>: this page holds the lease and may modify the object</li>
     *     <li><tt>readOnly</tt>: another session holds the lease, the lease is polled until it becomes free</li>
     *     <li><tt>idle</tt>: the lease was released due to inactivity, see resume(); it is still checked (but never
     *     taken back), so isHeldByOther() reports whether another session started editing meanwhile</li>
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
     * @returns {{session: string, request: function(string): Promise<Object>, releaseOnUnload: function(),
     * announceChange: function(), listen: function(Object): function()}}
     */
    function httpApi(url, params, session) {
        const editorSession = session || generateSession();
        const channelName = CHANNEL_PREFIX + url;

        function body(action) {
            return new URLSearchParams({...(params || {}), action: action, editorSession: editorSession});
        }

        /**
         * Tells other pages of this browser using the same lease route that this page released the lease or
         * changed the object.
         */
        function announce(type, confirmed) {
            if (typeof BroadcastChannel !== 'function') {
                return;
            }
            // A short-lived channel: an open one keeps some browsers from putting the page into the back-forward
            // cache. Messages already posted are still delivered after close().
            const channel = new BroadcastChannel(channelName);
            channel.postMessage({type: type, session: editorSession, confirmed: confirmed});
            channel.close();
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
                }).then(status => {
                    if (action === 'release' && !status.heldByOther) {
                        announce(MESSAGE_RELEASED, true);
                    }
                    return status;
                });
            },
            releaseOnUnload: function () {
                navigator.sendBeacon(url, body('release'));
                announce(MESSAGE_RELEASED, false);
            },
            /**
             * Tells other pages of this browser using the same lease route that the object was just changed.
             */
            announceChange: function () {
                announce(MESSAGE_CHANGED, true);
            },
            /**
             * Listens to other pages of this browser using the same lease route: <tt>listeners.released()</tt> is
             * invoked once one released the lease, <tt>listeners.changed()</tt> once one changed the object.
             * Returns a function which stops listening.
             */
            listen: function (listeners) {
                if (typeof BroadcastChannel !== 'function') {
                    return function () {
                    };
                }
                const released = listeners.released || function () {
                };
                const changed = listeners.changed || function () {
                };
                const channel = new BroadcastChannel(channelName);
                const delayed = new Set();
                channel.onmessage = event => {
                    if (!event.data || event.data.session === editorSession) {
                        return;
                    }
                    if (event.data.type === MESSAGE_CHANGED) {
                        changed();
                        return;
                    }
                    if (event.data.confirmed) {
                        released();
                        return;
                    }
                    const timeout = setTimeout(() => {
                        delayed.delete(timeout);
                        released();
                    }, UNCONFIRMED_RELEASE_DELAY_MS);
                    delayed.add(timeout);
                };
                return function () {
                    channel.close();
                    delayed.forEach(clearTimeout);
                    delayed.clear();
                };
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
     *                are sent via <tt>request('release')</tt>, so they cannot overtake a later acquire. Optionally
     *                <tt>listen({released, changed})</tt>, returning a function which stops listening, to learn right
     *                away when another page of this browser released the lease or changed the object, and
     *                <tt>announceChange()</tt> to tell those pages about a change of this page
     * @param options <tt>onChange()</tt>: invoked whenever getState(), isRechecking() or isHeldByOther() changed;
     *                <tt>onStale()</tt>: invoked whenever locally shown data may be outdated: the lease was lost,
     *                taken over, resumed or re-checked; <tt>onReadOnlyPoll()</tt>: invoked after every poll of a
     *                read-only page which found the lease still held by another session, and whenever another page of
     *                this browser announced a change, so the page can show the changes made by the holder;
     *                <tt>idleTimeoutMs</tt>: time without pointer, keyboard, wheel or
     *                scroll activity after which an editing page releases the lease
     */
    function create(api, options) {
        options = options || {};
        const onChange = options.onChange || function () {
        };
        const onStale = options.onStale || function () {
        };
        const onReadOnlyPoll = options.onReadOnlyPoll || function () {
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
        // Whether the last check of an idle page found the lease held by another session.
        let idleHeldByOther = false;
        // Stops listening to other pages, see api.listen.
        let stopListeningToOtherPages = null;
        // Pending refresh of a read-only page after another page announced a change, see onChangedElsewhere.
        let changeTimer = null;
        // Bumped whenever answers of requests already issued must no longer be applied: on dispose, on pagehide,
        // when going idle and on refresh and takeover. Every queued task captures it when it is queued and is skipped, or drops its
        // result, if it changed.
        let generation = 0;

        function setState(newState) {
            if (state !== newState) {
                state = newState;
                if (newState !== STATES.IDLE) {
                    idleHeldByOther = false;
                }
                onChange();
            }
        }

        function setIdleHeldByOther(heldByOther) {
            if (idleHeldByOther !== heldByOther) {
                idleHeldByOther = heldByOther;
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
            if (!suspended) {
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
            if (state === STATES.IDLE) {
                void enqueue(checkWhileIdle);
                return;
            }
            if (Date.now() - lastActivity >= idleTimeoutMs) {
                const wasEditing = state === STATES.EDITING;
                // A read-only page knows that another session holds the lease, before its first idle check.
                idleHeldByOther = state === STATES.READ_ONLY;
                setState(STATES.IDLE);
                generation++;
                if (wasEditing) {
                    void enqueue(async () => {
                        await send('release');
                    });
                }
                schedule();
                return;
            }
            if (state === STATES.READ_ONLY) {
                void enqueue(poll);
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

        /**
         * Polls a read-only page, so it takes the lease over once the holder freed it or it expired.
         */
        async function poll(isCurrent) {
            const status = await send('acquire');
            if (!isCurrent()) {
                releaseLateGrant(status);
                return;
            }
            apply(status);
            if (status.heldByMe) {
                onStale();
            } else if (status.heldByOther) {
                onReadOnlyPoll();
            }
        }

        /**
         * Checks the lease of an idle page, so it can offer a takeover instead of resume() once another session
         * started editing.
         */
        async function checkWhileIdle(isCurrent) {
            // Unlike an acquire, a heartbeat never grants a lease, so the lease is not taken back behind the
            // user's back.
            const status = await api.request('heartbeat');
            if (!isCurrent()) {
                return;
            }
            if (status.heldByMe) {
                // The release sent when going idle did not get through, and the heartbeat just renewed the lease.
                await api.request('release');
                if (!isCurrent()) {
                    return;
                }
            }
            heartbeatMs = toMillis(status.heartbeatSeconds, DEFAULT_HEARTBEAT_MS);
            setIdleHeldByOther(status.heldByOther === true);
            schedule();
        }

        /**
         * Runs the next tick right away instead of waiting for its timer. Does nothing while a request is in
         * flight (its answer schedules the next tick) or while suspended.
         */
        function checkNow() {
            if (timer) {
                stop();
                tick();
            }
        }

        function onVisibilityChange() {
            // The timers of a hidden page may have been throttled or paused, so what is shown may be outdated.
            if (document.visibilityState === 'visible') {
                checkNow();
            }
        }

        function onReleasedElsewhere() {
            if (state === STATES.READ_ONLY || state === STATES.IDLE) {
                checkNow();
            }
        }

        function onChangedElsewhere() {
            if (state !== STATES.READ_ONLY || changeTimer) {
                return;
            }
            changeTimer = setTimeout(() => {
                changeTimer = null;
                if (state === STATES.READ_ONLY && !suspended) {
                    onReadOnlyPoll();
                }
            }, CHANGE_DEBOUNCE_MS);
        }

        function listenToOtherPages() {
            if (!stopListeningToOtherPages && typeof api.listen === 'function') {
                stopListeningToOtherPages = api.listen({released: onReleasedElsewhere, changed: onChangedElsewhere});
            }
        }

        function stopListening() {
            if (stopListeningToOtherPages) {
                stopListeningToOtherPages();
                stopListeningToOtherPages = null;
            }
            if (changeTimer) {
                clearTimeout(changeTimer);
                changeTimer = null;
            }
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
            // An open BroadcastChannel keeps some browsers from putting the page into the back-forward cache.
            stopListening();
            releaseOnUnload();
        }

        function removeListeners() {
            ACTIVITY_EVENTS.forEach(eventName => document.removeEventListener(eventName,
                onActivity,
                ACTIVITY_LISTENER_OPTIONS));
            document.removeEventListener('visibilitychange', onVisibilityChange);
            window.removeEventListener('pagehide', onPageHide);
            window.removeEventListener('pageshow', onPageShow);
            stopListening();
        }

        function onPageShow(event) {
            // Never fires after dispose (the listener is removed), so clearing `suspended` cannot revive the lease.
            if (event.persisted) {
                suspended = false;
                hidden = false;
                listenToOtherPages();
                if (state === STATES.IDLE) {
                    // Paused for inactivity before the page was hidden: the lease is only taken back via resume(),
                    // as someone else may have started editing meanwhile. Checking it goes on, though.
                    schedule();
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
                document.addEventListener('visibilitychange', onVisibilityChange);
                window.addEventListener('pagehide', onPageHide);
                window.addEventListener('pageshow', onPageShow);
                listenToOtherPages();
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
             * Tells the other pages of this browser editing the same object that this page just changed it, so
             * read-only ones show the change right away. To be called after every successful write; does nothing
             * unless this page holds the lease.
             */
            notifyChanged: function () {
                if (state === STATES.EDITING && typeof api.announceChange === 'function') {
                    api.announceChange();
                }
            },
            /**
             * Determines if another session holds the lease: always while read-only, and while idle if the last
             * check found that another session started editing meanwhile (so a takeover is to be offered instead of
             * resume()).
             */
            isHeldByOther: function () {
                return state === STATES.READ_ONLY || (state === STATES.IDLE && idleHeldByOther);
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
                // May be called from an idle page, whose inactivity must not end the lease right away.
                lastActivity = Date.now();
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
