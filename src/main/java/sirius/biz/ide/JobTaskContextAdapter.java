/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.ide;

import sirius.kernel.async.TaskContextAdapter;
import sirius.kernel.commons.RateLimit;
import sirius.kernel.nls.NLS;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Provides a special context adapter which redirects all logging to the
 * {@link Scripting#logInTranscript(String, String)} method.
 */
class JobTaskContextAdapter implements TaskContextAdapter {

    private final Scripting scripting;
    private final String jobNumber;
    private final RateLimit logLimiter;
    private final RateLimit stateLimiter;

    protected JobTaskContextAdapter(Scripting scripting, String jobNumber) {
        this.scripting = scripting;
        this.jobNumber = jobNumber;
        this.logLimiter = RateLimit.timeInterval(10, TimeUnit.SECONDS);
        this.stateLimiter = RateLimit.timeInterval(10, TimeUnit.SECONDS);
    }

    @Override
    public void log(String message) {
        scripting.logInTranscript(jobNumber, message);
    }

    @Override
    public void trace(String message) {
        scripting.logInTranscript(jobNumber, message);
    }

    /**
     * Invoked if {@link sirius.kernel.async.TaskContext#setState(String, Object...)} is called in the attached
     * context.
     *
     * @param message the message to set as state
     * @deprecated Use either {@link #forceUpdateState(String)} or {@link #tryUpdateState(String)}
     */
    @Deprecated(since = "2021/07/01")
    @Override
    public void setState(String message) {
        // unsupported by this adapter.
    }

    @Override
    public RateLimit shouldUpdateState() {
        return stateLimiter;
    }

    @Override
    public void tryUpdateState(String message) {
        // unsupported by this adapter.
    }

    @Override
    public void forceUpdateState(String message) {
        // unsupported by this adapter.
    }

    @Override
    public void logLimited(Object message) {
        if (logLimiter.check()) {
            scripting.logInTranscript(jobNumber, NLS.toUserString(message));
        }
    }

    @Override
    public void smartLogLimited(Supplier<Object> messageSupplier) {
        if (logLimiter.check()) {
            scripting.logInTranscript(jobNumber, NLS.toUserString(messageSupplier.get()));
        }
    }

    @Override
    public void addTiming(String counter, long millis) {
        // unsupported by this adapter.
    }

    @Override
    public void addTiming(String counter, long millis, boolean adminOnly) {
        // unsupported by this adapter.
    }

    @Override
    public void markErroneous() {
        // unsupported by this adapter.
    }

    @Override
    public void cancel() {
        // unsupported by this adapter.
    }

    @Override
    public boolean isActive() {
        return true;
    }
}
