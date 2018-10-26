/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import com.alibaba.fastjson.JSONObject;
import sirius.kernel.async.CallContext;
import sirius.kernel.async.Tasks;
import sirius.kernel.commons.Values;
import sirius.kernel.di.std.Part;
import sirius.kernel.health.console.Command;

public abstract class ProcessCommand implements Command {

    @Part
    private Processes processes;

    @Part
    private Tasks tasks;

    @Override
    public void execute(Output output, String... args) throws Exception {
        Values argumentValues = Values.of(args);
        String processId =
                processes.createProcessForCurrentUser(createTitle(argumentValues), createContext(argumentValues));
        tasks.defaultExecutor().fork(() -> processes.execute(processId, this::executeProcess));

        output.apply("A process has been started on node %s: %s", CallContext.getNodeName(), "/process/" + processId);
    }

    protected abstract String createTitle(Values args);

    protected abstract JSONObject createContext(Values args);

    protected abstract void executeProcess(ProcessContext context);
}
