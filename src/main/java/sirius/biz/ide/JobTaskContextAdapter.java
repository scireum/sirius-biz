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

    protected JobTaskContextAdapter(Scripting scripting, String jobNumber) {
        this.scripting = scripting;
        this.jobNumber = jobNumber;
        this.logLimiter = RateLimit.timeInterval(10, TimeUnit.SECONDS);
    }

    @Override
    public void log(String message) {
        scripting.logInTranscript(jobNumber, message);
    }

    @Override
    public void trace(String message) {
        scripting.logInTranscript(jobNumber, message);
    }

    @Override
    public void setState(String message) {
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

    }

    @Override
    public void addTiming(String counter, long millis, boolean adminOnly) {

    }

    @Override
    public void markErroneous() {

    }

    @Override
    public void cancel() {

    }

    @Override
    public boolean isActive() {
        return true;
    }
}
