/*
 * Made with all the love in the world
 * by scireum in Stuttgart, Germany
 *
 * Copyright by scireum GmbH
 * https://www.scireum.de - info@scireum.de
 */

package sirius.biz.importer.txn;

/**
 * Describes the entities used by {@link ImportTransactionHelperTest}, independently of the database.
 */
public interface ImportTransactionTestEntity extends ImportTransactionalEntity {

    String getName();

    void setName(String name);

    /**
     * Returns the name along with the {@link #getDerivedNameSuffix() suffix}, as computed by the <tt>beforeSave</tt>
     * handler of the entity.
     *
     * @return the derived name
     */
    String getDerivedName();

    String getDerivedNameSuffix();

    /**
     * Specifies a suffix which is appended to the derived name by the <tt>beforeSave</tt> handler of the entity.
     * <p>
     * This permits to change an entity solely within its <tt>beforeSave</tt> handler.
     *
     * @param derivedNameSuffix the suffix to append
     */
    void setDerivedNameSuffix(String derivedNameSuffix);
}
