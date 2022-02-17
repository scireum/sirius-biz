/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Defines a line based export to unify writing into a CSV or Excel file.
 */
public interface LineBasedExport extends Closeable {

    /**
     * Adds the list of fields as new row.
     *
     * @param row the list of fields to add
     * @throws IOException in case of an IO error in the underlying storage
     */
    void addListRow(List<?> row) throws IOException;

    /**
     * Adds the list of fields as new row.
     *
     * @param row the list of fields to add
     * @throws IOException in case of an IO error in the underlying storage
     * @deprecated Use {@link #addListRow(List)}which does exactly the same but has a clarified naming scheme.
     */
    @Deprecated
    default void addRow(List<?> row) throws IOException {
        addListRow(row);
    }

    /**
     * Adds the array of fields as new row.
     *
     * @param row the array of fields to add
     * @throws IOException in case of an IO error in the underlying storage
     */
    void addArrayRow(Object... row) throws IOException;
}
