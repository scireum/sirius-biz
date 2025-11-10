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
import sirius.biz.storage.layer2.jdbc.SQLBlob;
import sirius.biz.storage.layer2.jdbc.SQLBlobStorage;
import sirius.biz.storage.layer2.jdbc.SQLVariant;
import sirius.biz.storage.layer2.variants.BlobVariant;
import sirius.biz.tenants.TenantUserManager;
import sirius.db.jdbc.OMA;
import sirius.db.jdbc.SmartQuery;
import sirius.db.mixing.query.BaseQuery;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.web.security.Permission;

import javax.annotation.Nonnull;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Implementation for the {@link FillBlobChecksumJob} using the {@link SQLBlobStorage#FRAMEWORK_JDBC_BLOB_STORAGE}.
 */
public class FillSqlBlobChecksumJob extends FillBlobChecksumJob<SQLBlob, Long> {

    @Part
    private static OMA oma;

    private final Consumer<SmartQuery<SQLBlob>> queryTuner;

    /**
     * Creates a new batch job for the given batch process.
     * <p>
     * As a batch job is created per execution, subclasses can define fields and fill those from parameters
     * defined by their factory.
     *
     * @param process the context in which the process will be executed
     */
    protected FillSqlBlobChecksumJob(ProcessContext process, Consumer<SmartQuery<SQLBlob>> queryTuner) {
        super(process);
        this.queryTuner = queryTuner;
    }

    @Override
    protected void updateBlobChecksum(SQLBlob blob, String checksum) {
        try {
            oma.updateStatement(SQLBlob.class)
               .where(SQLBlob.ID, blob.getId())
               .set(SQLBlob.CHECKSUM, checksum)
               .executeUpdate();
        } catch (SQLException exception) {
            throw Exceptions.createHandled().error(exception).handle();
        }
    }

    @Override
    protected void updateVariantChecksum(BlobVariant variant, String checksum) {
        SQLVariant sqlVariant = (SQLVariant) variant;
        try {
            oma.updateStatement(SQLVariant.class)
               .where(SQLVariant.ID, sqlVariant.getId())
               .set(SQLVariant.CHECKSUM, checksum)
               .executeUpdate();
        } catch (SQLException exception) {
            throw Exceptions.createHandled().error(exception).handle();
        }
    }

    @Override
    protected Long fetchStartId() {
        return process.get(START_FROM_ID_PARAMETER).asLong(0L);
    }

    @Override
    protected List<SQLBlob> fetchNextBlobBatch(Long lastId) {
        SmartQuery<SQLBlob> query = oma.selectFromSecondary(SQLBlob.class)
                                       .eq(SQLBlob.SPACE_NAME, getStorageSpaceName())
                                       .eq(SQLBlob.DELETED, false)
                                       .eq(SQLBlob.COMMITTED, true)
                                       .ne(SQLBlob.PHYSICAL_OBJECT_KEY, null)
                                       .eq(SQLBlob.CHECKSUM, null);
        if (Strings.isFilled(lastId)) {
            query.where(oma.filters().gt(SQLBlob.ID, lastId));
        } else {
            long startId = fetchStartId();
            if (startId > 0L) {
                // This is the first query, and we want to start from a specific ID
                query.where(oma.filters().gte(SQLBlob.ID, startId));
            }
        }
        if (queryTuner != null) {
            queryTuner.accept(query);
        }
        return query.orderAsc(SQLBlob.ID).limit(BaseQuery.MAX_LIST_SIZE).queryList();
    }

    /**
     * Provides a factory to create instances of {@link FillSqlBlobChecksumJob}.
     */
    @Register(framework = SQLBlobStorage.FRAMEWORK_JDBC_BLOB_STORAGE)
    @Permission(TenantUserManager.PERMISSION_SYSTEM_ADMINISTRATOR)
    public static class FillSqlBlobJobFactory extends FillBlobChecksumJobFactory {

        @Override
        protected BatchJob createJob(ProcessContext process) throws Exception {
            return new FillSqlBlobChecksumJob(process, getQueryTuner());
        }

        @Nonnull
        @Override
        public String getName() {
            return "fill-sql-blob-checksum";
        }

        /**
         * Allows to provide a tuner for the query used to fetch the blobs to be processed.
         *
         * @return a consumer which tunes the query or <tt>null</tt> to not modify it
         */
        public Consumer<SmartQuery<SQLBlob>> getQueryTuner() {
            return null;
        }
    }
}
