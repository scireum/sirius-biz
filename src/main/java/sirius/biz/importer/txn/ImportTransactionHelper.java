/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.txn;

import sirius.biz.importer.BeforeDeleteEvent;
import sirius.biz.importer.ImportHelper;
import sirius.biz.importer.Importer;
import sirius.biz.importer.ImporterContext;
import sirius.biz.jobs.batch.file.SyncSourceDeleteMode;
import sirius.db.mixing.BaseEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.Mixing;
import sirius.db.mixing.query.Query;
import sirius.db.mixing.query.constraints.Constraint;
import sirius.kernel.commons.Explain;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Helps to perform import transactions.
 * <p>
 * Import transactions can be used to delete all unchanged entities after an import has been completed. This way,
 * a large number of entities out of an even larger set of entities can be updated or even just "touched" by an
 * import process. Then this helper can be used to delete all remaining entities without keeping a large number of
 * entities in the heap
 */
public class ImportTransactionHelper extends ImportHelper {

    /**
     * Contains the constant to identify that no transaction is present / was started.
     */
    public static final long NO_TRANSACTION = -1L;

    @Part
    private static Mixing mixing;

    private long transactionId = NO_TRANSACTION;
    private String source;
    private SyncSourceDeleteMode deleteMode = SyncSourceDeleteMode.ALL;

    /**
     * Creates a new instance.
     * <p>
     * Note that this shouldn't be called manually but rather via
     * {@link Importer#findHelper(Class)} which returns the specific instance per importer.
     *
     * @param context the import context to use
     */
    public ImportTransactionHelper(ImporterContext context) {
        super(context);
    }

    /**
     * Starts a new transaction by setting the internal transaction id to the current timestamp.
     *
     * @return the helper itself for fluent method calls
     */
    public ImportTransactionHelper start() {
        this.transactionId = System.currentTimeMillis();
        return this;
    }

    /**
     * Starts a new transaction by setting the internal transaction id to the current timestamp and a source.
     *
     * @param source     the source, later used to delete unmarked items of the same source
     * @param deleteMode the mode used to delete unmarked items depending on the given source
     * @return the helper itself for fluent method calls
     */
    public ImportTransactionHelper start(String source, SyncSourceDeleteMode deleteMode) {
        start();
        // The source is never initialized with an empty string but null instead.
        if (source != null && !source.isBlank()) {
            this.source = source.trim();
        }
        this.deleteMode = deleteMode;
        return this;
    }

    /**
     * Determines if a transaction has been {@link #start() started} and not {@link #finish() finished} yet.
     *
     * @return <tt>true</tt> if a transaction is active, <tt>false</tt> otherwise
     */
    public boolean isActive() {
        return this.transactionId != NO_TRANSACTION;
    }

    /**
     * Returns the transaction id currently being used.
     * <p>
     * Note that we use a simple timestamp value as we do not expect sub millisecond transactions.
     *
     * @return the transaction id used by this helper
     */
    public long getCurrentTransaction() {
        if (transactionId == NO_TRANSACTION) {
            throw new IllegalStateException("No transaction has been started!");
        }
        return transactionId;
    }

    /**
     * Finishes the transaction by resetting the internal transaction id to {@link #NO_TRANSACTION}.
     * <p>
     * Note that this will not perform any changes on any database.
     */
    public void finish() {
        this.transactionId = NO_TRANSACTION;
        this.source = null;
        this.deleteMode = SyncSourceDeleteMode.ALL;
    }

    /**
     * Installs the current transaction id in the given entity.
     * <p>
     * Note that the entity still has to be persisted after this call.
     *
     * @param entity the entity to write the current transaction id into
     */
    public void mark(ImportTransactionalEntity entity) {
        entity.getImportTransactionData().setTxnId(getCurrentTransaction());
        entity.getImportTransactionData().setSource(source);
    }

    /**
     * Deletes all entities of the given type which have not been marked by the current import transaction.
     *
     * @param entityType     the type of entities to delete
     * @param queryExtender  a consumer to further filter the query which determines which entities should be deleted
     * @param entityCallback an optional callback which is invoked for each entity to be deleted
     * @param <E>            the generic type of entities being deleted
     */
    @SuppressWarnings({"unchecked", "java:S1905"})
    @Explain("This cast is actually necessary.")
    public <E extends BaseEntity<?> & ImportTransactionalEntity, C extends Constraint> void deleteUnmarked(Class<E> entityType,
                                                                                                           Consumer<Query<?, E, C>> queryExtender,
                                                                                                           @Nullable
                                                                                                           Consumer<E> entityCallback) {
        Query<?, E, C> query =
                (Query<?, E, C>) (Object) mixing.getDescriptor(entityType).getMapper().select(entityType);
        query.ne(ImportTransactionalEntity.IMPORT_TRANSACTION_DATA.inner(ImportTransactionData.TXN_ID),
                 getCurrentTransaction());

        queryExtender.accept(query);

        Mapping sourceMapping = ImportTransactionalEntity.IMPORT_TRANSACTION_DATA.inner(ImportTransactionData.SOURCE);
        if (deleteMode == SyncSourceDeleteMode.SAME_SOURCE) {
            query.eq(sourceMapping, source);
        }

        if (deleteMode == SyncSourceDeleteMode.SAME_SOURCE_OR_EMPTY) {
            query.where(query.filters().eqOrEmpty(sourceMapping, source));
        }

        if (context.getEventHandler().isActive()) {
            query.streamBlockwise().forEach(entity -> {
                BeforeDeleteEvent<E> beforeDeleteEvent = new BeforeDeleteEvent<>(entity, context);
                context.getEventHandler().handleEvent(beforeDeleteEvent);
                if (beforeDeleteEvent.isAborted()) {
                    return;
                }

                if (entityCallback != null) {
                    entityCallback.accept(entity);
                }
                query.getDescriptor().getMapper().delete(entity);
            });
        } else {
            query.delete(entityCallback);
        }
    }

    /**
     * Deletes all entities of the given type which have not been marked by the current import transaction but contain
     * the given value in the given field.
     *
     * @param entityType     the type of entities to delete
     * @param field          the field to filter on
     * @param value          the value used to determine if the entity should be deleted or not
     * @param entityCallback an optional callback which is invoked for each entity to be deleted
     * @param <E>            the generic type of entities being deleted
     */
    public <I, E extends BaseEntity<I> & ImportTransactionalEntity> void deleteUnmarked(Class<E> entityType,
                                                                                        Mapping field,
                                                                                        Object value,
                                                                                        @Nullable
                                                                                        Consumer<E> entityCallback) {
        deleteUnmarked(entityType, query -> query.eq(field, value), entityCallback);
    }

    /**
     * Checks if the given entity can be deleted under the current transaction context.
     *
     * @param entity the entity to check
     * @return <tt>true</tt> if the entity can be deleted, <tt>false</tt> otherwise
     */
    public <E extends ImportTransactionalEntity> boolean canDelete(E entity) {
        if (entity.getImportTransactionData().getTxnId() == getCurrentTransaction()) {
            return false;
        }

        String entitySource = entity.getImportTransactionData().getSource();

        if (deleteMode == SyncSourceDeleteMode.SAME_SOURCE && Strings.areEqual(entitySource, source)) {
            return true;
        }

        if (deleteMode == SyncSourceDeleteMode.SAME_SOURCE_OR_EMPTY && (Strings.areEqual(entitySource, source)
                                                                        || Strings.isEmpty(entitySource))) {
            return true;
        }

        return deleteMode == SyncSourceDeleteMode.ALL;
    }

    /**
     * Sets a new value for the source.
     * This is useful e.g. in migration scenarios where the source in one transaction could be different.
     *
     * @param source the new source
     * @deprecated Only use this method if you are sure that you want to change the source in one and the same transaction.
     */
    @Deprecated
    public void setSource(String source) {
        this.source = source;
    }
}
