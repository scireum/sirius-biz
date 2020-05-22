/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.kernel.nls.NLS;

/**
 * Declares the modes of operation for an {@link EntityImportJob}.
 */
public enum SyncMode {

    /**
     * Only updates existing entities.
     */
    NEW_AND_UPDATE_ONLY,

    /**
     * Only updates existing entities.
     */
    UPDATE_ONLY,

    /**
     * Only creates new entities.
     */
    NEW_ONLY,

    /**
     * Creates new entities and also updates existing ones.
     */
    SYNC,

    /**
     * Only deletes the entities which are passed in.
     */
    DELETE_EXISTING;

    @Override
    public String toString() {
        return NLS.get(getClass().getSimpleName() + "." + name());
    }
}
