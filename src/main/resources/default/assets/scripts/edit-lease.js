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
        const editorSession = session || crypto.randomUUID();

        function body(action) {
            return new URLSearchParams({...(params || {}), action: action, editorSession: editorSession});
        }

        return {
            session: editorSession,
            request: function (action) {
                return fetch(url, {method: 'POST', body: body(action)}).then(response => {
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
     * Creates the lease client for one edited object.
     *
     * @param api     the server access: <tt>request(action)</tt> resolving to <tt>{heldByMe, heldByOther,
     *                heartbeatSeconds}</tt> and <tt>releaseOnUnload()</tt> as a best-effort release while the page
     *                is being unloaded (see httpApi)
     * @param options <tt>onChange()</tt>: invoked whenever getState() or isRechecking() changed;
     *                <tt>onStale()</tt>: invoked whenever locally shown data may be outdated: the lease was lost,
     *                taken over, resumed or re-checked; <tt>idleTimeoutMs</tt>: time without pointer, keyboard,
     *                wheel or scroll activity after which an editing page releases the lease
     */
    function create(api, options) {
        const onChange = options.onChange || function () {
        };
        const onStale = options.onStale || function () {
        };
        const idleTimeoutMs = options.idleTimeoutMs || DEFAULT_IDLE_TIMEOUT_MS;

        let state = STATES.ACQUIRING;
        // Set synchronously by refresh(), i.e. before the rejected request's caller sees the error.
        let pendingRefresh = null;
        let heartbeatMs = DEFAULT_HEARTBEAT_MS;
        let timer = null;
        let lastActivity = Date.now();
        // Set on pagehide and dispose: no further heartbeats are scheduled until a back-forward cache restore.
        let suspended = false;
        let queue = Promise.resolve();
        // Bumped whenever answers of requests already issued must no longer be applied: on dispose, on pagehide
        // and when going idle. Every queued task captures it when it is queued and is skipped, or drops its
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

        function enqueue(task) {
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
                        schedule();
                    }
                }
            });
            queue = run;
            return run;
        }

        function apply(status) {
            heartbeatMs = status.heartbeatSeconds * 1000;
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
                        await api.request('release');
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
        function requestLease(action, stale) {
            return enqueue(async isCurrent => {
                const status = await api.request(action);
                if (!isCurrent()) {
                    return;
                }
                apply(status);
                if (stale(status)) {
                    onStale();
                }
            });
        }

        async function heartbeat(isCurrent) {
            const status = await api.request('heartbeat');
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
            const next = await api.request('acquire');
            if (!isCurrent()) {
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

        function onPageHide() {
            suspend();
            if (state === STATES.EDITING) {
                api.releaseOnUnload();
            }
        }

        function onPageShow(event) {
            // Never fires after dispose (the listener is removed), so clearing `suspended` cannot revive the lease.
            if (event.persisted) {
                suspended = false;
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
                ACTIVITY_EVENTS.forEach(eventName => document.addEventListener(eventName,
                    onActivity,
                    ACTIVITY_LISTENER_OPTIONS));
                window.addEventListener('pagehide', onPageHide);
                window.addEventListener('pageshow', onPageShow);
                return requestLease('acquire', () => false);
            },
            /**
             * Stops all heartbeats and removes all listeners. The lease itself is left to expire or to the
             * pagehide release.
             */
            dispose: function () {
                suspend();
                ACTIVITY_EVENTS.forEach(eventName => document.removeEventListener(eventName,
                    onActivity,
                    ACTIVITY_LISTENER_OPTIONS));
                window.removeEventListener('pagehide', onPageHide);
                window.removeEventListener('pageshow', onPageShow);
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
             */
            takeover: function () {
                return requestLease('takeover', () => true);
            },
            /**
             * Re-acquires the lease after an inactivity pause.
             */
            resume: function () {
                lastActivity = Date.now();
                return requestLease('acquire', () => true);
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
