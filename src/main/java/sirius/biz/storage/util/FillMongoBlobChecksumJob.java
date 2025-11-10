/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.storage.util;

import sirius.biz.jobs.batch.BatchJob;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer2.mongo.MongoBlob;
import sirius.biz.storage.layer2.mongo.MongoBlobStorage;
import sirius.biz.storage.layer2.mongo.MongoVariant;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.mixing.query.BaseQuery;
import sirius.db.mongo.Mango;
import sirius.db.mongo.Mongo;
import sirius.db.mongo.MongoQuery;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Implementation for the {@link FillBlobChecksumJob} using the {@link MongoBlobStorage#FRAMEWORK_MONGO_BLOB_STORAGE}.
 */
public class FillMongoBlobChecksumJob extends FillBlobChecksumJob<MongoBlob, String> {

    @Part
    private static Mango mango;

    @Part
    private static Mongo mongo;

    private final BiConsumer<ProcessContext, MongoQuery<MongoBlob>> queryTuner;

    /**
     * Creates a new batch job for the given batch process.
     * <p>
     * As a batch job is created per execution, subclasses can define fields and fill those from parameters
     * defined by their factory.
     *
     * @param process    the context in which the process will be executed
     * @param queryTuner allows to tune the query used to fetch the blobs to be processed
     */
    protected FillMongoBlobChecksumJob(ProcessContext process,
                                       BiConsumer<ProcessContext, MongoQuery<MongoBlob>> queryTuner) {
        super(process);
        this.queryTuner = queryTuner;
    }

    @Override
    protected void updateBlobChecksum(MongoBlob blob, String checksum) {
        mongo.update()
             .where(MongoBlob.ID, blob.getId())
             .set(MongoBlob.CHECKSUM, checksum)
             .executeForOne(MongoBlob.class);
    }

    @Override
    protected void updateVariantChecksum(BlobVariant variant, String checksum) {
        mongo.update()
             .where(MongoVariant.ID, variant.getIdAsString())
             .set(MongoVariant.CHECKSUM, checksum)
             .executeForOne(MongoVariant.class);
    }

    @Override
    protected String fetchStartId() {
        return process.get(START_FROM_ID_PARAMETER).asString();
    }

    @Override
    protected List<MongoBlob> fetchNextBlobBatch(String lastId) {
        MongoQuery<MongoBlob> query = mango.selectFromSecondary(MongoBlob.class)
                                           .eq(MongoBlob.SPACE_NAME, getStorageSpaceName())
                                           .eq(MongoBlob.DELETED, false)
                                           .eq(MongoBlob.COMMITTED, true)
                                           .ne(MongoBlob.PHYSICAL_OBJECT_KEY, null)
                                           .eq(MongoBlob.CHECKSUM, null);
        if (Strings.isFilled(lastId)) {
            query.where(mango.filters().gt(MongoBlob.ID, lastId));
        } else {
            String startId = fetchStartId();
            if (Strings.isFilled(startId)) {
                // This is the first query, and we want to start from a specific ID
                query.where(mango.filters().gte(MongoBlob.ID, startId));
            }
        }
        if (queryTuner != null) {
            queryTuner.accept(process, query);
        }
        return query.orderAsc(MongoBlob.ID).limit(BaseQuery.MAX_LIST_SIZE).queryList();
    }

    /**
     * Provides a factory to create instances of {@link FillMongoBlobChecksumJob}.
     */
    @Register(framework = MongoBlobStorage.FRAMEWORK_MONGO_BLOB_STORAGE)
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    public static class FillMongoBlobJobFactory extends FillBlobChecksumJobFactory {

        @Override
        protected BatchJob createJob(ProcessContext process) throws Exception {
            return new FillMongoBlobChecksumJob(process, getQueryTuner());
        }

        @Nonnull
        @Override
        public String getName() {
            return "fill-mongo-blob-checksum";
        }

        /**
         * Allows to provide a tuner for the query used to fetch the blobs to be processed.
         *
         * @return a consumer which tunes the query or <tt>null</tt> to not modify it
         */
        public BiConsumer<ProcessContext, MongoQuery<MongoBlob>> getQueryTuner() {
            return null;
        }
    }
}
