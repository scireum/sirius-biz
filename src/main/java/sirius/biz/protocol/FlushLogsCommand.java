/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.es.Elastic;
import sirius.kernel.commons.Value;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.console.Command;
import sirius.kernel.nls.NLS;

import java.time.LocalDateTime;

/**
 * Deletes all logs older than N days.
 */
@Register(framework = Protocols.FRAMEWORK_PROTOCOLS)
public class FlushLogsCommand implements Command {

    @Part
    private Elastic elastic;

    @Override
    public void execute(Output output, String... params) throws Exception {
        int numDays = Value.indexOf(0, params).asInt(0);
        LocalDateTime limit = LocalDateTime.now().minusDays(numDays);
        output.apply("Deleted everything older than: %s", NLS.toUserString(limit));
        elastic.select(LoggedMessage.class).where(Elastic.FILTERS.lt(LoggedMessage.TOD, limit)).truncate();
    }

    @Override
    public String getName() {
        return "flushLogs";
    }

    @Override
    public String getDescription() {
        return "Deletes all logs older than the number of given days";
    }
}
