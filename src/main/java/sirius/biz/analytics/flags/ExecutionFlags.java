/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags;

import sirius.db.mixing.BaseEntity;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;

/**
 * Provides a database independent framework to remember the execution timestamp of certain tasks for referenced objects.
 * <p>
 * This might be used to remember when the last execution of a specific checkup or analytical task for an entity
 * has been executed.
 * <p>
 * Note that either {@link sirius.biz.analytics.flags.jdbc.SQLExecutionFlags#FRAMEWORK_EXECUTION_FLAGS_JDBC} or
 * {@link sirius.biz.analytics.flags.mongo.MongoExecutionFlags#FRAMEWORK_EXECUTION_FLAGS_MONGO} has to be enabled,
 * depending on which database should be used to store the flags.
 */
public abstract class ExecutionFlags {

    /**
     * Determines the last execution of the given <tt>flag</tt> for the given <tt>reference</tt>.
     *
     * @param reference the entity for which the execution flag is to be determined
     * @param flag      the flag or type of execution to read
     * @return the timestamp of the last execution wrapped as optional or an empty optional if no record is available
     */
    public Optional<LocalDateTime> readExecutionFlag(BaseEntity<?> reference, String flag) {
        return readExecutionFlag(reference.getUniqueName(), flag);
    }

    /**
     * Determines the last execution of the given <tt>flag</tt> for the given <tt>reference</tt>.
     *
     * @param reference the entity for which the execution flag is to be determined
     * @param flag      the flag or type of execution to read
     * @return the timestamp of the last execution wrapped as optional or an empty optional if no record is available
     */
    public abstract Optional<LocalDateTime> readExecutionFlag(String reference, String flag);

    /**
     * Determines if the last execution of the given <tt>flag</tt> for the given <tt>reference</tt> was within the
     * given <tt>duration</tt>.
     *
     * @param reference the entity for which the execution flag is to be determined
     * @param flag      the flag or type of execution to read
     * @param duration  the reference duration
     * @return <tt>true</tt> was stored (set) within the given duration, <tt>false</tt> otherwise
     */
    public boolean wasExecuted(BaseEntity<?> reference, String flag, Duration duration) {
        return wasExecuted(reference.getUniqueName(), flag, duration);
    }

    /**
     * Determines if the last execution of the given <tt>flag</tt> for the given <tt>reference</tt> was within the
     * given <tt>duration</tt>.
     *
     * @param reference the entity for which the execution flag is to be determined
     * @param flag      the flag or type of execution to read
     * @param duration  the reference duration
     * @return <tt>true</tt> was stored (set) within the given duration, <tt>false</tt> otherwise
     */
    public boolean wasExecuted(String reference, String flag, Duration duration) {
        Optional<LocalDateTime> timestamp = readExecutionFlag(reference, flag);
        return timestamp.filter(localDateTime -> Duration.between(localDateTime, LocalDateTime.now())
                                                         .compareTo(duration) < 0).isPresent();
    }

    /**
     * Stores the execution of a specific task or flag for the given reference.
     *
     * @param reference          the entity for which the execution flag is to be stored
     * @param flag               the flag or type of execution to store
     * @param executionTimestamp the timestamp of execution
     * @param storageDuration    used to determine how long a flag must be remembered
     */
    public void storeExecutionFlag(BaseEntity<?> reference,
                                   String flag,
                                   LocalDateTime executionTimestamp,
                                   Period storageDuration) {
        storeExecutionFlag(reference.getUniqueName(), flag, executionTimestamp, storageDuration);
    }

    /**
     * Stores the execution of a specific task or flag for the given reference.
     *
     * @param reference          the entity for which the execution flag is to be stored
     * @param flag               the flag or type of execution to store
     * @param executionTimestamp the timestamp of execution
     * @param storageDuration    used to determine how long a flag must be remembered
     */
    public abstract void storeExecutionFlag(String reference,
                                            String flag,
                                            LocalDateTime executionTimestamp,
                                            Period storageDuration);

    /**
     * Computes the effective name of an execution flag.
     *
     * @param reference the reference for which the flag should be stored
     * @param flag      the flag itself
     * @return the effective name to store and lookup
     */
    protected String getEffectiveFlagName(String reference, String flag) {
        return reference + ":" + flag;
    }
}
