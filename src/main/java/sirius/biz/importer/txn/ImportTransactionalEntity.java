/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.txn;

import sirius.db.mixing.Mapping;
import sirius.kernel.commons.Explain;

/**
 * Marks entities which support import transactions by containing a {@link ImportTransactionData}.
 *
 * @see ImportTransactionHelper
 */
@SuppressWarnings("squid:S1214")
@Explain("The constant is best kept here for consistency reasons.")
public interface ImportTransactionalEntity {

    /**
     * Provides the default mapping for accessing the import transaction data.
     */
    Mapping IMPORT_TRANSACTION_DATA = Mapping.named("importTransactionData");

    /**
     * Provides access to the transaction id stored for an entity.
     *
     * @return the import transaction data which wraps the transaction id
     */
    ImportTransactionData getImportTransactionData();
}
