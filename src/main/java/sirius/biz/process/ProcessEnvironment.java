/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.output.ChartOutput;
import sirius.biz.process.output.ChartProcessOutputType;
import sirius.biz.process.output.LogsProcessOutputType;
import sirius.biz.process.output.ProcessOutput;
import sirius.biz.process.output.TableOutput;
import sirius.biz.process.output.TableProcessOutputType;
import sirius.db.mixing.types.StringMap;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.RateLimit;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Average;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Provides the implementation for {@link ProcessContext}.
 */
class ProcessEnvironment implements ProcessContext {

    private String processId;

    private RateLimit logLimiter = RateLimit.timeInterval(10, TimeUnit.SECONDS);
    private RateLimit timingLimiter = RateLimit.timeInterval(10, TimeUnit.SECONDS);
    private Map<String, Average> timings;

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
    public void smartLogLimited(Supplier<Object> messageSupplier) {
        if (logLimiter.check()) {
            log(NLS.toUserString(messageSupplier.get()));
        }
    }

    @Override
    public void addTiming(String counter, long millis) {
        getTimings().computeIfAbsent(counter, ignored -> new Average()).addValue(millis);

        if (timingLimiter.check()) {
            processes.addTimings(processId, getTimings());
        }
    }

    @Override
    public void addDebugTiming(String counter, long millis) {
        if (isDebugging()) {
            addTiming(counter, millis);
        }
    }

    protected Map<String, Average> getTimings() {
        if (timings == null) {
            timings = new HashMap<>();
            loadPreviousTimings();
        }

        return Collections.unmodifiableMap(timings);
    }

    private void loadPreviousTimings() {
        processes.fetchProcess(processId)
                 .ifPresent(process -> process.getPerformanceCounters().data().keySet().forEach(key -> {
                     int counter = process.getPerformanceCounters().get(key).orElse(0);
                     int timing = process.getTimings().get(key).orElse(0);

                     Average average = new Average();
                     average.addValues(counter, (double) counter * timing);
                     timings.put(key, average);
                 }));
    }

    @Override
    public String getProcessId() {
        return processId;
    }

    @Override
    public String getTitle() {
        return processes.fetchProcess(processId).map(Process::getTitle).orElse(null);
    }

    @Override
    public void updateTitle(String newTitle) {
        processes.updateTitle(processId, newTitle);
    }

    @Override
    public void log(ProcessLog logEntry) {
        processes.log(processId, logEntry);
    }

    @Override
    public void debug(ProcessLog logEntry) {
        if (isDebugging()) {
            log(logEntry);
        }
    }

    @Override
    public HandledException handle(Exception e) {
        HandledException handledException = Exceptions.handle(Log.BACKGROUND, e);
        log(ProcessLog.error().withMessage(handledException.getMessage()));
        return handledException;
    }

    @Override
    public boolean isDebugging() {
        return processes.fetchProcess(processId).map(Process::isDebugging).orElse(true);
    }

    @Override
    public boolean isErroneous() {
        return processes.fetchProcess(processId).map(Process::isErrorneous).orElse(true);
    }

    @Override
    public void markCompleted() {
        processes.markCompleted(processId, timings);
    }

    /**
     * Flushes all timings for a partial execution.
     */
    protected void flushTimings() {
        if (timings != null) {
            processes.addTimings(processId, timings);
        }
    }

    @Override
    public void log(String message) {
        log(ProcessLog.info().withMessage(message));
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
        return processes.fetchProcess(processId)
                        .map(proc -> proc.getState() == ProcessState.RUNNING || proc.getState() == ProcessState.STANDBY)
                        .orElse(false) && tasks.isRunning();
    }

    @Nullable
    public String getUserId() {
        return processes.fetchProcess(processId).map(Process::getUserId).orElse(null);
    }

    @Nullable
    public String getTenantId() {
        return processes.fetchProcess(processId).map(Process::getTenantId).orElse(null);
    }

    @Override
    public void setCurrentStateMessage(String state) {
        processes.setStateMessage(processId, state);
    }

    @Override
    public Map<String, String> getContext() {
        return processes.fetchProcess(processId)
                        .map(Process::getContext)
                        .map(StringMap::data)
                        .orElse(Collections.emptyMap());
    }

    @Override
    public Value get(String key) {
        return Value.of(getContext().get(key));
    }

    @Override
    public <V, P extends Parameter<V, P>> Optional<V> getParameter(Parameter<V, P> parameter) {
        return parameter.get(getContext());
    }

    @Override
    public <V, P extends Parameter<V, P>> V require(Parameter<V, P> parameter) {
        return parameter.require(getContext());
    }

    @Override
    public void addLink(ProcessLink link) {
        processes.addLink(processId, link);
    }

    @Override
    public void addReference(String reference) {
        processes.addReference(processId, reference);
    }

    @Override
    public void addOutput(ProcessOutput output) {
        processes.addOutput(processId, output);
    }

    @Override
    public void addLogOutput(String name, String label) {
        addOutput(new ProcessOutput().withType(LogsProcessOutputType.TYPE).withName(name).withLabel(label));
    }

    @Override
    public ChartOutput addCharts(String name, String label) {
        addOutput(new ProcessOutput().withType(ChartProcessOutputType.TYPE).withName(name).withLabel(label));
        return new ChartOutput(name, this);
    }

    @Override
    public TableOutput addTable(String name, String label, List<Tuple<String, String>> columns) {
        ProcessOutput output =
                new ProcessOutput().withType(TableProcessOutputType.TYPE).withName(name).withLabel(label);

        // Store the metadata (column names and labels) in the context of the output...
        output.getContext()
              .modify()
              .put(TableProcessOutputType.CONTEXT_KEY_COLUMNS,
                   columns.stream().map(Tuple::getFirst).collect(Collectors.joining("|")));
        columns.forEach(column -> output.getContext().modify().put(column.getFirst(), column.getSecond()));

        addOutput(output);
        return new TableOutput(name, this, columns.stream().map(Tuple::getFirst).collect(Collectors.toList()));
    }

    @Override
    public void addFile(String filename, File data) {
        processes.addFile(processId, filename, data);
    }

    @Override
    public OutputStream addFile(String filename) throws IOException {
        return processes.addFile(processId, filename);
    }

    @Override
    public TableOutputBuilder addTable(String name, String label) {
        return new TableOutputBuilder(name, label);
    }

    public class TableOutputBuilder {
        private String name;
        private String label;
        private List<Tuple<String, String>> columns;

        private TableOutputBuilder(String name, String label) {
            this.name = name;
            this.label = label;
            this.columns = new ArrayList<>();
        }

        /**
         * Adds a column to the table.
         *
         * @param name  the name of the column
         * @param label the label of the column to be displayed
         * @return the builder itself for fluent method calls
         */
        public TableOutputBuilder withColumn(String name, String label) {
            columns.add(Tuple.create(name, label));
            return this;
        }

        /**
         * Builds the final TableOutput.
         *
         * @return the {@link TableOutput} with all columns
         */
        public TableOutput build() {
            return addTable(name, label, Collections.unmodifiableList(columns));
        }
    }
}
