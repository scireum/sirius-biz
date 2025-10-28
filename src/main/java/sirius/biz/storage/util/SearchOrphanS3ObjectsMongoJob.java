/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer2.mongo.MongoBlob;
import sirius.biz.storage.layer2.mongo.MongoBlobStorage;
import sirius.biz.storage.layer2.mongo.MongoVariant;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.mongo.Mango;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;

/**
 * Implementation for the {@link MissingBlobObjectCheckJob} using the {@link MongoBlobStorage#FRAMEWORK_MONGO_BLOB_STORAGE}.
 */
public class SearchOrphanS3ObjectsMongoJob extends SearchOrphanS3ObjectsJob {

    @Part
    private static Mango mango;

    /**
     * Creates a new batch job for the given batch process.
     * <p>
     * As a batch job is created per execution, subclasses can define fields and fill those from parameters
     * defined by their factory.
     *
     * @param process the context in which the process will be executed
     */
    protected SearchOrphanS3ObjectsMongoJob(ProcessContext process) {
        super(process);
    }

    @Override
    protected boolean blobExists(String physicalObjectKey) {
        return mango.selectFromSecondary(MongoBlob.class).eq(MongoBlob.PHYSICAL_OBJECT_KEY, physicalObjectKey).exists();
    }

    @Override
    protected boolean variantExists(String physicalObjectKey) {
        return mango.selectFromSecondary(MongoVariant.class)
                    .eq(MongoVariant.PHYSICAL_OBJECT_KEY, physicalObjectKey)
                    .exists();
    }

    /**
     * Provides a factory to create instances of {@link SearchOrphanS3ObjectsMongoJob}.
     */
    @Register(framework = MongoBlobStorage.FRAMEWORK_MONGO_BLOB_STORAGE)
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    public static class SearchOrphanS3ObjectsMongoJobFactory extends SearchOrphanS3ObjectsJobFactory {

        @Override
        protected SearchOrphanS3ObjectsMongoJob createJob(ProcessContext process) {
            return new SearchOrphanS3ObjectsMongoJob(process);
        }

        @Nonnull
        @Override
        public String getName() {
            return "search-orphan-s3-objects-mongo";
        }
    }
}
