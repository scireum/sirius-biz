/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags.mongo;

import sirius.biz.analytics.flags.ExecutionFlags;
import sirius.db.mongo.Mango;
import sirius.db.mongo.QueryBuilder;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.timer.EveryDay;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;

/**
 * Provides an implementation which stores the execution flags in a MongoDB as {@link ExecutionFlag}.
 */
@Register(classes = {ExecutionFlags.class, EveryDay.class},
        framework = MongoExecutionFlags.FRAMEWORK_EXECUTION_FLAGS_MONGO)
public class MongoExecutionFlags extends ExecutionFlags implements EveryDay {

    /**
     * Determines the name of the framework which enables this implementation.
     */
    public static final String FRAMEWORK_EXECUTION_FLAGS_MONGO = "biz.analytics-execution-flags-mongo";

    @Part
    private Mango mango;

    @Override
    public Optional<LocalDateTime> readExecutionFlag(String reference, String flag) {
        return mango.select(ExecutionFlag.class)
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
            mango.select(ExecutionFlag.class).eq(ExecutionFlag.NAME, getEffectiveFlagName(reference, flag)).delete();
        } else {
            String flagName = getEffectiveFlagName(reference, flag);
            ExecutionFlag executionFlag = mango.select(ExecutionFlag.class)
                                               .eq(ExecutionFlag.NAME, flagName)
                                               .first()
                                               .orElseGet(ExecutionFlag::new);
            executionFlag.setName(flagName);
            executionFlag.setLastExecution(executionTimestamp);
            executionFlag.setStorageLimit(executionTimestamp.plusDays(storageDuration.getDays()));
            mango.update(executionFlag);
        }
    }

    @Override
    public String getConfigKeyName() {
        return "delete-execution-flags";
    }

    @Override
    public void runTimer() throws Exception {
        mango.select(ExecutionFlag.class)
             .where(QueryBuilder.FILTERS.lt(ExecutionFlag.STORAGE_LIMIT, LocalDateTime.now()))
             .delete();
    }
}
