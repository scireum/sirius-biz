/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.importer.txn.ImportTransactionalEntity;
import sirius.kernel.nls.NLS;

import java.util.function.Consumer;

/**
 * Declares the deletion modes for a {@link RelationalEntityImportJob} using {@linkplain RelationalEntityImportJob#SYNC_MODE_PARAMETER}
 * equals {@linkplain SyncMode#SYNC}.
 * <p>
 * The deletion performed in {@link sirius.biz.importer.txn.ImportTransactionHelper#deleteUnmarked(Class, Consumer, Consumer)},
 * attempts to delete all records not {@linkplain sirius.biz.importer.txn.ImportTransactionHelper#mark(ImportTransactionalEntity) marked}
 * during an import and are considered obsolete.
 * <p>
 * Depending on this mode, the deletion clause is fine-tuned to consider the {@link sirius.biz.importer.txn.ImportTransactionData#SOURCE}
 * field in different ways.
 */
public enum SyncSourceDeleteMode {

    /**
     * Deletes unmarked records with the same source.
     */
    SAME_SOURCE,

    /**
     * Deletes unmarked records with the same source or empty source.
     */
    SAME_SOURCE_OR_EMPTY,

    /**
     * Deletes unmarked records independent on source.
     */
    ALL;

    @Override
    public String toString() {
        return NLS.get(getClass().getSimpleName() + "." + name());
    }
}
