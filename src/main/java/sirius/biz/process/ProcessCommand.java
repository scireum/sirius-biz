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

import java.util.HashMap;
import java.util.Map;

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
        String processId = processes.createProcessForCurrentUser(createTitle(argumentValues), context);
        tasks.defaultExecutor().fork(() -> processes.execute(processId, this::executeProcess));

        output.apply("A process has been started on node %s: %s", CallContext.getNodeName(), "/ps/" + processId);
    }

    protected abstract String createTitle(Values args);

    protected abstract void fillContext(Values args, Map<String, String> context);

    protected abstract void executeProcess(ProcessContext context);
}
