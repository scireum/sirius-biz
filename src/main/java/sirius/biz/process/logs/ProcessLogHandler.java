/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.logs;

import sirius.biz.process.Process;
import sirius.kernel.di.std.Named;
import sirius.web.http.WebContext;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Assists in formatting {@link ProcessLog log entries} or providing and executing messages for it.
 */
public interface ProcessLogHandler extends Named {

    /**
     * Generates the effective log message based on the context of the given entry.
     *
     * @param log the entry to format
     * @return the effective message to show
     */
    @Nullable
    String formatMessage(ProcessLog log);

    /**
     * Enumerates the actions available for the given entry.
     * <p>
     * Note that {@link ProcessLog#getDefaultActions()} provides a decent list of default actions based on the state
     * of the entry.
     *
     * @param log the entry to provide actions for
     * @return a list of actions available for the given entry
     */
    List<ProcessLogAction> getActions(ProcessLog log);

    /**
     * Executes the given action by responding to the given request for the given process and log entry.
     * <p>
     * Note that modifying the {@link ProcessLog#STATE} of an entry can be done using
     * {@link sirius.biz.process.Processes#updateProcessLogStateAndReturn(ProcessLog, ProcessLogState, WebContext, String)}.
     *
     * @param request   the request to respond to
     * @param process   the process to which the log entry belongs
     * @param log       the log entry to execute the action for
     * @param action    the name of the action to execute
     * @param returnUrl the url to eventually redirect to, once the action is fully completed
     * @return <tt>true</tt> if the action was executed (or its execution was started) and the request has been
     * respondedm <tt>false</tt> if the system should generate a response
     */
    boolean executeAction(WebContext request, Process process, ProcessLog log, String action, String returnUrl);
}
