/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
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
import sirius.db.mongo.MongoQuery;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Implementation for the {@link MissingBlobObjectCheckJob} using the {@link MongoBlobStorage#FRAMEWORK_MONGO_BLOB_STORAGE}.
 */
public class MissingMongoBlobObjectCheckJob extends MissingBlobObjectCheckJob<MongoBlob, MongoVariant, String> {

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
    protected MissingMongoBlobObjectCheckJob(ProcessContext process) {
        super(process);
    }

    @Override
    protected List<MongoBlob> fetchNextBlobBatch(String lastId) {
        MongoQuery<MongoBlob> query = mango.select(MongoBlob.class)
                                           .eq(MongoBlob.SPACE_NAME, getStorageSpaceName())
                                           .eq(MongoBlob.DELETED, false)
                                           .eq(MongoBlob.COMMITTED, true);
        if (Strings.isFilled(lastId)) {
            query.where(mango.filters().gt(MongoBlob.ID, lastId));
        } else {
            String startId = fetchStartId();
            if (Strings.isFilled(startId)) {
                // This is the first query, and we want to start from a specific ID
                query.where(mango.filters().gte(MongoBlob.ID, startId));
            }
        }
        return query.orderAsc(MongoBlob.ID).queryList();
    }

    @Override
    protected List<MongoVariant> fetchVariants(MongoBlob blob) {
        return mango.select(MongoVariant.class)
                    .eq(MongoVariant.BLOB, blob.getId())
                    .eq(MongoVariant.QUEUED_FOR_CONVERSION, false)
                    .where(mango.filters().filled(MongoVariant.PHYSICAL_OBJECT_KEY))
                    .queryList();
    }

    @Override
    protected String fetchStartId() {
        return process.get(START_FROM_ID_PARAMETER).asString();
    }

    /**
     * Provides a factory to create instances of {@link MissingMongoBlobObjectCheckJob}.
     */
    @Register(framework = MongoBlobStorage.FRAMEWORK_MONGO_BLOB_STORAGE)
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    public static class MissingMongoBlobObjectCheckJobFactory extends MissingBlobObjectCheckJobFactory {

        @Override
        protected MissingMongoBlobObjectCheckJob createJob(ProcessContext process) {
            return new MissingMongoBlobObjectCheckJob(process);
        }

        @Nonnull
        @Override
        public String getName() {
            return "missing-mongo-blob-object-check";
        }
    }
}
