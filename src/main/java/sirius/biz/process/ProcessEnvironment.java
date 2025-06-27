/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.biz.jobs.BasicJobFactory;
import sirius.biz.jobs.params.Parameter;
import sirius.biz.process.logs.ProcessLog;
import sirius.biz.process.output.ChartOutput;
import sirius.biz.process.output.ChartProcessOutputType;
import sirius.biz.process.output.LogsProcessOutputType;
import sirius.biz.process.output.ProcessOutput;
import sirius.biz.process.output.TableOutput;
import sirius.biz.process.output.TableProcessOutputType;
import sirius.biz.storage.util.WatchableOutputStream;
import sirius.db.mixing.types.StringMap;
import sirius.kernel.async.CombinedFuture;
import sirius.kernel.async.Future;
import sirius.kernel.async.Promise;
import sirius.kernel.async.TaskContext;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.CSVWriter;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.RateLimit;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.UncloseableOutputStream;
import sirius.kernel.commons.UnitOfWork;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.Average;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;
import sirius.kernel.health.Log;
import sirius.kernel.nls.NLS;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Provides the implementation for {@link ProcessContext}.
 */
class ProcessEnvironment implements ProcessContext {

    private final String processId;

    private final RateLimit logLimiter = RateLimit.timeInterval(10, TimeUnit.SECONDS);
    private final RateLimit timingLimiter = RateLimit.timeInterval(10, TimeUnit.SECONDS);
    private final RateLimit stateUpdate = RateLimit.timeInterval(5, TimeUnit.SECONDS);

    private CombinedFuture barrier = new CombinedFuture();
    private Map<String, Average> timings;
    private Map<String, Average> adminTimings;
    private final Map<String, Integer> limitsPerType = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> messageCountsPerType = new ConcurrentHashMap<>();

    private Boolean limitLogMessages = null;
    private final ProgressTracker progressTracker = new ProgressTracker(this);

    private final List<LogFileCollector> logFileCollectors = new CopyOnWriteArrayList<>();

    @Part
    @Nullable
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
        addTiming(counter, millis, false);
    }

    @Override
    public void addTiming(String counter, long millis, boolean adminOnly) {
        // Drops the dollar sign used in NLS.smartGet, because the counters are always translated.
        if (counter.startsWith("$")) {
            counter = counter.substring(1);
        }

        if (adminOnly) {
            getAdminTimings().computeIfAbsent(counter, ignored -> new Average()).addValue(millis);
        } else {
            getTimings().computeIfAbsent(counter, ignored -> new Average()).addValue(millis);
        }

        if (timingLimiter.check()) {
            processes.addTimings(processId, getTimings(), getAdminTimings());
        }
    }

    @Override
    public void addDebugTiming(String counter, long millis) {
        if (isDebugging()) {
            addTiming(counter, millis, false);
        }
    }

    @Override
    public void incCounter(String counter) {
        incCounter(counter, false);
    }

    @Override
    public void incCounter(String counter, boolean adminOnly) {
        addTiming(counter, -1L, adminOnly);
    }

    @Override
    public void addLogFile(@Nonnull String fileName, @Nonnull Predicate<ProcessLog> logFileFilter) {
        logFileCollectors.add(new LogFileCollector(fileName, logFileFilter));
    }

    private Map<String, Average> getTimings() {
        if (timings == null) {
            initializeTimings();
        }

        return timings;
    }

    private Map<String, Average> getAdminTimings() {
        if (adminTimings == null) {
            initializeTimings();
        }

        return adminTimings;
    }

    private synchronized void initializeTimings() {
        if (timings != null) {
            return;
        }

        timings = new ConcurrentHashMap<>();
        adminTimings = new ConcurrentHashMap<>();
        processes.fetchProcess(processId).ifPresent(process -> {
            process.getPerformanceCounters().data().keySet().forEach(key -> {
                int counter = process.getPerformanceCounters().get(key).orElse(0);
                int timing = process.getTimings().get(key).orElse(0);

                Average average = new Average();
                average.addValues(counter, (double) counter * timing);
                timings.put(key, average);
            });

            process.getAdminPerformanceCounters().data().keySet().forEach(key -> {
                int counter = process.getAdminPerformanceCounters().get(key).orElse(0);
                int timing = process.getAdminTimings().get(key).orElse(0);

                Average average = new Average();
                average.addValues(counter, (double) counter * timing);
                adminTimings.put(key, average);
            });
        });
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
        logEntryToProcess(logEntry);
        logEntryToFiles(logEntry);
    }

    private void logEntryToProcess(ProcessLog logEntry) {
        if (Strings.isFilled(logEntry.getMessageType())
            && logEntry.getMaxMessagesToLog() > 0
            && shouldLimitLogMessages()) {
            AtomicInteger messagesSoFar =
                    messageCountsPerType.computeIfAbsent(logEntry.getMessageType(), this::countMessagesForType);
            limitsPerType.putIfAbsent(logEntry.getMessageType(), logEntry.getMaxMessagesToLog());
            if (messagesSoFar.incrementAndGet() > logEntry.getMaxMessagesToLog()) {
                return;
            }
        }

        processes.log(processId, logEntry);
    }

    private void logEntryToFiles(ProcessLog logEntry) {
        logFileCollectors.forEach(collector -> collector.logEntry(logEntry));
    }

    private boolean shouldLimitLogMessages() {
        if (limitLogMessages == null) {
            limitLogMessages = getParameter(BasicJobFactory.LIMIT_LOG_MESSAGES_PARAMETER).orElse(true);
        }
        return limitLogMessages;
    }

    private AtomicInteger countMessagesForType(String messageType) {
        return new AtomicInteger((int) processes.countMessagesForType(processId, messageType));
    }

    @Override
    public void debug(ProcessLog logEntry) {
        if (isDebugging()) {
            log(logEntry);
        }
    }

    @Override
    public HandledException handle(Exception exception) {
        HandledException handledException = Exceptions.handle(Log.BACKGROUND, exception);
        log(ProcessLog.error().withHandledException(handledException));
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
    public void markRunning() {
        processes.markRunning(processId);
    }

    @Override
    public void markCompleted(int computationTimeInSeconds) {
        logFileCollectors.forEach(LogFileCollector::closeAndUpload);
        processes.reportLimitedMessages(processId, messageCountsPerType, limitsPerType);
        processes.markCompleted(processId, timings, adminTimings, computationTimeInSeconds);
    }

    /**
     * Flushes all timings for a partial execution.
     */
    protected void flushTimings() {
        if (timings != null) {
            processes.addTimings(processId, getTimings(), getAdminTimings());
        }
        processes.reportLimitedMessages(processId, messageCountsPerType, limitsPerType);
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
    public RateLimit shouldUpdateState() {
        return stateUpdate;
    }

    @Override
    public void tryUpdateState(String message) {
        if (shouldUpdateState().check()) {
            forceUpdateState(message);
        }
    }

    @Override
    public void forceUpdateState(String message) {
        processes.setStateMessage(processId, message);
    }

    @Override
    public void markErroneous() {
        processes.markErroneous(processId);
    }

    @Override
    public void cancel() {
        processes.markCanceled(processId);
    }

    @Override
    public boolean isActive() {
        return processes.fetchProcess(processId)
                        .map(process -> process.getState() == ProcessState.WAITING
                                        || process.getState() == ProcessState.RUNNING
                                        || process.getState() == ProcessState.STANDBY)
                        .orElse(false) && tasks.isRunning();
    }

    /**
     * Fetches the identifier of the user who started the process.
     *
     * @return the identifier of the user who started the process
     */
    @Nullable
    public String fetchUserId() {
        return processes.fetchProcess(processId).map(Process::getUserId).orElse(null);
    }

    /**
     * Fetches the name of the user who started the process.
     *
     * @return the name of the user who started the process
     */
    @Nullable
    public String fetchUserName() {
        return processes.fetchProcess(processId).map(Process::getUserName).orElse(null);
    }

    /**
     * Fetches the identifier of the tenant which started the process.
     *
     * @return the identifier of the tenant which started the process
     */
    @Nullable
    public String fetchTenantId() {
        return processes.fetchProcess(processId).map(Process::getTenantId).orElse(null);
    }

    /**
     * Fetches the name of the tenant which started the process.
     *
     * @return the name of the tenant which started the process
     */
    @Nullable
    public String fetchTenantName() {
        return processes.fetchProcess(processId).map(Process::getTenantName).orElse(null);
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
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
    public <V> Optional<V> getParameter(Parameter<V> parameter) {
        return parameter.get(getContext());
    }

    @Override
    public <V> V require(Parameter<V> parameter) {
        return parameter.require(getContext());
    }

    @Override
    public void addLink(ProcessLink link) {
        processes.addLink(processId, link);
    }

    @Override
    public void addUniqueLink(ProcessLink link) {
        processes.addUniqueLink(processId, link);
    }

    @Override
    public void clearLinks() {
        processes.clearLinks(processId);
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
        return new TableOutput(name, this, columns.stream().map(Tuple::getFirst).toList());
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
    @SuppressWarnings("java:S2095")
    @Explain("The stream is closed in a finally-block within the callback")
    public OutputStream addZipFile(String zipArchiveName, String filename) throws IOException {
        File zipFile = Files.createTempFile("ZipBuilder", ".zip").toFile();
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile));
        zipOutputStream.putNextEntry(new ZipEntry(filename));

        WatchableOutputStream outputStream = new WatchableOutputStream(new UncloseableOutputStream(zipOutputStream));
        outputStream.onSuccess(() -> {
            try {
                zipOutputStream.closeEntry();
                zipOutputStream.close();
                addFile(zipArchiveName, zipFile);
            } catch (Exception exception) {
                throw Exceptions.handle(Log.BACKGROUND, exception);
            } finally {
                sirius.kernel.commons.Files.delete(zipFile);
            }
        });

        return outputStream;
    }

    @Override
    public TableOutput.ColumnBuilder addTable(String name, String label) {
        return new TableOutput.ColumnBuilder(this, name, label);
    }

    @Override
    public <P> Promise<P> computeInSideTask(Producer<P> parallelTask) {
        Promise<P> promise = new Promise<>();
        performInSideTask(() -> promise.success(parallelTask.create())).onFailure(promise::fail);

        return promise;
    }

    @Override
    public Future performInSideTask(UnitOfWork parallelTask) {
        Future future = new Future();
        tasks.executor("process-sidetask").fork(() -> {
            try {
                parallelTask.execute();
                future.success();
            } catch (Exception exception) {
                future.fail(exception);
            }
        }).onFailure(future::fail);

        barrier.add(future);

        return future;
    }

    @Override
    public void awaitSideTaskCompletion() {
        Future completionFuture = barrier.asFuture();
        barrier = new CombinedFuture();

        if (!completionFuture.isCompleted()) {
            log(ProcessLog.info().withNLSKey("Process.awaitingSideTaskCompletion"));
            while (TaskContext.get().isActive()) {
                if (completionFuture.await(Duration.ofSeconds(1))) {
                    return;
                }
            }
        }
    }

    @Override
    public boolean awaitFlushedLogs() {
        return processes.awaitFlushedLogs(processId);
    }

    @Override
    public Optional<ProcessOutput> fetchOutput(String outputName) {
        return processes.fetchOutput(processId, outputName);
    }

    @Override
    public void fetchOutputEntries(@Nullable String outputName,
                                   BiConsumer<List<String>, List<String>> columnsAndLabelsConsumer,
                                   BiPredicate<List<String>, List<String>> columnsAndValues) {
        processes.fetchOutputEntries(processId, outputName, columnsAndLabelsConsumer, columnsAndValues);
    }

    private class LogFileCollector {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        private String logFileName;
        private File logFile;
        private BufferedWriter logFileWriter;
        private CSVWriter logCsvWriter;
        private Predicate<ProcessLog> logFileFilter;
        private boolean lineLogged = false;

        protected LogFileCollector(String fileName, Predicate<ProcessLog> logFileFilter) {
            try {
                this.logFileName = fileName;
                this.logFile = File.createTempFile("ProcessLogFile", ".csv");
                this.logFileWriter = new BufferedWriter(new FileWriter(logFile));
                this.logCsvWriter = new CSVWriter(logFileWriter);
                this.logFileFilter = logFileFilter;
                logCsvWriter.writeArray("timestamp", "level", "messageType", "message");
            } catch (IOException exception) {
                handle(exception);
            }
        }

        protected void logEntry(ProcessLog logEntry) {
            if (logFileFilter.test(logEntry)) {
                try {
                    lineLogged = true;
                    logCsvWriter.writeArray(LocalDateTime.now().format(formatter),
                                            logEntry.getType().name(),
                                            NLS.smartGet(logEntry.getMessageType()),
                                            logEntry.getMessage());
                } catch (IOException exception) {
                    // Failure to create or write to the log file is not critical, so we ignore it.
                    Exceptions.ignore(exception);
                }
            }
        }

        protected void closeAndUpload() {
            try {
                logCsvWriter.close();
                logFileWriter.close();
                if (lineLogged) {
                    addFile(logFileName, logFile);
                }
                sirius.kernel.commons.Files.delete(logFile);
            } catch (IOException exception) {
                handle(exception);
            }
        }
    }
}
