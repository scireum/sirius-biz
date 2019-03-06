/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags.jdbc;

import sirius.biz.analytics.flags.ExecutionFlags;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.timer.EveryDay;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;

/**
 * Provides an implementation which stores the execution flags in a JDBC database as {@link ExecutionFlag}.
 */
@Register(classes = {ExecutionFlags.class, EveryDay.class},
        framework = SQLExecutionFlags.FRAMEWORK_EXECUTION_FLAGS_JDBC)
public class SQLExecutionFlags extends ExecutionFlags implements EveryDay {

    /**
     * Determines the name of the framework which enables this implementation.
     */
    public static final String FRAMEWORK_EXECUTION_FLAGS_JDBC = "biz.analytics.execution-flags-jdbc";

    @Part
    private OMA oma;

    @Override
    public Optional<LocalDateTime> readExecutionFlag(String reference, String flag) {
        return oma.select(ExecutionFlag.class)
                  .eq(ExecutionFlag.NAME, getEffectiveFlagName(reference, flag))
                  .first()
                  .map(ExecutionFlag::getLastExecution);
    }

    @Override
    public void storeExecutionFlag(String reference,
                                   String flag,
                                   LocalDateTime executionTimestamp,
                                   Period storageDuration) {
        if (executionTimestamp == null) {
            oma.select(ExecutionFlag.class).eq(ExecutionFlag.NAME, getEffectiveFlagName(reference, flag)).delete();
        } else {
            String flagName = getEffectiveFlagName(reference, flag);
            ExecutionFlag executionFlag = oma.select(ExecutionFlag.class)
                                             .eq(ExecutionFlag.NAME, flagName)
                                             .first()
                                             .orElseGet(ExecutionFlag::new);

            executionFlag.setName(flagName);
            executionFlag.setLastExecution(executionTimestamp);
            executionFlag.setStorageLimit(executionTimestamp.plusDays(storageDuration.getDays()));
            oma.update(executionFlag);
        }
    }

    @Override
    public String getConfigKeyName() {
        return "delete-execution-flags";
    }

    @Override
    public void runTimer() throws Exception {
        oma.select(ExecutionFlag.class)
           .where(OMA.FILTERS.lt(ExecutionFlag.STORAGE_LIMIT, LocalDateTime.now()))
           .delete();
    }
}
