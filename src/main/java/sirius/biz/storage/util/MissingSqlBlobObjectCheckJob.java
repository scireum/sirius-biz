/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer2.jdbc.SQLBlobStorage;
import sirius.biz.tenants.TenantUserManager;
import sirius.kernel.di.std.Framework;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Implementation for the {@link MissingBlobObjectCheckJob} using the {@link SQLBlobStorage#FRAMEWORK_JDBC_BLOB_STORAGE}.
 */
public class MissingSqlBlobObjectCheckJob extends MissingBlobObjectCheckJob {
    /**
     * Creates a new batch job for the given batch process.
     * <p>
     * As a batch job is created per execution, subclasses can define fields and fill those from parameters
     * defined by their factory.
     *
     * @param process the context in which the process will be executed
     */
    protected MissingSqlBlobObjectCheckJob(ProcessContext process) {
        super(process);
    }

    /**
     * Provides a factory to create instances of {@link MissingSqlBlobObjectCheckJob}.
     */
    @Register
    @Framework(SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    public static class MissingMongoBlobObjectCheckJobFactory extends MissingBlobObjectCheckJobFactory {

        @Override
        protected MissingBlobObjectCheckJob createJob(ProcessContext process) {
            return new MissingSqlBlobObjectCheckJob(process);
        }

        @Nonnull
        @Override
        public String getName() {
            return "missing-sql-blob-object-check";
        }
    }
}
