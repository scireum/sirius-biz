/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.web.data.ExcelExport;

import java.io.OutputStream;
import java.util.function.Supplier;

/**
 * Provides a line based export which yields a MS Excel 97 file.
 */
public class ExportXLS extends ExportXLSX {

    /**
     * Creates a new export which writes to the given output stream.
     *
     * @param outputStreamSupplier the supplies which provides the destination output stream to write to
     */
    public ExportXLS(Supplier<OutputStream> outputStreamSupplier) {
        super(outputStreamSupplier);
    }

    @Override
    protected ExcelExport createExcelExport() {
        return ExcelExport.asXLS();
    }
}
