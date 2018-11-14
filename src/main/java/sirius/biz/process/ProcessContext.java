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
import sirius.kernel.async.TaskContextAdapter;
import sirius.kernel.commons.Tuple;
import sirius.kernel.commons.Value;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProcessContext extends TaskContextAdapter {

    /**
     * Logs the given message unless the method is called to frequently.
     * <p>
     * This method has an internal rate limit and can therefore be used by loops etc. to report the progress
     * every now and then.
     * <p>
     * A caller can rely on the rate limit and therefore can invoke this method as often as desired. Howerver
     * one must not rely on any message to be shown.
     *
     * @param message the message to add to the logs.
     */
    void logLimited(Object message);

    /**
     * Increments the given performance counter by one and supplies a loop duration in milliseconds.
     * <p>
     * The avarage value will be computed for the given counter and gives the user a rough estimate what the current
     * task is doing.
     *
     * @param counter the counter to increment
     * @param millis  the current duration for the block being counted
     */
    void addTiming(String counter, long millis);

    void handle(Exception e);

    void log(ProcessLog logEntry);

    /**
     * Determines if the current task is erroneous
     *
     * @return <tt>true</tt> if the task is marked as erroneous, <tt>false</tt> otherwise.
     */
    boolean isErroneous();

    void markCompleted();

    void setCurrentStateMessage(String state);

    Map<String, String> getContext();

    Value get(String name);

    <V, P extends Parameter<V, P>> Optional<V> getParameter(Parameter<V, P> parameter);

    <V, P extends Parameter<V, P>> V require(Parameter<V, P> parameter);

    void addLink(ProcessLink link);

    void addOutput(ProcessOutput table);

    ChartOutput addCharts(String name, String label);

    TableOutput addTable(String name, String label, List<Tuple<String, String>> columns);

    void addLogOutput(String name, String label);

    void addFile(String filename, File data);
}
