/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.locks;

import redis.clients.jedis.AbstractTransaction;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;
import sirius.db.redis.Redis;
import sirius.kernel.async.CompletionHandler;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Wait;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.controller.Controller;
import sirius.web.http.WebContext;
import sirius.web.services.JSONStructuredOutput;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Grants a single editor session (usually one browser tab) the exclusive right to modify an object, e.g. an entity
 * edited in an interactive editor.
 * <p>
 * Unlike {@link Locks}, which guard code against concurrent execution, an edit lease is owned by a client-side editor
 * session which spans many requests. The lease is an expiring Redis key per edited object holding the editor session,
 * so an abandoned editor frees it automatically once it stops renewing it. Acquiring uses {@code SET NX}; renewing and
 * releasing use WATCH/MULTI so they never touch a lease that changed hands in the meantime.
 * <p>
 * A leased object is addressed by a <tt>scope</tt> (e.g. the kind of editor) and the <tt>id</tt> of the edited object,
 * so that different editors working on the same object can either share a scope or lock independently. The scope must
 * not contain a colon, so that every pair of scope and id maps to its own Redis key.
 * <p>
 * The client-side counterpart is <tt>sirius.editLease</tt> (see <tt>/assets/scripts/edit-lease.js</tt>), which talks
 * to an application route calling {@link #handleAction(WebContext, JSONStructuredOutput, String, String)}. Every
 * route modifying the object has to call {@link #assertHeldBy(WebContext, String, String)} before applying the change.
 * <p>
 * Checking the lease and applying the write are not atomic, so a write could otherwise race a takeover. Therefore,
 * every write which passed the check is registered as in-flight until it completes, and whenever the lease changes
 * hands, the new holder is only granted the lease once all in-flight writes of the previous holder are done (but at
 * most {@code edit-leases.maxWriteDuration}). Once a takeover or acquire returns, no write of a previous holder which
 * completes within {@code edit-leases.maxWriteDuration} can be applied anymore, so the data the new holder loads
 * afterwards is final. A write running longer is no longer waited for and may still be applied after the handover, so
 * the max write duration has to cover the slowest write route.
 */
@Register(classes = EditLeases.class)
public class EditLeases {

    /**
     * Error code reported to the editor when a write is rejected because its session does not hold the lease.
     */
    public static final String ERROR_CODE_EDIT_LOCKED = "editLocked";

    /**
     * Request parameter carrying the editor session which may hold the lease.
     */
    public static final String PARAM_EDITOR_SESSION = "editorSession";

    /**
     * Request parameter selecting the action performed by {@link #handleAction}.
     */
    public static final String PARAM_ACTION = "action";

    private static final String ACTION_ACQUIRE = "acquire";
    private static final String ACTION_HEARTBEAT = "heartbeat";
    private static final String ACTION_TAKEOVER = "takeover";
    private static final String ACTION_RELEASE = "release";

    private static final String KEY_PREFIX = "edit-lease:";
    private static final String WRITES_KEY_PREFIX = "edit-lease-writes:";
    private static final String SCOPE_SEPARATOR = ":";
    private static final int PENDING_WRITES_POLL_MILLIS = 50;

    @Part
    private Redis redis;

    @ConfigValue("edit-leases.leaseTimeout")
    private Duration leaseTimeout;

    @ConfigValue("edit-leases.heartbeatInterval")
    private Duration heartbeatInterval;

    @ConfigValue("edit-leases.maxWriteDuration")
    private Duration maxWriteDuration;

    @Part
    private Tasks tasks;

    /**
     * Describes the lease as seen by one session.
     *
     * @param heldByMe    whether the asking session holds the lease
     * @param heldByOther whether another session holds the lease
     */
    public record EditLeaseStatus(boolean heldByMe, boolean heldByOther) {
    }

    private static final EditLeaseStatus HELD = new EditLeaseStatus(true, false);
    private static final EditLeaseStatus HELD_BY_OTHER = new EditLeaseStatus(false, true);

    /**
     * The status of a lease nobody holds.
     */
    public static final EditLeaseStatus FREE = new EditLeaseStatus(false, false);

    /**
     * Grants the lease if it is free or already held by the given session.
     *
     * @param scope   the scope of the lease, e.g. the kind of editor
     * @param id      the id of the object to edit
     * @param session the editor session asking for the lease
     * @return whether the session now holds the lease, and otherwise whether another session does
     */
    public EditLeaseStatus acquire(String scope, String id, @Nullable String session) {
        String key = key(scope, id);
        if (Strings.isEmpty(session)) {
            return statusFor(readHolder(key), session);
        }
        if (setIfAbsent(key, session)) {
            return grantAfterPendingWrites(key, session);
        }
        // A plain read first, so a read-only editor polling a lease held elsewhere needs no transaction.
        EditLeaseStatus status = statusFor(readHolder(key), session);
        if (status.heldByOther()) {
            return status;
        }
        status = heartbeat(scope, id, session);
        if (!FREE.equals(status)) {
            return status;
        }
        // The lease expired between SET NX and the heartbeat - it is free now, so try once more.
        if (setIfAbsent(key, session)) {
            return grantAfterPendingWrites(key, session);
        }
        // Another session acquired the lease right before the retry.
        return statusFor(readHolder(key), session);
    }

    /**
     * Renews the lease, but only if the given session still holds it.
     *
     * @param scope   the scope of the lease
     * @param id      the id of the edited object
     * @param session the editor session renewing its lease
     * @return whether the session still holds the lease, and otherwise whether another session does
     */
    public EditLeaseStatus heartbeat(String scope, String id, @Nullable String session) {
        String key = key(scope, id);
        return updateIfHeldBy(key, session, transaction -> transaction.expire(key, leaseTimeout.getSeconds()));
    }

    /**
     * Gives up the lease, but only if the given session holds it.
     *
     * @param scope   the scope of the lease
     * @param id      the id of the edited object
     * @param session the editor session releasing its lease
     * @return {@link #FREE} if the lease was released, otherwise the status of the lease, which the session did not
     * hold (e.g. as another session holds it)
     */
    public EditLeaseStatus release(String scope, String id, @Nullable String session) {
        String key = key(scope, id);
        EditLeaseStatus status = updateIfHeldBy(key, session, transaction -> transaction.del(key));
        return status.heldByMe() ? FREE : status;
    }

    /**
     * Unconditionally moves the lease to the given session, so the previous holder can no longer save.
     * <p>
     * Returns only once all writes of the previous holder which are still in flight are done.
     *
     * @param scope   the scope of the lease
     * @param id      the id of the object to edit
     * @param session the editor session taking over
     * @return whether the session holds the lease once the pending writes are done - which is the case unless the
     * session is blank or yet another session took the lease over in the meantime
     */
    public EditLeaseStatus takeover(String scope, String id, @Nullable String session) {
        String key = key(scope, id);
        if (Strings.isEmpty(session)) {
            return statusFor(readHolder(key), session);
        }
        redis.exec(() -> "Take over edit lease",
                   jedis -> jedis.set(key, session, SetParams.setParams().ex(leaseTimeout.getSeconds())));
        return grantAfterPendingWrites(key, session);
    }

    /**
     * Applies a write, but only if the given session holds the lease.
     * <p>
     * The write is registered as in-flight while it runs, so that a takeover waits until it is done.
     *
     * @param scope   the scope of the lease
     * @param id      the id of the object to modify
     * @param session the editor session sending the write
     * @param write   the write to apply while holding the lease
     * @throws sirius.kernel.health.HandledException tagged with {@link #ERROR_CODE_EDIT_LOCKED} if the session
     *                                               does not hold the lease, in which case the write is not applied
     */
    public void runWhileHeldBy(String scope, String id, @Nullable String session, Runnable write) {
        String key = key(scope, id);
        String writeId = beginWrite(key, session);
        try {
            write.run();
        } finally {
            endWrite(key, writeId);
        }
    }

    /**
     * Ensures the editor session of the given request holds the lease before a write is applied.
     * <p>
     * The request is registered as an in-flight write until it is completely handled, so that a takeover waits until
     * the write of this request is done.
     *
     * @param webContext the current request, carrying {@link #PARAM_EDITOR_SESSION}
     * @param scope      the scope of the lease
     * @param id         the id of the object about to be modified
     * @throws sirius.kernel.health.HandledException tagged with {@link #ERROR_CODE_EDIT_LOCKED} if the session
     *                                               does not hold the lease
     */
    public void assertHeldBy(WebContext webContext, String scope, String id) {
        String key = key(scope, id);
        String writeId = beginWrite(key, webContext.get(PARAM_EDITOR_SESSION).asString());
        webContext.getCompletionPromise().onComplete(new CompletionHandler<>() {
            @Override
            public void onSuccess(@Nullable Integer value) {
                endWriteAsync(key, writeId);
            }

            @Override
            public void onFailure(@Nonnull Throwable throwable) {
                endWriteAsync(key, writeId);
            }
        });
    }

    /**
     * Performs one lease action (acquire, heartbeat, release or takeover) for the request's editor session and
     * writes the resulting status.
     * <p>
     * This implements the server side of <tt>sirius.editLease</tt>. The calling route is responsible for checking
     * that the current user may edit the object at all.
     *
     * @param webContext the current request, carrying {@link #PARAM_ACTION} and {@link #PARAM_EDITOR_SESSION}
     * @param output     the JSON output to write {@code heldByMe}, {@code heldByOther} and {@code heartbeatSeconds} to
     * @param scope      the scope of the lease
     * @param id         the id of the edited object
     */
    public void handleAction(WebContext webContext, JSONStructuredOutput output, String scope, String id) {
        String session = webContext.get(PARAM_EDITOR_SESSION).asString();
        String action = webContext.get(PARAM_ACTION).asString();

        EditLeaseStatus status = switch (action) {
            case ACTION_ACQUIRE -> acquire(scope, id, session);
            case ACTION_HEARTBEAT -> heartbeat(scope, id, session);
            case ACTION_TAKEOVER -> takeover(scope, id, session);
            case ACTION_RELEASE -> release(scope, id, session);
            default -> throw Exceptions.createHandled()
                                       .withSystemErrorMessage("Unknown edit lease action: '%s'", action)
                                       .handle();
        };

        output.property("heldByMe", status.heldByMe());
        output.property("heldByOther", status.heldByOther());
        output.property("heartbeatSeconds", getHeartbeatSeconds());
    }

    /**
     * Returns the interval in which a holding editor should renew its lease.
     *
     * @return the heartbeat interval in seconds
     */
    public long getHeartbeatSeconds() {
        return heartbeatInterval.getSeconds();
    }

    /**
     * Registers a write as in-flight, but only if the given session holds the lease.
     * <p>
     * Checking the holder and registering the write happen in one WATCH/MULTI transaction, so a write either counts
     * as in-flight before the lease changes hands (and a takeover waits for it), or it is rejected.
     * <p>
     * Each write is a member of a sorted set scored by its deadline, so a write which is never completed (e.g. as its
     * node crashed) or takes longer than the max write duration only counts as in-flight until its own deadline, and
     * completing it can never affect other writes.
     *
     * @return the id of the registered write, to be passed to {@link #endWrite(String, String)}
     */
    private String beginWrite(String key, @Nullable String session) {
        String writeId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        boolean registered = redis.query(() -> "Register edit lease write", jedis -> {
            try (AbstractTransaction transaction = jedis.transaction(false)) {
                transaction.watch(key);
                if (!statusFor(readHolder(jedis, key), session).heldByMe()) {
                    transaction.unwatch();
                    return false;
                }
                transaction.multi();
                transaction.zremrangeByScore(writesKey(key), "-inf", "(" + now);
                transaction.zadd(writesKey(key), now + maxWriteDuration.toMillis(), writeId);
                // The set outlives the latest deadline it contains, so no in-flight write is lost with it.
                transaction.pexpire(writesKey(key), maxWriteDuration.toMillis());
                // Discarded if the lease changed hands since WATCH - the write is then rejected like any other.
                return transaction.exec() != null;
            }
        });
        if (!registered) {
            throw Exceptions.createHandled()
                            .withNLSKey("EditLeases.error.editLocked")
                            .hint(Controller.ERROR_CODE, ERROR_CODE_EDIT_LOCKED)
                            .handle();
        }
        return writeId;
    }

    private void endWrite(String key, String writeId) {
        redis.exec(() -> "Complete edit lease write", jedis -> jedis.zrem(writesKey(key), writeId));
    }

    private void endWriteAsync(String key, String writeId) {
        // Completion handlers run in the IO thread pool, which must not be blocked by Redis calls.
        tasks.defaultExecutor().start(() -> endWrite(key, writeId));
    }

    /**
     * Waits until all writes registered by a previous holder are done (but at most for the max write duration) and
     * then reports the lease as seen by the given session, which may have lost it to another takeover meanwhile.
     */
    private EditLeaseStatus grantAfterPendingWrites(String key, String session) {
        long deadline = System.currentTimeMillis() + maxWriteDuration.toMillis();
        while (hasPendingWrites(key) && System.currentTimeMillis() < deadline) {
            Wait.millis(PENDING_WRITES_POLL_MILLIS);
        }
        return statusFor(readHolder(key), session);
    }

    private boolean hasPendingWrites(String key) {
        String now = String.valueOf(System.currentTimeMillis());
        long pending = redis.query(() -> "Read edit lease writes",
                                   jedis -> jedis.zcount(writesKey(key), now, "+inf"));
        return pending > 0;
    }

    private boolean setIfAbsent(String key, String session) {
        String response = redis.query(() -> "Acquire edit lease",
                                      jedis -> jedis.set(key,
                                                         session,
                                                         SetParams.setParams().nx().ex(leaseTimeout.getSeconds())));
        return Strings.areEqual("OK", response);
    }

    private EditLeaseStatus updateIfHeldBy(String key,
                                           @Nullable String session,
                                           Function<AbstractTransaction, ?> update) {
        return redis.query(() -> "Update edit lease", jedis -> {
            try (AbstractTransaction transaction = jedis.transaction(false)) {
                transaction.watch(key);
                // Read through the pool: WATCH already guards the key, so any change from now on aborts EXEC.
                EditLeaseStatus status = statusFor(readHolder(jedis, key), session);
                if (!status.heldByMe()) {
                    transaction.unwatch();
                    return status;
                }
                transaction.multi();
                update.apply(transaction);
                List<Object> result = transaction.exec();
                if (result == null) {
                    // The lease changed hands between WATCH and EXEC - report the new state.
                    return statusFor(readHolder(jedis, key), session);
                }
                return HELD;
            }
        });
    }

    private EditLeaseStatus statusFor(@Nullable String holder, @Nullable String session) {
        if (Strings.isEmpty(holder)) {
            return FREE;
        }
        if (Strings.isFilled(session) && Strings.areEqual(holder, session)) {
            return HELD;
        }
        return HELD_BY_OTHER;
    }

    @Nullable
    private String readHolder(String key) {
        return redis.query(() -> "Read edit lease", jedis -> readHolder(jedis, key));
    }

    @Nullable
    private String readHolder(UnifiedJedis jedis, String key) {
        return jedis.get(key);
    }

    private String key(String scope, String id) {
        // As the scope is the first segment and contains no separator, the key stays unique for ids containing one.
        if (Strings.isEmpty(scope) || scope.contains(SCOPE_SEPARATOR)) {
            throw new IllegalArgumentException(Strings.apply("Invalid edit lease scope: '%s'", scope));
        }
        return KEY_PREFIX + scope + SCOPE_SEPARATOR + id;
    }

    /**
     * Derives the key of the in-flight writes, which lives in its own namespace so it can never collide with a lease.
     */
    private String writesKey(String key) {
        return WRITES_KEY_PREFIX + key.substring(KEY_PREFIX.length());
    }
}
