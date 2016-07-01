/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.protocol;

import sirius.db.mixing.OMA;
import sirius.db.mixing.constraints.FieldOperator;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.timer.EveryDay;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Removes old {@link LogEntry} and {@link Incident} objects.
 * <p>
 * The duration for which logs and incidents are kept can be controlled via <tt>journal.keep-logs</tt> and
 * <tt>journal.keep-incidents</tt>.
 */
@Register
public class JournalCleaner implements EveryDay {

    @Override
    public String getConfigKeyName() {
        return "journal-cleaner";
    }

    @Part
    private OMA oma;

    @ConfigValue("journal.keep-logs")
    private Duration keepLogs;

    @ConfigValue("journal.keep-incidents")
    private Duration keepIncidents;

    @Override
    public void runTimer() throws Exception {
        LocalDateTime limit = LocalDateTime.now().minusSeconds(keepLogs.getSeconds());
        oma.select(LogEntry.class).where(FieldOperator.on(LogEntry.TOD).lessThan(limit)).delete();

        limit = LocalDateTime.now().minusSeconds(keepIncidents.getSeconds());
        oma.select(Incident.class)
           .where(FieldOperator.on(Incident.LAST_OCCURRENCE).lessThan(limit))
           .delete();
    }
}
