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
import sirius.kernel.async.TaskContext;
import sirius.kernel.async.TaskContextAdapter;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.HandledException;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Declares the client API of a process.
 * <p>
 * {@link Processes} will instantiate and provide an instace of this to caller while executing a process. Also it will
 * install this using {@link sirius.kernel.async.TaskContext#setAdapter(TaskContextAdapter)} so that calls to
 * {@link sirius.kernel.async.TaskContext} will be delegated to the processes framework.
 */
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
     * Handles the given exception.
     * <p>
     * This will invoke {@link Exceptions#handle()} and log the result.
     *
     * @param e the exception to handle
     * @return the handled exception for further processing
     */
    HandledException handle(Exception e);

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
     * Marks the process as completed.
     * <p>
     * This is most probably done by {@link Processes#execute(String, Consumer)}. However, when executing in
     * multiple steps (maybe even on multiple nodes) using {@link Processes#partiallyExecute(String, Consumer)},
     * this has to be manually invoked once the process is finally completed.
     */
    void markCompleted();

    /**
     * Updates the "current state" message of the process.
     * <p>
     * Note that this doesn't perform any rate limiting etc. Therefore {@link TaskContext#shouldUpdateState()}
     * along with {@link TaskContext#setState(String, Object...)} is most probably a better choice.
     *
     * @param state the new state message to show
     */
    void setCurrentStateMessage(String state);

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
     * @param <P>       the generic type of the parameter
     * @return the value read from the process context wrapped as optional or an empty optional if there was no data or
     * a conversion error
     */
    @Nonnull
    <V, P extends Parameter<V, P>> Optional<V> getParameter(Parameter<V, P> parameter);

    /**
     * Uses the given parameter to read and convert a value from the context of the process.
     * <p>
     * Thows an exception of no data is available or an conversion error occurs.
     *
     * @param parameter the parameter used to read and convert
     * @param <V>       the type of the returned value
     * @param <P>       the generic type of the parameter
     * @return the value read from the process context
     */
    @Nonnull
    <V, P extends Parameter<V, P>> V require(Parameter<V, P> parameter);

    /**
     * Adds an external link to the process.
     *
     * @param link the link to add
     */
    void addLink(ProcessLink link);

    /**
     * Adds the given reference to the the process.
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
     * Adds an additional log output to the process.
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
}
