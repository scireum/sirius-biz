/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.kernel.async.Tasks;
import sirius.kernel.commons.RateLimit;
import sirius.kernel.di.std.Part;
import sirius.kernel.nls.NLS;

import java.util.concurrent.TimeUnit;

class ProcessEnvironment implements ProcessContext {

    private String processId;

    private RateLimit logLimiter = RateLimit.timeInterval(10, TimeUnit.SECONDS);

    @Part
    private static Processes processes;

    @Part
    private static Tasks tasks;

    protected ProcessEnvironment(String processId) {
        this.processId = processId;
    }

    @Override
    public void logLimited(Object message) {
        if (logLimiter.check()) {
            log(NLS.toUserString(message));
        }
    }

    @Override
    public void addTiming(String counter, long millis) {

    }

    @Override
    public void warn(Object message) {

    }

    @Override
    public void error(Object message) {

    }

    @Override
    public void handle(Exception e) {

    }

    @Override
    public boolean isErroneous() {
        return false;
    }

    @Override
    public void markRunning() {
        processes.markStarted(processId);
    }

    @Override
    public void markCompleted() {

    }

    @Override
    public void log(String message) {

    }

    @Override
    public void trace(String s) {
        // ignored
    }

    @Override
    public void setState(String state) {
        processes.setStateMessage(processId, state);
    }

    @Override
    public void markErroneous() {

    }

    @Override
    public void cancel() {
        processes.markCanceled(processId);
    }

    @Override
    public boolean isActive() {
        return processes.fetchProcess(processId).map(proc -> proc.getState() == ProcessState.RUNNING).orElse(false)
               && tasks.isRunning();
    }
}
