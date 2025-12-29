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
import sirius.biz.process.output.ProcessOutput;
import sirius.biz.process.output.TableOutput;
import sirius.kernel.async.Future;
import sirius.kernel.async.Promise;
import sirius.kernel.async.TaskContextAdapter;
import sirius.kernel.commons.Producer;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.UnitOfWork;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Declares the client API of a process.
 * <p>
 * {@link Processes} will instantiate and provide an instance of this to caller while executing a process. Also, it will
 * install this using {@link sirius.kernel.async.TaskContext#setAdapter(TaskContextAdapter)} so that calls to
 * {@link sirius.kernel.async.TaskContext} will be delegated to the "processes" framework.
 */
@ThreadSafe
public interface ProcessContext extends TaskContextAdapter {

    /**
     * Returns the id of the process context.
     *
     * @return the id of the process
     */
    String getProcessId();

    /**
     * Returns the title of the underlying process.
     *
     * @return the title of the process
     */
    String getTitle();

    /**
     * Provides a new title for the process.
     *
     * @param newTitle the new title to use
     */
    void updateTitle(String newTitle);

    /**
     * Increments the given performance counter by one and supplies a loop duration in milliseconds if the current
     * process has debugging enabled.
     * <p>
     * The average value will be computed for the given counter and gives the user a rough estimate what the current
     * task is doing.
     *
     * @param counter the counter to increment
     * @param millis  the current duration for the block being counted
     * @see Process#DEBUGGING
     * @see Processes#changeDebugging(String, boolean)
     */
    void addDebugTiming(String counter, long millis);

    /**
     * Increments the given performance counter by one.
     *
     * @param counter the counter to increment
     */
    void incCounter(String counter);

    /**
     * Increments the given performance counter by one.
     *
     * @param counter   the counter to increment
     * @param adminOnly whether to show the timing only to administrators instead of all users
     */
    void incCounter(String counter, boolean adminOnly);

    /**
     * Handles the given exception.
     * <p>
     * This will invoke {@link Exceptions#handle()} and log the result.
     *
     * @param exception the exception to handle
     * @return the handled exception for further processing
     */
    HandledException handle(Exception exception);

    /**
     * Logs the given log entry.
     *
     * @param logEntry the entry to log
     */
    void log(ProcessLog logEntry);

    /**
     * Logs the given log entry if the current process has debugging enabled.
     *
     * @param logEntry the entry to log
     * @see Process#DEBUGGING
     * @see Processes#changeDebugging(String, boolean)
     */
    void debug(ProcessLog logEntry);

    /**
     * Determines if the current process has debugging enabled.
     * <p>
     * Debugging can be enabled via the backend UI. This is mainly useful for long running processes or
     * {@link Processes#executeInStandbyProcess(String, Supplier, String, Supplier, Consumer) standby} processes.
     *
     * @return <tt>true</tt> if debugging is enabled, <tt>false</tt> otherwise
     * @see Process#DEBUGGING
     * @see Processes#changeDebugging(String, boolean)
     */
    boolean isDebugging();

    /**
     * Determines if the current task is erroneous
     *
     * @return <tt>true</tt> if the task is marked as erroneous, <tt>false</tt> otherwise.
     */
    boolean isErroneous();

    /**
     * Marks the process as running.
     */
    void markRunning();

    /**
     * Marks the process as completed.
     * <p>
     * This is most probably done by {@link Processes#execute(String, Consumer)}. However, when executing in
     * multiple steps (maybe even on multiple nodes) using {@link Processes#partiallyExecute(String, Consumer)},
     * this has to be manually invoked once the process is finally completed.
     *
     * @param computationTimeInSeconds the computation time of the last manual step <tt>execute</tt> and
     *                                 <tt>partiallyExecute</tt> already record this manually.
     */
    void markCompleted(int computationTimeInSeconds);

    /**
     * Provides access to the context which has been provided for the process.
     * <p>
     * Note that this cannot and must not be modified.
     *
     * @return the context of the process
     */
    Map<String, String> getContext();

    /**
     * Reads the value stored for the given key from the context.
     *
     * @param key the name of the key to lookup in the context
     * @return the value stored in the context wrapped as Value or an empty value if there was no data
     */
    @Nonnull
    Value get(String key);

    /**
     * Uses the given parameter to read and convert a value from the context of the process.
     *
     * @param parameter the parameter used to read and convert
     * @param <V>       the type of the returned value
     * @return the value read from the process context wrapped as optional or an empty optional if there was no data or
     * a conversion error
     */
    @Nonnull
    <V> Optional<V> getParameter(Parameter<V> parameter);

    /**
     * Uses the given parameter to read and convert a value from the context of the process.
     * <p>
     * Throws an exception if no data is available or a conversion error occurs.
     *
     * @param parameter the parameter used to read and convert
     * @param <V>       the type of the returned value
     * @return the value read from the process context
     */
    @Nonnull
    <V> V require(Parameter<V> parameter);

    /**
     * Adds an external link to the process.
     *
     * @param link the link to add
     */
    void addLink(ProcessLink link);

    /**
     * Adds an external link to the process if it is not already present, based on the link's
     * {@link ProcessLink#equals(Object) equals(Object)} method.
     *
     * @param link the link to add
     */
    void addUniqueLink(ProcessLink link);

    /**
     * Clears all links from the process.
     */
    void clearLinks();

    /**
     * Adds the given reference to the process.
     *
     * @param reference the reference to attach
     */
    void addReference(String reference);

    /**
     * Adds an output to the process.
     *
     * @param output the output to add
     */
    void addOutput(ProcessOutput output);

    /**
     * Adds a chart output to the process.
     *
     * @param name  the name of the output
     * @param label the label of the output which will be {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     * @return a helper class used to provide one or more charts for the created output
     */
    ChartOutput addCharts(String name, String label);

    /**
     * Adds a table output to the process.
     *
     * @param name    the name of the output
     * @param label   the label of the output which will be {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     * @param columns a list of columns and their labels in the created table
     * @return a helper class used to provide rows for the created output
     */
    TableOutput addTable(String name, String label, List<Tuple<String, String>> columns);

    /**
     * Adds a table output to the process.
     *
     * @param name  the name of the output
     * @param label the label of the output which will be {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     * @return a builder {@link sirius.biz.process.output.TableOutput.ColumnBuilder} to add columns to the table
     */
    TableOutput.ColumnBuilder addTable(String name, String label);

    /**
     * Adds a log output to the process.
     * <p>
     * Use {@link ProcessLog#into(String)} to add log entries to this output.
     *
     * @param name  the name of the output
     * @param label the label of the output which will be {@link sirius.kernel.nls.NLS#smartGet(String) auto translated}
     */
    void addLogOutput(String name, String label);

    /**
     * Adds a file to the process.
     *
     * @param filename the filename to show
     * @param data     the data to persist
     */
    void addFile(String filename, File data);

    /**
     * Adds a file to the process which will contain the data written into the {@link OutputStream}.
     * <p>
     * This will create a temporary file which will buffer everything written into the returned output stream.
     * Once the stream is closed, the file is added using {@link #addFile(String, File)} and then deleted locally.
     *
     * @param filename the filename to use
     * @return an output stream to be supplied with the contents of the file
     * @throws IOException in case of a local IO error
     */
    OutputStream addFile(String filename) throws IOException;

    /**
     * Adds a file to the process, which contains a ZIP archive with the data written into the {@link OutputStream}.
     *
     * @param zipArchiveName the name of the created ZIP archive
     * @param filename       the name of the file within the archive
     * @return an output stream to be supplied with the contents of the file
     * @throws IOException in case of a local IO error
     */
    OutputStream addZipFile(String zipArchiveName, String filename) throws IOException;

    /**
     * Executes the given task in parallel to the main thread of this process.
     * <p>
     * If no "work stealing" threads are available the main thread is blocked and the task is executed there.
     * <p>
     * Note that the process will await the completion of all of its forked side tasks. However, once this state has
     * been reached no further tasks may be started. Therefore, when processing the results of this task, a
     * {@link sirius.kernel.async.CombinedFuture} has to be used in the main thread, rather than a simple completion
     * handler attached to the promise.
     *
     * @param parallelTask the task to execute in parallel
     * @param <P>          the type of result being produced by the given task
     * @return a promise which represents the result of this task. Note that the main thread can wait for the completion
     * of one or many tasks using {@link sirius.kernel.async.CombinedFuture}. However, a sideTask must not fork another
     * side task in the completion handler of the promise, as the process might have already ended / being shutdown then.
     */
    <P> Promise<P> computeInSideTask(Producer<P> parallelTask);

    /**
     * Executes the given task in parallel to the main thread of this process.
     * <p>
     * If no "work stealing" threads are available the main thread is blocked and the task is executed there.
     * <p>
     * Note that the process will await the completion of all of its forked side tasks. However, once this state has
     * been reached no further tasks may be started. Therefore, when processing the results of this task, a
     * {@link sirius.kernel.async.CombinedFuture} has to be used in the main thread, rather than a simple completion
     * handler attached to the future.
     *
     * @param parallelTask the task to execute in parallel
     * @return a future which is fulfilled once the task is completed.
     * @see #computeInSideTask(Producer) for a description why the completion handler of the future must not fork
     * additional sideTasks (other than using a {@link sirius.kernel.async.CombinedFuture} in the main thread).
     */
    Future performInSideTask(UnitOfWork parallelTask);

    /**
     * Blocks until all currently active side-tasks are completed.
     */
    void awaitSideTaskCompletion();

    /**
     * Blocks the current thread until all logs have been flushed into Elasticsearch.
     * <p>
     * As we internally pump the logs through the {@link sirius.biz.elastic.AutoBatchLoop}, we have to wait
     * until this ran, before trying to access them.
     *
     * @return <tt>true</tt> if the logs have successfully been flushed, <tt>false</tt> if a timeout occurred or if
     * the thread was interrupted while waiting for the flush.
     */
    boolean awaitFlushedLogs();

    /**
     * Tries to resolve the output with the given name.
     *
     * @param outputName the name of the output to fetch
     * @return the output with the given name or an empty optional, if no output with the given name exists
     */
    Optional<ProcessOutput> fetchOutput(String outputName);

    /**
     * Pulls all log/table entries from the given output and pumps it into the given consumers.
     * <p>
     * Note that {@link #awaitFlushedLogs()} should be called before this method to ensure that all logs are available.
     *
     * @param outputName               the name of the output to fetch the logs for. Use <tt>null</tt> to fetch the
     *                                 main logs.
     * @param columnsAndLabelsConsumer the consumer which is supplied with the column names and their labels
     * @param columnsAndValues         the consumer which is invoked for each entry/row, providing the column names and
     *                                 the actual row values
     */
    void fetchOutputEntries(@Nullable String outputName,
                            BiConsumer<List<String>, List<String>> columnsAndLabelsConsumer,
                            BiPredicate<List<String>, List<String>> columnsAndValues);

    /**
     * Get the {@link ProgressTracker} initialized by the process.
     *
     * @return the progress tracker
     */
    ProgressTracker getProgressTracker();

    /**
     * Adds a log file which collects log messages in addition to the process log.
     * <p>
     * Several log files might be added simultaneously. The messages which are written into the log file
     * are filtered using the provided {@link Predicate}, so a user could for example create a file with
     * warnings and another with errors. If anything has been written into the log file, it will be
     * uploaded to the process upon completion.
     * <p>
     * This feature is very useful when using limited process logging, so a process can still collect
     * all messages into a separate file.
     *
     * @param fileName      the name of the file to be created.
     * @param logFileFilter permits to filter the log messages which are written into the file.
     * @see #log(ProcessLog)
     */
    void addLogFile(@Nonnull String fileName, @Nonnull Predicate<ProcessLog> logFileFilter);
}
