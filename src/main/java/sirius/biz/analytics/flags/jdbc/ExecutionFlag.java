/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.analytics.flags.jdbc;

import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Index;
import sirius.db.mixing.annotations.Length;
import sirius.kernel.di.std.Framework;

import java.time.LocalDateTime;

/**
 * Used to store execution flags in a JDBC database.
 * <p>
 * Entities of this type shouldn't be mutated outside of {@link SQLExecutionFlags}.
 * Use {@link sirius.biz.analytics.flags.ExecutionFlags} for reading and writing flags in a database independent manner.
 */
@Framework(SQLExecutionFlags.FRAMEWORK_EXECUTION_FLAGS_JDBC)
@Index(name = "name_lookup", columns = "name")
public class ExecutionFlag extends SQLEntity {

    /**
     * Stores the effective name of an execution flag.
     */
    public static final Mapping NAME = Mapping.named("name");
    @Length(100)
    private String name;

    /**
     * Stores the last execution of this flag.
     */
    public static final Mapping LAST_EXECUTION = Mapping.named("lastExecution");
    private LocalDateTime lastExecution;

    /**
     * Stores the timestamp from which on this flag can be deleted.
     * <p>
     * As the reference object might vanish, we do not store flags for an unlimited amount of time, but rather
     * purge old flags on a regular basis.
     */
    public static final Mapping STORAGE_LIMIT = Mapping.named("storageLimit");
    private LocalDateTime storageLimit;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getLastExecution() {
        return lastExecution;
    }

    public void setLastExecution(LocalDateTime lastExecution) {
        this.lastExecution = lastExecution;
    }

    public LocalDateTime getStorageLimit() {
        return storageLimit;
    }

    public void setStorageLimit(LocalDateTime storageLimit) {
        this.storageLimit = storageLimit;
    }
}
