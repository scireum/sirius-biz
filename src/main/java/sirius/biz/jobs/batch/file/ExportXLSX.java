/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.web.data.ExcelExport;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Supplier;

/**
 * Provides a line based export which yields a MS Excel file.
 */
public class ExportXLSX implements LineBasedExport {

    private final ExcelExport export;
    private final Supplier<OutputStream> outputStreamSupplier;

    /**
     * Creates a new export which writes to the given output stream.
     *
     * @param outputStreamSupplier the supplies which provides the destination output stream to write to
     */
    public ExportXLSX(Supplier<OutputStream> outputStreamSupplier) {
        this.outputStreamSupplier = outputStreamSupplier;
        this.export = createExcelExport();
    }

    protected ExcelExport createExcelExport() {
        return ExcelExport.asStreamingXLSX();
    }

    @Override
    public void addListRow(List<?> row) throws IOException {
        export.addListRow(row);
    }

    @Override
    public void addArrayRow(Object... row) throws IOException {
        export.addArrayRow(row);
    }

    @Override
    public void close() throws IOException {
        try (OutputStream out = outputStreamSupplier.get()) {
            export.writeToStream(out);
        }
    }
}
