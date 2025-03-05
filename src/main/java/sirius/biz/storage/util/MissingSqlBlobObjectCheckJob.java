/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer2.jdbc.SQLBlob;
import sirius.biz.storage.layer2.jdbc.SQLBlobStorage;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Implementation for the {@link MissingBlobObjectCheckJob} using the {@link SQLBlobStorage#FRAMEWORK_JDBC_BLOB_STORAGE}.
 */
public class MissingSqlBlobObjectCheckJob extends MissingBlobObjectCheckJob<SQLBlob, Long> {

    @Part
    private static OMA oma;

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

    @Override
    protected List<SQLBlob> fetchNextBlobBatch(Long lastId) {
        SmartQuery<SQLBlob> query = oma.select(SQLBlob.class)
                                       .eq(SQLBlob.SPACE_NAME, getStorageSpaceName())
                                       .eq(SQLBlob.DELETED, false)
                                       .eq(SQLBlob.COMMITTED, true);
        if (Strings.isFilled(lastId)) {
            query.where(oma.filters().gt(SQLBlob.ID, lastId));
        }
        return query.orderAsc(SQLBlob.ID).queryList();
    }

    /**
     * Provides a factory to create instances of {@link MissingSqlBlobObjectCheckJob}.
     */
    @Register(framework = SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    public static class MissingMongoBlobObjectCheckJobFactory extends MissingBlobObjectCheckJobFactory {

        @Override
        protected MissingSqlBlobObjectCheckJob createJob(ProcessContext process) {
            return new MissingSqlBlobObjectCheckJob(process);
        }

        @Nonnull
        @Override
        public String getName() {
            return "missing-sql-blob-object-check";
        }
    }
}
