/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.process;

import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.Named;
import sirius.web.http.WebContext;

import java.util.List;

public interface ProcessLogHandler extends Named {

    String getLabel();

    String formatMessage(ProcessLog log);

    List<Tuple<String, String>> getActions(ProcessLog log);

    void executeAction(WebContext request, ProcessLog log, String action);
}
