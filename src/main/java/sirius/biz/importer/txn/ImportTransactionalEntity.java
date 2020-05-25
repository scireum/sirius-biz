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
 * Marks entities which support import transactions by containin a {@link ImportTransactionData}.
 *
 * @see ImportTransactionHelper
 */
@SuppressWarnings("squid:S1214")
@Explain("This constants belongs here as there is no point in defining it in each user of the API or somwhere else.")
public interface ImportTransactionalEntity {

    /**
     * Contains the import transaction id wrapped as composite so that it can be easily embedded.
     */
    Mapping IMPORT_TRANSACTION_DATA = Mapping.named("importTransactionData");

    /**
     * Provides access to the transaction id stored for an entity.
     *
     * @return the import transaction data which wraps the transaction id
     */
    ImportTransactionData getImportTransactionData();
}
