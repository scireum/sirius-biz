/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.kernel.async.TaskContextAdapter;

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

    /**
     * Adds a warning to the task log.
     *
     * @param message the message to log
     */
    void warn(Object message);

    /**
     * Adds an error to the task log.
     *
     * @param message the message to log
     */
    void error(Object message);

    void handle(Exception e);

    /**
     * Determines if the current task is erroneous
     *
     * @return <tt>true</tt> if the task is marked as erroneous, <tt>false</tt> otherwise.
     */
    boolean isErroneous();

    void markRunning();

    void markCompleted();
}
