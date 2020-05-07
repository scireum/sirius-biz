/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.biz.jobs.batch.file;

import sirius.biz.jobs.params.EnumParameter;
import sirius.biz.process.ProcessContext;
import sirius.biz.storage.layer3.FileOrDirectoryParameter;
import sirius.biz.storage.layer3.FileParameter;
import sirius.kernel.commons.Values;

import java.io.Closeable;
import java.io.IOException;

/**
 * Provides a job for transforming a line based file into another.
 * <p>
 * This is implemented by a {@link LineBasedImportExportJobFactory} and will read a line based file, process a row
 * and output one or more rows into an output file.
 */
public abstract class LineBasedImportExportJob extends LineBasedImportJob {

    protected final LineBasedExportJob exportJob;

    /**
     * Creates a new job for the given factory, name and process.
     *
     * @param inputFileParameter   the parameter which is used to derive the import file from
     * @param destinationParameter the parameter used to select the destination for the file being written
     * @param fileTypeParameter    the file type to use when writing the line based data
     * @param process              the process context itself
     */
    protected LineBasedImportExportJob(FileParameter inputFileParameter,
                                       FileOrDirectoryParameter destinationParameter,
                                       EnumParameter<ExportFileType> fileTypeParameter,
                                       ProcessContext process) {
        super(inputFileParameter, process);
        this.exportJob = new LineBasedExportJob(destinationParameter, fileTypeParameter, process) {
            @Override
            protected void executeIntoExport() throws Exception {
                // ignored
            }

            @Override
            protected String determineFilenameWithoutExtension() {
                return LineBasedImportExportJob.this.determineFilenameWithoutExtension();
            }
        };
    }

    /**
     * Determines the base name to use for the file.
     * <p>
     * This will be expanded by the date and also by additional suffixes to generate a unique name. Also the file
     * extension as supplied by {@link FileExportJob#determineFileExtension()} will be appended.
     *
     * @return the base file name to use
     */
    protected abstract String determineFilenameWithoutExtension();

    @Override
    public void execute() throws Exception {
        exportJob.execute();
        super.execute();
    }

    @Override
    public void handleRow(int lineNumber, Values row) {
        handleRow(lineNumber, row, exportJob.export);
    }

    /**
     * Invoked for each line in the given input file.
     * <p>
     * The provided export writes into the selected output file.
     * </p>
     *
     * @param lineNumber the line of the import being processed
     * @param row        the data in this line
     * @param export     the export to write to
     */
    protected abstract void handleRow(int lineNumber, Values row, LineBasedExport export);

    @Override
    public void close() throws IOException {
        try (Closeable c = exportJob) {
            super.close();
        }
    }
}
