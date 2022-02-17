/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.kernel.commons.CSVWriter;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Provides a line based export which yields a CSV file.
 */
public class ExportCSV implements LineBasedExport {

    private final CSVWriter writer;

    /**
     * Creates a new export which writes to the given writer.
     *
     * @param writer the destination to write the CSV data to
     */
    public ExportCSV(Writer writer) {
        this.writer = new CSVWriter(writer);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void addListRow(List<?> row) throws IOException {
        writer.writeList((List<Object>)row);
    }

    @Override
    public void addArrayRow(Object... row) throws IOException {
        writer.writeArray(row);
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    public CSVWriter getWriter() {
        return writer;
    }
}
