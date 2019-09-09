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
 * Enumerates the formats supported by the {@link LineBasedExportJob}.
 */
public enum ExportFileType {

    XLSX, XLS, CSV;

    @Override
    public String toString() {
        return NLS.get(getClass().getSimpleName() + "." + name());
    }
}
