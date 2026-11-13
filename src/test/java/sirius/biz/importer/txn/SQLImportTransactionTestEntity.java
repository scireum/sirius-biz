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
 * Provides a JDBC based entity used by {@link SQLImportTransactionHelperTest}.
 */
public class SQLImportTransactionTestEntity extends SQLEntity implements ImportTransactionTestEntity {

    public static final Mapping NAME = Mapping.named("name");
    @NullAllowed
    @Length(50)
    private String name;

    public static final Mapping DERIVED_NAME = Mapping.named("derivedName");
    @NullAllowed
    @Length(100)
    private String derivedName;

    private final ImportTransactionData importTransactionData = new ImportTransactionData();

    @Transient
    private String derivedNameSuffix = "";

    @BeforeSave
    protected void fillDerivedName() {
        derivedName = name + derivedNameSuffix;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getDerivedName() {
        return derivedName;
    }

    @Override
    public String getDerivedNameSuffix() {
        return derivedNameSuffix;
    }

    @Override
    public void setDerivedNameSuffix(String derivedNameSuffix) {
        this.derivedNameSuffix = derivedNameSuffix;
    }

    @Override
    public ImportTransactionData getImportTransactionData() {
        return importTransactionData;
    }
}
