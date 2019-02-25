/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.kernel.async.CallContext;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Values;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.console.Command;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines a command which executed is represented as {@link Process}.
 * <p>
 * Therefore its progress and output can be monitored independent of the node it runs on.
 */
public abstract class ProcessCommand implements Command {

    @Part
    private Processes processes;

    @Part
    private Tasks tasks;

    @Override
    public void execute(Output output, String... args) throws Exception {
        Values argumentValues = Values.of(args);
        Map<String, String> context = new HashMap<>();
        fillContext(argumentValues, context);
        String processId = processes.createProcessForCurrentUser(null,
                                                                 createTitle(argumentValues),
                                                                 getIcon(argumentValues),
                                                                 context);
        tasks.defaultExecutor().fork(() -> processes.execute(processId, this::executeProcess));

        output.apply("A process has been started on node %s: %s", CallContext.getNodeName(), "/ps/" + processId);
    }

    /**
     * Generates the title of the process based on the arguments.
     *
     * @param args the arguments passed to the command
     * @return the title for the process
     */
    protected abstract String createTitle(Values args);

    /**
     * Determines the icon to use for the process.
     *
     * @param args the arguments passed to the command
     * @return the icon to use or <tt>null</tt> to indicate that the default icon should be used
     */
    @Nullable
    protected String getIcon(Values args) {
        return null;
    }

    /**
     * Transforms the arguments of the command into the context present in the process.
     *
     * @param args    the arguments passed to the command
     * @param context the context to be used for the process
     */
    protected abstract void fillContext(Values args, Map<String, String> context);

    /**
     * Invoked within the process which is represented as the given {@link ProcessContext}.
     *
     * @param context the execution context of the created process
     */
    protected abstract void executeProcess(ProcessContext context);
}
