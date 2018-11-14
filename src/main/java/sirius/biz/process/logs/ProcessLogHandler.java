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

import java.util.List;

public interface ProcessLogHandler extends Named {

    String getLabel();

    String formatMessage(ProcessLog log);

    List<ProcessLogAction> getActions(ProcessLog log);

    boolean executeAction(WebContext request, Process process, ProcessLog log, String action, String returnUrl);
}
