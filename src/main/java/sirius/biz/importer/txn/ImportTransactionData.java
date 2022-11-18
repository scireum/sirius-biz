/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.txn;

import sirius.biz.importer.AutoImport;
import sirius.biz.protocol.NoJournal;
import sirius.db.mixing.Composite;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.SkipDefaultValue;

import java.util.function.Consumer;

/**
 * Contains import transaction information.
 * <p>
 * This is used by the {@link ImportTransactionHelper} to identify and delete all unchanged
 * entities after an update transaction has been completed.
 * <p>
 * Note that appropriate index should be added to the embedding entity.
 */
public class ImportTransactionData extends Composite {

    /**
     * Represents the last import transaction id which was used to modify the owning entity.
     */
    public static final Mapping TXN_ID = Mapping.named("txnId");
    @NoJournal
    @AutoImport(hidden = true)
    private long txnId;

    /**
     * Represents the source associated with the {@link #TXN_ID}, used to limit the scope of data being deleted.
     *
     * @see ImportTransactionHelper#deleteUnmarked(Class, Consumer, Consumer)
     */
    public static final Mapping SOURCE = Mapping.named("source");
    @NoJournal
    @AutoImport(hidden = true)
    @SkipDefaultValue
    private String source;

    public long getTxnId() {
        return txnId;
    }

    public void setTxnId(long txnId) {
        this.txnId = txnId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
