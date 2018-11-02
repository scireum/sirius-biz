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
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Average;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class ProcessEnvironment implements ProcessContext {

    private String processId;

    private RateLimit logLimiter = RateLimit.timeInterval(10, TimeUnit.SECONDS);
    private RateLimit timingLimiter = RateLimit.timeInterval(10, TimeUnit.SECONDS);
    private Map<String, Average> timings = new HashMap<>();

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
        timings.computeIfAbsent(counter, ignored -> new Average()).addValue(millis);

        if (timingLimiter.check()) {
            processes.addTimings(processId, getTimingsAsStrings());
        }
    }

    private Map<String, String> getTimingsAsStrings() {
        return timings.entrySet()
                      .stream()
                      .map(e -> Tuple.create(e.getKey(),
                                             Strings.apply("%1.0f ms (%s)",
                                                           e.getValue().getAvg(),
                                                           e.getValue().getCount())))
                      .collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
    }

    @Override
    public void log(ProcessLog logEntry) {
        processes.log(processId, logEntry);
    }

    @Override
    public void warn(Object message) {
        log(ProcessLog.warn(NLS.toUserString(message)));
    }

    @Override
    public void error(Object message) {
        log(ProcessLog.error(NLS.toUserString(message)));
    }

    @Override
    public void handle(Exception e) {
        log(ProcessLog.error(Exceptions.handle(Log.BACKGROUND, e).getMessage()));
    }

    @Override
    public boolean isErroneous() {
        return processes.fetchProcess(processId).map(Process::isErrorneous).orElse(true);
    }

    @Override
    public void markCompleted() {
        processes.markCompleted(processId, getTimingsAsStrings());
    }

    @Override
    public void log(String message) {
        log(ProcessLog.info(message));
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
        processes.markErrorneous(processId);
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

    @Nullable
    public String getUserId() {
        return processes.fetchProcess(processId).map(Process::getUserId).orElse(null);
    }

    @Override
    public void setCurrentStateMessage(String state) {
        processes.setStateMessage(processId, state);
    }
}
