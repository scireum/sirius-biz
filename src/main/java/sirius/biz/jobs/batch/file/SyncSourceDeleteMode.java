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
 * Declares the deletion modes for a {@link RelationalEntityImportJob} using {@linkplain RelationalEntityImportJob#SYNC_MODE_PARAMETER}
 * equals {@linkplain SyncMode#SYNC}.
 */
public enum SyncSourceDeleteMode {

    /**
     * Deletes non-matching records with the same source.
     */
    SAME_SOURCE,

    /**
     * Deletes non-matching records with the same source or empty source.
     */
    SAME_SOURCE_OR_EMPTY,

    /**
     * Deletes non-matching records independent on source.
     */
    ALL;

    @Override
    public String toString() {
        return NLS.get(getClass().getSimpleName() + "." + name());
    }
}
