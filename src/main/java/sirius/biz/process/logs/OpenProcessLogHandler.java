/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.process.logs;

import sirius.biz.process.Process;
import sirius.kernel.di.std.Register;
import sirius.web.http.WebContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Provides a log message handler, which provides an action to directly jump to a {@link Process}.
 */
@Register(classes = {OpenProcessLogHandler.class, ProcessLogHandler.class})
public class OpenProcessLogHandler implements ProcessLogHandler {

    /**
     * Defines the parameter which contains the ID of the process to jump to.
     */
    public static final String PARAM_TARGET_PROCESS_ID = "targetProcessId";
    private static final String ACTION_NAME = "jumpToProcess";

    @Nullable
    @Override
    public String formatMessage(ProcessLog log) {
        return null;
    }

    @Override
    public List<ProcessLogAction> getActions(ProcessLog log) {
        if (log.getContext().containsKey(PARAM_TARGET_PROCESS_ID)) {
            return Collections.singletonList(new ProcessLogAction(log, ACTION_NAME).withLabelKey(
                    "OpenProcessLogHandler.jobLink").withIcon("fa-solid fa-up-right-from-square"));
        }
        return Collections.emptyList();
    }

    @Override
    public boolean executeAction(WebContext request, Process process, ProcessLog log, String action, String returnUrl) {
        if (!ACTION_NAME.equals(action)) {
            return false;
        }
        Optional<String> processId = log.getContext().get(PARAM_TARGET_PROCESS_ID);
        processId.ifPresent(id -> request.respondWith().redirectToGet("/ps/" + id));
        return processId.isPresent();
    }

    @Nonnull
    @Override
    public String getName() {
        return "open-process";
    }
}
