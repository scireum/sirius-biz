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
import sirius.db.mixing.annotations.BeforeSave;
import sirius.db.mixing.annotations.Length;
import sirius.db.mixing.annotations.NullAllowed;
import sirius.db.mixing.annotations.Transient;

/**
 * Provides a JDBC based entity used by {@link ImportTransactionHelperTest}.
 */
public class SQLImportTransactionTestEntity extends SQLEntity implements ImportTransactionalEntity {

    public static final Mapping NAME = Mapping.named("name");
    @NullAllowed
    @Length(50)
    private String name;

    public static final Mapping DERIVED_NAME = Mapping.named("derivedName");
    @NullAllowed
    @Length(100)
    private String derivedName;

    private final ImportTransactionData importTransactionData = new ImportTransactionData();

    /**
     * Contains a suffix which is appended to the {@link #DERIVED_NAME} in {@link #fillDerivedName()}.
     * <p>
     * This permits to change an entity solely within its <tt>beforeSave</tt> handler.
     */
    @Transient
    public String derivedNameSuffix = "";

    @BeforeSave
    protected void fillDerivedName() {
        derivedName = name + derivedNameSuffix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDerivedName() {
        return derivedName;
    }

    @Override
    public ImportTransactionData getImportTransactionData() {
        return importTransactionData;
    }
}
