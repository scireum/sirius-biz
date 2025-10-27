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
import sirius.biz.storage.layer2.jdbc.SQLVariant;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.OMA;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Implementation for the {@link MissingBlobObjectCheckJob} using the {@link SQLBlobStorage#FRAMEWORK_JDBC_BLOB_STORAGE}.
 */
public class SearchOrphanS3ObjectsSQLJob extends SearchOrphanS3ObjectsJob {

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
    protected SearchOrphanS3ObjectsSQLJob(ProcessContext process) {
        super(process);
    }

    @Override
    protected boolean blobExists(String physicalObjectKey) {
        return oma.selectFromSecondary(SQLBlob.class).eq(SQLBlob.PHYSICAL_OBJECT_KEY, physicalObjectKey).exists();
    }

    @Override
    protected boolean variantExists(String physicalObjectKey) {
        return oma.selectFromSecondary(SQLVariant.class).eq(SQLVariant.PHYSICAL_OBJECT_KEY, physicalObjectKey).exists();
    }

    /**
     * Provides a factory to create instances of {@link SearchOrphanS3ObjectsSQLJob}.
     */
    @Register(framework = SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    public static class SearchOrphanS3ObjectsSQLJobFactory extends SearchOrphanS3ObjectsJobFactory {

        @Override
        protected SearchOrphanS3ObjectsSQLJob createJob(ProcessContext process) {
            return new SearchOrphanS3ObjectsSQLJob(process);
        }

        @Nonnull
        @Override
        public String getName() {
            return "search-orphan-s3-objects-sql";
        }
    }
}
