/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.txn;

import sirius.db.jdbc.SQLEntity;
import sirius.db.mixing.Mapping;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;

/**
 * Provides a JDBC based entity used by {@link ImportTransactionHelperTest}.
 */
public class SQLImportTransactionTestEntity extends SQLEntity implements ImportTransactionalEntity {

    public static final Mapping NAME = Mapping.named("name");
    @NullAllowed
    @Length(50)
    private String name;

    private final ImportTransactionData importTransactionData = new ImportTransactionData();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public ImportTransactionData getImportTransactionData() {
        return importTransactionData;
    }
}
